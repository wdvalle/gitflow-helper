package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ReleaseFinishAction extends BaseAction {

    public ReleaseFinishAction() {
//        super(actionTitle, GitFlowDescriptions.RELEASE_FINISH.getValue(), AllIcons.Vcs.Patch_applied);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true, project);
            try {
                releaseFinish(project, true, true, true);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Released finished and tag pushed successfully");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
            }
            setLoading(false, project);
        });
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch(e.getProject())) &&
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).startsWith(getReleasePrefix(e.getProject()))
        );
    }

    public List<GitResult> releaseFinish(
            Project project,
            boolean deleteLocalBranch,
            boolean deleteRemoteBranch,
            boolean tagAndPush
    ) {
        setProgress(1, project);
        List<GitResult> results = new ArrayList<>();
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        for (GitRepository repository : repoManager.getRepositories()) {
            String releaseName = repository.getCurrentBranchName().substring(
                    repository.getCurrentBranchName().indexOf("/")+1
            );
            String releaseBranch = repository.getCurrentBranchName();
            String tagMessage = String.format("Merge branch '%s' into %s", releaseBranch, getMainBranch(project));

            VirtualFile root = repository.getRoot();

            // 1 checkout main
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, getMainBranch(project))
            );
            setProgress(2, project);

            // 2 pull main
            results.add(
                    executor.execute(root, GitCommand.PULL, REMOTE, getMainBranch(project))
            );
            setProgress(3, project);

            // 3 merge release -> main
            results.add(
                    executor.execute(
                            root,
                            GitCommand.MERGE,
                            "--no-ff",
                            "-m",
                            tagMessage,
                            releaseBranch
                    )
            );
            setProgress(4, project);

            // 4 cria tag
            if (tagAndPush) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.TAG,
                                "-a",
                                releaseName,
                                "-m",
                                tagMessage
                        )
                );
            }
            setProgress(5, project);

            // 5 checkout develop
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, getDevelopBranch(project))
            );

            // 6 pull develop
            results.add(
                    executor.execute(root, GitCommand.PULL, REMOTE, getDevelopBranch(project))
            );
            setProgress(6, project);

            // 7 merge release -> develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.MERGE,
                            "--no-ff",
                            "-m",
                            tagMessage,
                            releaseBranch
                    )
            );
            setProgress(7, project);

            // 8 push branches + tag
            if (tagAndPush) {
                results.add(
                        executor.execute(root, GitCommand.PUSH, REMOTE, getMainBranch(project))
                );

                results.add(
                        executor.execute(root, GitCommand.PUSH, REMOTE, getDevelopBranch(project))
                );

                results.add(
                        executor.execute(root, GitCommand.PUSH, REMOTE, releaseName)
                );
            }

            setProgress(8, project);
            // 9 delete local release branch
            if (deleteLocalBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.BRANCH,
                                "-d",
                                releaseBranch
                        )
                );
            }


            GitResult checkResult = executor.execute(
                    root,
                    GitCommand.LS_REMOTE,
                    "--heads",
                    REMOTE,
                    releaseBranch
            );
            boolean branchExists = checkResult.getExitCode() == 0 &&
                    checkResult.getProcessMessage().contains("refs/heads/" + releaseBranch);

            setProgress(9, project);
            // 10 delete remote release branch
            if (branchExists && deleteRemoteBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                REMOTE,
                                "--delete",
                                releaseBranch
                        )
                );
            }

            repository.update();
            setProgress(10, project);
        }

        return results;
    }
}
