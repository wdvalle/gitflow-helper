package br.com.gitflowhelper.actions;

import br.com.gitflow.tracker.IssueTrackerConnector;
import br.com.gitflow.tracker.TrackerFactory;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.dialog.ActionChoiceDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import git4idea.GitCommit;
import git4idea.commands.GitCommand;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class FeatureFinishAction extends BaseAction {

    public FeatureFinishAction() {
//        super(actionTitle, GitFlowDescriptions.FEATURE_FINISH.getValue(), AllIcons.Vcs.Patch_applied);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        final String[] featureCommits = {""};
        String[] postAction = new String[1];
        Project project = e.getProject();
        String branchName = getBranchName(project);
        ActionChoiceDialog dialog = new ActionChoiceDialog(project, branchName, getDevelopBranch(project));

        var future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
            try {
                for (GitRepository repository : repoManager.getRepositories()) {
                    VirtualFile root = repository.getRoot();
                    //grabs from the first
                    if (featureCommits[0].equals("")) {
                        featureCommits[0] = getFeatureCommits(project, repository, getDevelopBranch(project), branchName);
                        break;
                    }
                }
                dialog.setLog(featureCommits[0]);
            } catch (Exception ex) {
                ExceptionUtil.handleException(project, ex);
            }
        });

        try {
            future.get();
            if (dialog.showAndGet()) {
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    setLoading(true, true, project);
                    try {
                        featureFinish(
                            project,
                            branchName,
                            dialog.getSquashCommit(),
                            dialog.getCommitMessage(),
                            !dialog.getKeepLocalBranch(),
                            !dialog.getKeepRemoteBranch(),
                            dialog.getSelectedAction(),
                            true,
                            postAction,
                            branchName);

                        doFinishTask(dialog.getCloseAssociatedTask(), project);

                        NotificationUtil.showGitFlowSuccessNotification(project, "Success",  postAction[0]);
                    } catch (GitException ex) {
                        NotificationUtil.showGitFlowErrorNotification(project, "Error", "Error message: "+ex.getGitResult().getProcessMessage());
                    } catch (Throwable ex) {
                        ExceptionUtil.handleException(project, ex);
                    }

                    setLoading(false, project);
                });
            }
        } catch (Exception ex) {
            ExceptionUtil.handleException(project, ex);
        }
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch(e.getProject())) &&
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).startsWith(getFeaturePrefix(e.getProject()))
        );
    }

    private String getFeatureCommits(
            Project project, GitRepository repository,
            String baseBranch, String featureBranch) throws VcsException {
        List<GitCommit> commits = GitHistoryUtils.history(
                project,
                repository.getRoot(),
                baseBranch + ".." + featureBranch
        );
        return commits.stream()
                .map(c -> "- " + c.getFullMessage())
                .collect(Collectors.joining("\n"));

    }

    private List<GitResult> featureFinish(
            Project project,
            String featureBranch,
            boolean squash,
            String finalCommitMessage,
            boolean deleteLocalBranch,
            boolean deleteRemoteBranch,
            String mode,
            boolean rebaseBeforeIntegrate,
            String[] postAction,
            String branchName) {
        setProgress(1, project);


        String baseBranch = getDevelopBranch(project);
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);
        List<GitResult> results = new ArrayList<>();

        for (GitRepository repository : repoManager.getRepositories()) {
            VirtualFile root = repository.getRoot();

            setProgress(2, project);
            switch (mode) {

                // =========================
                // INTEGRATE
                // =========================
                case ActionChoiceDialog.INTEGRATE -> {

                    // checkout develop
                    results.add(
                            executor.execute(root, GitCommand.CHECKOUT, baseBranch)
                    );

                    if (rebaseBeforeIntegrate) {
                        // fetch
                        executor.execute(root, GitCommand.FETCH);

                        // rebase develop
                        executor.execute(root, GitCommand.REBASE, baseBranch);
                    }

                    setProgress(3, project);

                    // pull develop
                    results.add(
                            executor.execute(root, GitCommand.PULL)
                    );

                    // merge
                    if (squash) {
                        results.add(
                                executor.execute(
                                        root,
                                        GitCommand.MERGE,
                                        "--squash",
                                        featureBranch
                                )
                        );

                        results.add(
                                executor.execute(
                                        root,
                                        GitCommand.COMMIT,
                                        "-m",
                                        finalCommitMessage
                                )
                        );
                        setProgress(5, project);
                    } else {
                        results.add(
                                executor.execute(
                                        root,
                                        GitCommand.MERGE,
                                        "--no-ff",
                                        "-m",
                                        finalCommitMessage,
                                        featureBranch
                                )
                        );

                        setProgress(5, project);
                    }

                    // push to develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.PUSH,
                                    REMOTE,
                                    baseBranch
                            )
                    );

                    setProgress(7, project);

                    postAction[0] = "Feature finished and pushed to " + getDevelopBranch(project) + " successfully.";

                }

                // =========================
                // SELF_CREATE
                // =========================
                case ActionChoiceDialog.SELF_CREATE -> {

                    // commit changes on feature branch
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.COMMIT,
                                    "--allow-empty",
                                    "-m",
                                    finalCommitMessage
                            )
                    );
                    setProgress(4, project);

                    // push feature branch
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.PUSH,
                                    REMOTE,
                                    featureBranch
                            )
                    );

                    setProgress(6, project);

                    // checkout develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.CHECKOUT,
                                    baseBranch
                            )
                    );

                    postAction[0] = "Feature pushed to " + branchName + " successfully. Create yourself a merge/pull request.";

                    setProgress(7, project);
                }

                // =========================
                // AUTO_CREATE
                // =========================
                case ActionChoiceDialog.AUTO_CREATE -> {

                    String title = finalCommitMessage.substring(0, finalCommitMessage.indexOf("\n"));
                    StringBuilder description = new StringBuilder();
                    for (String line : finalCommitMessage.substring(finalCommitMessage.indexOf("\n")+1).split("\\R")) {
                        description.append(line).append("<br/>");
                    }
                    // commit changes on feature branch
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.COMMIT,
                                    "--allow-empty",
                                    "-m",
                                    finalCommitMessage
                            )
                    );
                    setProgress(5, project);

                    // push with GitLab MR options
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.PUSH,
                                    REMOTE,
                                    featureBranch,
                                    "-o", "merge_request.create",
                                    "-o", "merge_request.target=" + baseBranch,
                                    "-o", "merge_request.title=" + title,
                                    "-o", "merge_request.description=" + description
                            )
                    );

                    setProgress(6, project);
                    // checkout develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.CHECKOUT,
                                    baseBranch
                            )
                    );

                    setProgress(7, project);

                    postAction[0] = "Feature pushed to " + branchName + " and merge request created successfully.";
                }
            }

            setProgress(8, project);
            // delete local branch
            if (deleteLocalBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.BRANCH,
                                "-d",
                                featureBranch
                        )
                );
            }

            setProgress(9, project);

            // delete remote branch
            if (deleteRemoteBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                REMOTE,
                                "--delete",
                                featureBranch
                        )
                );
            }

            repository.update();
            setProgress(10, project);
        }

        return results;
    }
}
