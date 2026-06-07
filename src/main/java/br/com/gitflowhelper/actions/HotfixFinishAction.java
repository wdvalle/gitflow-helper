package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class HotfixFinishAction extends BaseAction {

    public HotfixFinishAction() {
//        super(actionTitle, GitFlowDescriptions.HOTFIX_FINISH.getValue(), AllIcons.Vcs.Patch_applied);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = getProject();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true);
            try {
                hotfixFinish(project,true, true, true);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Hotfix finished and tag pushed successfully");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
            }
            setLoading(false);
        });
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch()) &&
                        getBranchName() != null && getBranchName().startsWith(getHotfixPrefix())
        );
    }

    public List<GitResult> hotfixFinish(
            Project project, boolean deleteLocalBranch,
            boolean deleteRemoteBranch,
            boolean tagAndPush
    ) {
        setProgress(1);

        List<GitResult> results = new ArrayList<>();
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        String mainBranch = getMainBranch();
        String developBranch = getDevelopBranch();

        for (GitRepository repository : repoManager.getRepositories()) {

            VirtualFile root = repository.getRoot();

            GitLocalBranch hotfixBranch = repository.getCurrentBranch();
            if (hotfixBranch == null) {
                throw new GitException("Branch atual não encontrada.");
            }

            setProgress(2);

            String hotfixName = hotfixBranch.getName();

            if (!hotfixName.startsWith("hotfix/")) {
                throw new GitException(
                        "Branch atual não é hotfix: " + hotfixName
                );
            }

            String tagName = hotfixName.replace("hotfix/", "");

            // 1️⃣ checkout main
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, mainBranch)
            );
            setProgress(3);

            // 2️⃣ merge hotfix -> main
            results.add(
                    executor.execute(
                            root,
                            GitCommand.MERGE,
                            "--no-ff",
                            hotfixName
                    )
            );
            setProgress(4);

            // 3️⃣ tag
            results.add(
                    executor.execute(
                            root,
                            GitCommand.TAG,
                            tagName
                    )
            );
            setProgress(5);

            // 4️⃣ checkout develop
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, developBranch)
            );

            setProgress(6);

            // 5️⃣ merge hotfix -> develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.MERGE,
                            "--no-ff",
                            hotfixName
                    )
            );
            setProgress(7);

            // 6️⃣ delete hotfix local
            if (deleteLocalBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.BRANCH,
                                "-d",
                                hotfixName
                        )
                );
            }

            setProgress(8);

            GitResult checkResult = executor.execute(
                    root,
                    GitCommand.LS_REMOTE,
                    "--heads",
                    REMOTE,
                    hotfixName
            );
            boolean branchExists = checkResult.getExitCode() == 0 &&
                    checkResult.getProcessMessage().contains("refs/heads/" + hotfixName);

            // 7️⃣ delete hotfix remote
            if (branchExists && deleteRemoteBranch) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                "origin",
                                "--delete",
                                hotfixName
                        )
                );
            }
            setProgress(9);
            // 8️⃣ push final
            if (tagAndPush) {
                results.add(
                        executor.execute(root, GitCommand.PUSH, "origin", mainBranch)
                );
                results.add(
                        executor.execute(root, GitCommand.PUSH, "origin", developBranch)
                );
                results.add(
                        executor.execute(root, GitCommand.PUSH, "origin", "--tags")
                );
            }

            repository.update();
            setProgress(10);
        }

        return results;
    }
}
