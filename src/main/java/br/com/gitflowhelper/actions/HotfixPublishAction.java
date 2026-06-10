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
public class HotfixPublishAction extends BaseAction {

    public HotfixPublishAction() {
//        super(actionTitle, GitFlowDescriptions.HOTFIX_PUBLISH.getValue(), AllIcons.CodeWithMe.CwmShared);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true, project);
            try {
                hotfixPublish(project);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Hotfix published successfully");
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
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).startsWith(getHotfixPrefix(e.getProject()))
        );
    }

    private List<GitResult> hotfixPublish(Project project) {
        setProgress(1, project);

        List<GitResult> results = new ArrayList<>();
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        for (GitRepository repository : repoManager.getRepositories()) {

            VirtualFile root = repository.getRoot();

            GitLocalBranch currentBranch = repository.getCurrentBranch();
            if (currentBranch == null) {
                throw new GitException("Não foi possível identificar a branch atual.");
            }

            setProgress(3, project);

            String branchName = currentBranch.getName();

            // Validação básica de Git Flow
            if (!branchName.startsWith("hotfix/")) {
                throw new GitException(
                        "Branch atual não é uma hotfix: " + branchName
                );
            }

            setProgress(6, project);

            // git push -u origin hotfix/<name>
            results.add(
                    executor.execute(
                            root,
                            GitCommand.PUSH,
                            "-u",
                            "origin",
                            branchName
                    )
            );

            setProgress(9, project);

            repository.update();

            setProgress(10, project);
        }

        return results;
    }
}
