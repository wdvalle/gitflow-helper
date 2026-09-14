package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.service.GitFlowDivergenceService;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import git4idea.GitCommit;
import git4idea.commands.GitCommand;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncAction extends BaseAction {

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String currentBranch = getBranchName(project);
        String baseBranch = getBaseBranch(project, currentBranch);

        if (baseBranch == null) {
            NotificationUtil.showGitFlowErrorNotification(project, "Error", "Could not identify base branch for " + currentBranch);
            return;
        }

        List<GitRepository> repositories = getRepositories(project);

        // Check for uncommitted changes first
        for (GitRepository repository : repositories) {
            if (!ChangeListManager.getInstance(project).getChangesIn(repository.getRoot()).isEmpty()) {
                NotificationUtil.showGitFlowErrorNotification(project, "Sync Cancelled",
                        "Repository '" + repository.getRoot().getName() + "' has uncommitted changes. Please commit or stash them first.");
                return;
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true, project);
            setProgress(1, project);

            try {
                GitExecutor executor = new GitExecutor(project);

                // 1. Immediate remote fetch to guarantee up-to-date divergence check
                for (GitRepository repository : repositories) {
                    try {
                        executor.execute(repository.getRoot(), GitCommand.FETCH, REMOTE);
                        repository.update();
                    } catch (Exception ignored) {
                    }
                }
                setProgress(3, project);

                // 2. Immediate calculation of commits behind
                int commitsBehind = calculateCommitsBehind(project, currentBranch, baseBranch, repositories);

                GitFlowDivergenceService divergenceService = GitFlowDivergenceService.getInstance(project);
                if (divergenceService != null) {
                    divergenceService.requestCheck();
                }
                setProgress(4, project);

                AtomicBoolean proceedWithSync = new AtomicBoolean(false);

                ApplicationManager.getApplication().invokeAndWait(() -> {
                    if (project.isDisposed()) return;

                    if (commitsBehind <= 0) {
                        Messages.showInfoMessage(
                                project,
                                "Branch '" + currentBranch + "' is already synchronized with '" + baseBranch + "'.",
                                "Branch Synchronized"
                        );
                    } else {
                        String commitText = commitsBehind == 1 ? "1 commit" : commitsBehind + " commits";
                        String message = "Branch '" + currentBranch + "' is " + commitText + " behind '" + baseBranch + "'.\nDo you want to sync?";

                        int confirm = Messages.showYesNoDialog(
                                project,
                                message,
                                "Sync Branch",
                                Messages.getQuestionIcon()
                        );
                        if (confirm == Messages.YES) {
                            proceedWithSync.set(true);
                        }
                    }
                });

                if (!proceedWithSync.get()) {
                    return;
                }

                // 3. Execute sync
                sync(project, currentBranch, baseBranch, repositories);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Branch '" + currentBranch + "' synced with '" + baseBranch + "'");

                if (divergenceService != null) {
                    divergenceService.requestCheck();
                }
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Sync Error", ex.getGitResult().getProcessMessage());
            } catch (Exception ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getMessage());
            } finally {
                setLoading(false, project);
                setProgress(10, project);
            }
        });
    }

    private int calculateCommitsBehind(Project project, String currentBranch, String baseBranch, List<GitRepository> repositories) {
        Map<String, GitCommit> behindMap = new LinkedHashMap<>();
        for (GitRepository repository : repositories) {
            try {
                List<GitCommit> behindRemote = GitHistoryUtils.history(
                        project, repository.getRoot(),
                        currentBranch + ".." + REMOTE + "/" + baseBranch
                );
                for (GitCommit c : behindRemote) {
                    behindMap.put(c.getId().asString(), c);
                }
            } catch (VcsException ignored) {
            }

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
        return behindMap.size();
    }

    private void sync(Project project, String currentBranch, String baseBranch, List<GitRepository> repositories) {
        GitExecutor executor = new GitExecutor(project);

        int totalSteps = repositories.size() * 4;
        int currentStep = 0;

        for (GitRepository repository : repositories) {
            // 1. Checkout base branch
            setProgress(4 + (++currentStep * 5 / totalSteps), project);
            executor.execute(repository.getRoot(), GitCommand.CHECKOUT, baseBranch);

            // 2. Pull base branch
            setProgress(4 + (++currentStep * 5 / totalSteps), project);
            executor.execute(repository.getRoot(), GitCommand.PULL, REMOTE, baseBranch);

            // 3. Checkout current branch back
            setProgress(4 + (++currentStep * 5 / totalSteps), project);
            executor.execute(repository.getRoot(), GitCommand.CHECKOUT, currentBranch);

            // 4. Merge base into current
            setProgress(4 + (++currentStep * 5 / totalSteps), project);
            executor.execute(repository.getRoot(), GitCommand.MERGE, baseBranch);

            repository.update();
        }
        setProgress(10, project);
    }

    protected String getBaseBranch(Project project, String currentBranch) {
        if (currentBranch == null) return null;
        if (currentBranch.startsWith(getFeaturePrefix(project))) return getDevelopBranch(project);
        if (currentBranch.startsWith(getReleasePrefix(project))) return getDevelopBranch(project);
        if (currentBranch.startsWith(getHotfixPrefix(project))) return getMainBranch(project);
        return null;
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        if (project == null || getRepositories(project).isEmpty()) {
            presentation.setEnabled(false);
            return;
        }

        String currentBranch = getBranchName(project);
        boolean isFlowBranch = currentBranch != null &&
                (currentBranch.startsWith(getFeaturePrefix(project)) ||
                        currentBranch.startsWith(getReleasePrefix(project)) ||
                        currentBranch.startsWith(getHotfixPrefix(project)));

        presentation.setEnabled(StringUtil.isNotEmpty(getMainBranch(project)) &&
                StringUtil.isNotEmpty(getDevelopBranch(project)) &&
                isFlowBranch);
    }
}
