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
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class FeaturePublishAction extends BaseAction {

    public FeaturePublishAction() {
//        super(actionTitle, GitFlowDescriptions.FEATURE_PUBLISH.getValue(), AllIcons.CodeWithMe.CwmShared);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true, project);
            try {
                featurePublish(project);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "New feature published successfully");
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
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).startsWith(getFeaturePrefix(e.getProject()))
        );
    }

    private List<GitResult> featurePublish(Project project) {
        setProgress(1, project);
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);
        List<GitResult> results = new ArrayList<>();

        for (GitRepository repository : repoManager.getRepositories()) {
            String currentBranch = repository.getCurrentBranchName();

            setProgress(2, project);

            if (currentBranch == null) {
                throw new GitException("HEAD is detached");
            }

            setProgress(6, project);
            results.add(
                    executor.execute(
                            repository.getRoot(),
                            GitCommand.PUSH,
                            "-u",
                            REMOTE,
                            currentBranch
                    )
            );

            setProgress(7, project);

            repository.update();
            setProgress(10, project);
        }
        return results;
    }

}
