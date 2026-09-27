package br.com.gitflowhelper.actions;

import br.com.gitflow.cicd.JenkinsConnector;
import br.com.gitflowhelper.dialog.ActionChoiceDialog;
import br.com.gitflowhelper.dialog.UncommittedChangesDialog;
import br.com.gitflowhelper.dialog.UnpushedCommitsDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import br.com.gitflowhelper.toolwindow.CIDataToolWindowPanel;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class FeatureFinishAction extends BaseAction {

    public FeatureFinishAction() {
//        super(actionTitle, GitFlowDescriptions.FEATURE_FINISH.getValue(), AllIcons.Vcs.Patch_applied);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FileDocumentManager.getInstance().saveAllDocuments();

        String branchName = getBranchName(project);
        String developBranch = getDevelopBranch(project);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            //setLoading(true, true, project);
            try {
                GitExecutor executor = new GitExecutor(project);

                setProgress(1, project);

                // Save all open documents so memory buffers are flushed to disk before Git operations
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    FileDocumentManager.getInstance().saveAllDocuments();
                });

                // 1. Check for uncommitted changes first
                boolean hasUncommitted = false;
                for (GitRepository repository : getRepositories(project)) {
                    if (hasUncommittedChanges(repository, project)) {
                        hasUncommitted = true;
                        break;
                    }
                }

                if (hasUncommitted) {
                    AtomicBoolean userConfirmed = new AtomicBoolean(false);
                    AtomicReference<String> commitMsgRef = new AtomicReference<>("");

                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        FileDocumentManager.getInstance().saveAllDocuments();
                        UncommittedChangesDialog uncommittedDialog = new UncommittedChangesDialog(project);
                        if (uncommittedDialog.showAndGet()) {
                            userConfirmed.set(true);
                            commitMsgRef.set(uncommittedDialog.getCommitMessage());
                        }
                    });

                    setProgress(2, project);

                    if (!userConfirmed.get()) {
                        return; // Canceled -> abort operation
                    }

                    String commitMessage = commitMsgRef.get();
                    try {
                        for (GitRepository repository : getRepositories(project)) {
                            if (hasUncommittedChanges(repository, project)) {
                                executor.execute(repository.getRoot(), GitCommand.ADD, "-A");
                                try {
                                    executor.execute(repository.getRoot(), GitCommand.COMMIT, "-m", commitMessage);
                                } catch (GitException ex) {
                                    String msg = ex.getGitResult() != null ? ex.getGitResult().getProcessMessage() : ex.getMessage();
                                    if (msg == null || (!msg.contains("nothing to commit") && !msg.contains("working tree clean"))) {
                                        throw ex;
                                    }
                                }
                                repository.update();
                                VfsUtil.markDirtyAndRefresh(false, true, true, repository.getRoot());
                            }
                        }
                        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    } catch (GitException ex) {
                        NotificationUtil.showGitFlowErrorNotification(project, "Commit Error", ex.getGitResult().getProcessMessage());
                        return;
                    }
                }

                setProgress(3, project);

                // Update remote references via fetch to detect new commits in origin/develop and feature branch
                for (GitRepository repository : getRepositories(project)) {
                    try {
                        executor.execute(repository.getRoot(), GitCommand.FETCH, REMOTE);
                        repository.update();
                    } catch (Exception ignored) {
                    }
                }

                setProgress(4, project);

                // 2. Check for unpushed commits next
                List<GitCommit> unpushedCommits = new ArrayList<>();
                for (GitRepository repository : getRepositories(project)) {
                    try {
                        unpushedCommits.addAll(GitHistoryUtils.history(
                                project, repository.getRoot(),
                                REMOTE + "/" + branchName + ".." + branchName
                        ));
                    } catch (VcsException vcsEx) {
                        // Remote tracking branch may not exist yet; check against develop
                        try {
                            unpushedCommits.addAll(GitHistoryUtils.history(
                                    project, repository.getRoot(),
                                    developBranch + ".." + branchName
                            ));
                        } catch (VcsException ignored) {
                        }
                    }
                }

                setProgress(5, project);

                if (!unpushedCommits.isEmpty()) {
                    AtomicBoolean userConfirmedPush = new AtomicBoolean(false);

                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        UnpushedCommitsDialog unpushedDialog = new UnpushedCommitsDialog(project, branchName, unpushedCommits);
                        if (unpushedDialog.showAndGet()) {
                            userConfirmedPush.set(true);
                        }
                    });

                    if (!userConfirmedPush.get()) {
                        return; // Canceled -> abort operation
                    }

                    try {
                        for (GitRepository repository : getRepositories(project)) {
                            executor.execute(repository.getRoot(), GitCommand.PUSH, "-u", REMOTE, branchName);
                            repository.update();
                        }
                    } catch (GitException ex) {
                        NotificationUtil.showGitFlowErrorNotification(project, "Push Error", ex.getGitResult().getProcessMessage());
                        return;
                    }
                }

                setProgress(6, project);

                // 3. Check if feature branch is behind develop (local and remote)
                Map<String, GitCommit> behindMap = new LinkedHashMap<>();
                String featureCommits = "";
                for (GitRepository repository : getRepositories(project)) {
                    try {
                        List<GitCommit> behindRemote = GitHistoryUtils.history(
                                project, repository.getRoot(),
                                branchName + ".." + REMOTE + "/" + developBranch
                        );
                        for (GitCommit c : behindRemote) {
                            behindMap.put(c.getId().asString(), c);
                        }
                    } catch (VcsException ignored) {
                    }

                    setProgress(7, project);

                    try {
                        List<GitCommit> behindLocal = GitHistoryUtils.history(
                                project, repository.getRoot(),
                                branchName + ".." + developBranch
                        );
                        for (GitCommit c : behindLocal) {
                            behindMap.put(c.getId().asString(), c);
                        }
                    } catch (VcsException ignored) {
                    }

                    if (featureCommits.isEmpty()) {
                        try {
                            featureCommits = getFeatureCommits(project, repository, developBranch, branchName);
                        } catch (VcsException ignored) {
                        }
                    }
                }

                setProgress(8, project);

                int commitsBehindCount = behindMap.size();
                List<String> warnings = new ArrayList<>();
                boolean isBehind = commitsBehindCount > 0;
                if (isBehind) {
                    warnings.add("Branch '" + branchName + "' is behind '" + developBranch + "' by " + commitsBehindCount + " commit" + (commitsBehindCount > 1 ? "s" : "") + ".");
                }

                AtomicBoolean finishConfirmed = new AtomicBoolean(false);
                AtomicReference<ActionChoiceDialog> dialogRef = new AtomicReference<>();
                final String finalFeatureCommits = featureCommits;

                setProgress(10, project);

                ApplicationManager.getApplication().invokeAndWait(() -> {
                    ActionChoiceDialog dialog = new ActionChoiceDialog(project, branchName, developBranch, finalFeatureCommits, warnings, isBehind);
                    dialogRef.set(dialog);
                    if (dialog.showAndGet()) {
                        finishConfirmed.set(true);
                    }
                });

                if (finishConfirmed.get()) {
                    ActionChoiceDialog dialog = dialogRef.get();
                    String[] postAction = new String[1];
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

                        NotificationUtil.showGitFlowSuccessNotification(project, "Success", postAction[0]);
                    } catch (GitException ex) {
                        NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                    } catch (Throwable ex) {
                        ExceptionUtil.handleException(project, ex);
                    }
                }
            } catch (Exception ex) {
                ExceptionUtil.handleException(project, ex);
            } finally {
                setProgress(10, project);
            }
        });
    }

    private boolean hasUncommittedChanges(GitRepository repository, Project project) {
        try {
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.STATUS);
            handler.addParameters("--porcelain");
            GitCommandResult result = Git.getInstance().runCommand(handler);
            if (result.success()) {
                return !result.getOutputAsJoinedString().trim().isEmpty();
            }
        } catch (Exception ignored) {
        }
        return !ChangeListManager.getInstance(project).getChangesIn(repository.getRoot()).isEmpty();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                !getRepositories(e.getProject()).isEmpty() &&
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
        GitExecutor executor = new GitExecutor(project);
        List<GitResult> results = new ArrayList<>();

        for (GitRepository repository : getRepositories(project)) {
            VirtualFile root = repository.getRoot();

            switch (mode) {

                // =========================
                // INTEGRATE
                // =========================
                case ActionChoiceDialog.INTEGRATE -> {

                    // checkout develop
                    results.add(
                            executor.execute(root, GitCommand.CHECKOUT, baseBranch)
                    );

                    setProgress(2, project);

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
                        setProgress(4, project);

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

                    }

                    setProgress(6, project);

                    // push to develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.PUSH,
                                    REMOTE,
                                    baseBranch
                            )
                    );

                    GitFlowSettingsService settingsService = GitFlowSettingsService.getInstance(project);
                    RepoCiEntry repoEntry = settingsService.getRepoCiEntry(root.getPath());
                    CiServerConfig ciConfig = repoEntry != null ? repoEntry.ciServer : null;
                    if (ciConfig != null && ciConfig.isActive() && !ciConfig.isDoNothing()) {
                        CIDataToolWindowPanel.startMonitoringForRepo(project, root.getPath());
                        if (ciConfig.isTriggerBuild()) {
                            String token = settingsService.getTokenForRepo(root.getPath());
                            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                                try {
                                    JenkinsConnector connector = new JenkinsConnector(
                                            ciConfig.getCiUrl(),
                                            ciConfig.getCiLogin(),
                                            token != null ? token : ""
                                    );
                                    connector.triggerBuild();
                                } catch (Exception ex) {
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        NotificationUtil.showGitFlowErrorNotification(
                                                project,
                                                "CI/CD Error",
                                                "Failed to trigger build on feature finish: " + ex.getMessage()
                                        );
                                    });
                                }
                            });
                        }
                    }

                    postAction[0] = "Feature finished and pushed to '" + getDevelopBranch(project) + "' successfully.";

                    setProgress(8, project);

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

                    setProgress(2, project);

                    // push feature branch
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.PUSH,
                                    REMOTE,
                                    featureBranch
                            )
                    );

                    setProgress(5, project);

                    // checkout develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.CHECKOUT,
                                    baseBranch
                            )
                    );

                    postAction[0] = "Feature branch '" + branchName + "' pushed successfully. You can now create a merge/pull request.";

                    setProgress(8, project);
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

                    setProgress(2, project);

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

                    setProgress(7, project);

                    // checkout develop
                    results.add(
                            executor.execute(
                                    root,
                                    GitCommand.CHECKOUT,
                                    baseBranch
                            )
                    );

                    postAction[0] = "Feature branch '" + branchName + "' pushed and merge request created successfully.";
                }
            }

            setProgress(9, project);
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
            VfsUtil.markDirtyAndRefresh(false, true, true, repository.getRoot());
            setProgress(10, project);
        }
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();

        return results;
    }
}
