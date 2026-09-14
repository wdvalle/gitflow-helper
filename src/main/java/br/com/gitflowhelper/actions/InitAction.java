package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.InitDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class InitAction extends BaseAction {

    public InitAction(String actionTitle) {
        super(actionTitle, GitFlowDescriptions.INIT.getValue(), AllIcons.Scope.Production);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        new InitDialog(this, e.getProject()).show();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        boolean enabled = StringUtil.isEmpty(getMainBranch(e.getProject())) && !getRepositories(e.getProject()).isEmpty();
        presentation.setEnabled(enabled);
    }

    //invoked by InitDialog
    public void doOKAction(Project project, String mainField, String developField, String featureField,
                           String releaseField, String hotfixField) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, project);
            try {
                List<GitResult> results = init(true, project);
                if (results != null) {
                    NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Git Flow Initialization Successful");
                }
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                GitFlowSettingsService.getInstance(project).resetAndDeleteStorage();
            } catch (Exception ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getMessage() != null ? ex.getMessage() : ex.toString());
                GitFlowSettingsService.getInstance(project).resetAndDeleteStorage();
            } finally {
                setLoading(false, project);
            }
        });
    }

    public List<GitResult> init(boolean pushOnFinish, Project project) {

        setProgress(0, project);

        List<GitResult> results = new ArrayList<>();
        GitExecutor executor = new GitExecutor(project);

        String mainBranch = getMainBranch(project);
        String developBranch = getDevelopBranch(project);
        String featurePrefix = normalizePrefix(getFeaturePrefix(project));
        String releasePrefix = normalizePrefix(getReleasePrefix(project));
        String hotfixPrefix  = normalizePrefix(getHotfixPrefix(project));

        List<GitRepository> repositories = getRepositories(project);

        // Fetch remotes best-effort so remote branches are updated in repository
        for (GitRepository repository : repositories) {
            try {
                executor.execute(repository.getRoot(), GitCommand.FETCH, REMOTE);
                repository.update();
            } catch (Exception ignored) {
            }
        }

        // Check for missing local branches
        Map<GitRepository, List<String>> missingByRepo = new LinkedHashMap<>();
        int totalMissing = 0;

        for (GitRepository repository : repositories) {
            Map<String, GitLocalBranch> localBranches =
                    repository.getBranches()
                            .getLocalBranches()
                            .stream()
                            .collect(Collectors.toMap(
                                    GitLocalBranch::getName,
                                    Function.identity()
                            ));

            List<String> missing = new ArrayList<>();
            if (!localBranches.containsKey(mainBranch)) {
                missing.add(mainBranch);
            }
            if (!localBranches.containsKey(developBranch) && findRemoteBranch(repository, developBranch) != null) {
                missing.add(developBranch);
            }

            if (!missing.isEmpty()) {
                missingByRepo.put(repository, missing);
                totalMissing += missing.size();
            }
        }

        if (!missingByRepo.isEmpty()) {
            String message = buildMissingBranchesMessage(missingByRepo, totalMissing, repositories.size());
            AtomicBoolean userConfirmed = new AtomicBoolean(false);

            ApplicationManager.getApplication().invokeAndWait(() -> {
                if (project.isDisposed()) return;
                int confirm = Messages.showYesNoDialog(
                        project,
                        message,
                        "Git Flow Init",
                        Messages.getQuestionIcon()
                );
                if (confirm == Messages.YES) {
                    userConfirmed.set(true);
                }
            });

            if (!userConfirmed.get()) {
                GitFlowSettingsService.getInstance(project).resetAndDeleteStorage();
                NotificationUtil.showGitFlowWarningNotification(
                        project,
                        "Git Flow Init",
                        "Procedure cancelled. No changes were made."
                );
                return null;
            }

            // User confirmed: download missing branches from remote
            for (Map.Entry<GitRepository, List<String>> entry : missingByRepo.entrySet()) {
                GitRepository repo = entry.getKey();
                VirtualFile root = repo.getRoot();
                for (String branch : entry.getValue()) {
                    GitRemoteBranch remoteBranch = findRemoteBranch(repo, branch);
                    if (remoteBranch == null) {
                        throw new GitException(
                                "Branch '" + branch + "' was not found on remote repository '" + REMOTE + "'."
                        );
                    }
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.CHECKOUT,
                                    "-b",
                                    branch,
                                    "--track",
                                    remoteBranch.getName()
                            )
                    );
                }
                repo.update();
            }
        }

        for (GitRepository repository : repositories) {

            VirtualFile root = repository.getRoot();

            // does local branches exist
            Map<String, GitLocalBranch> localBranches =
                    repository.getBranches()
                            .getLocalBranches()
                            .stream()
                            .collect(Collectors.toMap(
                                    GitLocalBranch::getName,
                                    Function.identity()
                            ));

            setProgress(1, project);

            // 1 main branch
            if (!localBranches.containsKey(mainBranch)) {
                throw new GitException(
                        "Main branch '" + mainBranch + "' does not exist in this repo." +
                                root.getPath()
                );
            }

            setProgress(2, project);

            // 2 create develop if needed
            if (!localBranches.containsKey(developBranch)) {

                results.add(
                        executor.execute(
                                root,
                                GitCommand.CHECKOUT,
                                mainBranch
                        )
                );

                results.add(
                        executor.execute(
                                root,
                                GitCommand.CHECKOUT,
                                "-b",
                                developBranch
                        )
                );
            }

            setProgress(3, project);

            // 3 go to develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            developBranch
                    )
            );

            setProgress(4, project);

            // 4 initial push  (opcional)
            if (pushOnFinish) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                "-u",
                                "origin",
                                developBranch
                        )
                );
            }

            setProgress(5, project);

            // 5 git config - gitflow settings
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.branch.master",
                            mainBranch
                    )
            );

            setProgress(6, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.branch.develop",
                            developBranch
                    )
            );

            setProgress(7, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.feature",
                            featurePrefix
                    )
            );

            setProgress(8, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.release",
                            releasePrefix
                    )
            );

            setProgress(9, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.hotfix",
                            hotfixPrefix
                    )
            );

            // update repo state in intellij
            repository.update();
            setProgress(10, project);
        }

        return results;
    }

    public String buildMissingBranchesMessage(Map<GitRepository, List<String>> missingByRepo, int totalMissing, int totalRepos) {
        StringBuilder message = new StringBuilder();
        if (totalMissing == 1) {
            message.append("The following branch does not exist locally:\n");
        } else {
            message.append("The following branches do not exist locally:\n");
        }

        for (Map.Entry<GitRepository, List<String>> entry : missingByRepo.entrySet()) {
            GitRepository repo = entry.getKey();
            List<String> branches = entry.getValue();
            if (totalRepos > 1 && repo != null && repo.getRoot() != null) {
                message.append("• ").append(repo.getRoot().getName()).append(": ")
                        .append(String.join(", ", branches)).append("\n");
            } else {
                for (String b : branches) {
                    message.append("• ").append(b).append("\n");
                }
            }
        }

        if (totalMissing == 1) {
            message.append("\nDo you want to download it from the remote repository and proceed with Init?\n\n");
        } else {
            message.append("\nDo you want to download them from the remote repository and proceed with Init?\n\n");
        }
        message.append("Warning: If you choose 'No', nothing will be done and the procedure will be cancelled.");

        return message.toString();
    }

    public GitRemoteBranch findRemoteBranch(GitRepository repository, String branchName) {
        if (repository == null || branchName == null) {
            return null;
        }
        for (GitRemoteBranch remoteBranch : repository.getBranches().getRemoteBranches()) {
            if (remoteBranch.getName().equals(REMOTE + "/" + branchName)) {
                return remoteBranch;
            }
        }
        for (GitRemoteBranch remoteBranch : repository.getBranches().getRemoteBranches()) {
            if (remoteBranch.getNameForRemoteOperations().equals(branchName) ||
                    remoteBranch.getName().endsWith("/" + branchName)) {
                return remoteBranch;
            }
        }
        return null;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
