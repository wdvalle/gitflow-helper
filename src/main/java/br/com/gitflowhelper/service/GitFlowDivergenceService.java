package br.com.gitflowhelper.service;

import br.com.gitflowhelper.actions.BaseAction;
import br.com.gitflowhelper.events.GitFlowDivergenceListener;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitBranchUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.GitCommit;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class GitFlowDivergenceService implements Disposable {

    private final Project project;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingCheck;
    private volatile DivergenceInfo latestInfo = DivergenceInfo.EMPTY;

    public GitFlowDivergenceService(@NotNull Project project) {
        this.project = project;

        MessageBusConnection projectConn = project.getMessageBus().connect(this);

        // 1. Trigger when any Git repository state changes (checkout, commit, pull, fetch, etc.)
        projectConn.subscribe(GitRepository.GIT_REPO_CHANGE, repository -> requestCheck());

        // 2. Trigger when GitFlow settings change (e.g. branch names, prefixes, selected repos)
        projectConn.subscribe(GitFlowSettingsListener.TOPIC, this::requestCheck);

        // 3. Trigger when IDE window is activated/focused (e.g. switching back from browser/terminal)
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
                    @Override
                    public void applicationActivated(@NotNull IdeFrame ideFrame) {
                        requestCheck();
                    }
                });

        // 4. Periodic background verification fallback (every 10 minutes, with quiet origin fetch)
        scheduler.scheduleWithFixedDelay(this::requestCheckWithQuietFetch, 10, 10, TimeUnit.MINUTES);

        // Initial check
        requestCheck();
    }

    public static GitFlowDivergenceService getInstance(@NotNull Project project) {
        return project.getService(GitFlowDivergenceService.class);
    }

    public DivergenceInfo getLatestInfo() {
        return latestInfo;
    }

    /**
     * Requests an asynchronous divergence calculation.
     * Uses debouncing (400ms delay) so multiple rapid Git events collapse into a single check.
     */
    public synchronized void requestCheck() {
        if (project.isDisposed()) return;
        if (pendingCheck != null && !pendingCheck.isDone()) {
            pendingCheck.cancel(false);
        }
        pendingCheck = scheduler.schedule(this::doCheckDivergence, 400, TimeUnit.MILLISECONDS);
    }

    /**
     * Performs a quiet fetch of the remote repository and recalculates divergence.
     * Runs safely on the background scheduler thread without interrupting user work.
     */
    public void requestCheckWithQuietFetch() {
        if (project.isDisposed()) return;
        try {
            GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
            if (settings != null) {
                List<GitRepository> repositories = settings.getSelectedRepositories();
                if (repositories.isEmpty()) {
                    repositories = GitRepositoryManager.getInstance(project).getRepositories();
                }
                Git git = Git.getInstance();
                for (GitRepository repository : repositories) {
                    if (project.isDisposed()) return;
                    try {
                        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.FETCH);
                        handler.addParameters(BaseAction.REMOTE);
                        git.runCommand(handler);
                        repository.update();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        requestCheck();
    }

    private void doCheckDivergence() {
        if (project.isDisposed()) return;

        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        if (settings == null) {
            notifyUpdate(DivergenceInfo.EMPTY);
            return;
        }

        String developBranch = settings.getDevelopBranch();
        String mainBranch = settings.getMainBranch();
        if (StringUtil.isEmpty(developBranch) || StringUtil.isEmpty(mainBranch)) {
            notifyUpdate(DivergenceInfo.EMPTY);
            return;
        }

        List<GitRepository> repositories = settings.getSelectedRepositories();
        if (repositories.isEmpty()) {
            repositories = GitRepositoryManager.getInstance(project).getRepositories();
        }
        if (repositories.isEmpty()) {
            notifyUpdate(DivergenceInfo.EMPTY);
            return;
        }

        String currentBranch = GitBranchUtils.getCurrentBranchName(project);
        if (currentBranch == null) {
            notifyUpdate(DivergenceInfo.EMPTY);
            return;
        }

        String featurePrefix = settings.getFeaturePrefix();
        String releasePrefix = settings.getReleasePrefix();
        String hotfixPrefix = settings.getHotfixPrefix();

        String baseBranch = null;
        boolean isFlow = false;

        if (featurePrefix != null && !featurePrefix.isEmpty() && currentBranch.startsWith(featurePrefix)) {
            baseBranch = developBranch;
            isFlow = true;
        } else if (releasePrefix != null && !releasePrefix.isEmpty() && currentBranch.startsWith(releasePrefix)) {
            baseBranch = developBranch;
            isFlow = true;
        } else if (hotfixPrefix != null && !hotfixPrefix.isEmpty() && currentBranch.startsWith(hotfixPrefix)) {
            baseBranch = mainBranch;
            isFlow = true;
        }

        if (!isFlow || baseBranch == null || currentBranch.equals(baseBranch)) {
            notifyUpdate(new DivergenceInfo(false, currentBranch, null, 0));
            return;
        }

        Map<String, GitCommit> behindMap = new LinkedHashMap<>();
        for (GitRepository repository : repositories) {
            if (project.isDisposed()) return;

            // 1. Check against remote tracking base branch (e.g. branchName..origin/develop)
            try {
                List<GitCommit> behindRemote = GitHistoryUtils.history(
                        project, repository.getRoot(),
                        currentBranch + ".." + BaseAction.REMOTE + "/" + baseBranch
                );
                for (GitCommit c : behindRemote) {
                    behindMap.put(c.getId().asString(), c);
                }
            } catch (VcsException ignored) {
            }

            // 2. Check against local base branch (e.g. branchName..develop)
            try {
                List<GitCommit> behindLocal = GitHistoryUtils.history(
                        project, repository.getRoot(),
                        currentBranch + ".." + baseBranch
                );
                for (GitCommit c : behindLocal) {
                    behindMap.put(c.getId().asString(), c);
                }
            } catch (VcsException ignored) {
            }
        }

        int commitsBehindCount = behindMap.size();
        DivergenceInfo info = new DivergenceInfo(true, currentBranch, baseBranch, commitsBehindCount);
        notifyUpdate(info);
    }

    private void notifyUpdate(DivergenceInfo info) {
        this.latestInfo = info;
        if (!project.isDisposed()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    project.getMessageBus().syncPublisher(GitFlowDivergenceListener.TOPIC).divergenceUpdated(info);
                }
            });
        }
    }

    @Override
    public void dispose() {
        if (pendingCheck != null) {
            pendingCheck.cancel(true);
        }
        scheduler.shutdownNow();
    }
}
