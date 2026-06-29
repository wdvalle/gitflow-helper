package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.NameDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.GitFlowBranchType;
import br.com.gitflowhelper.util.NotificationUtil;
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
public class FeatureStartAction extends BaseAction {

    public FeatureStartAction() {
//        super(GitFlowDescriptions.FEATURE_START::getValue, AllIcons.Actions.Execute);
    }

    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        new NameDialog(project, GitFlowBranchType.FEATURE.getValue() + " start", "Feature description", false,true, GitFlowBranchType.FEATURE, response ->
        {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                setLoading(true, true, project);
                try {
                    featureStart(project, response.getName());

                    doStartTask(response.getSelectedTask(), response.isActivateTask(), response.getUsername(), project);

                    NotificationUtil.showGitFlowSuccessNotification(project, "Success", "New feature created successfully");

                } catch (GitException ex) {
                    NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                } catch (Throwable ex) {
                    ExceptionUtil.handleException(project, ex);
                }
                setLoading(false, project);
            });
        }).show();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch(e.getProject())) &&
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).equals(getDevelopBranch(e.getProject()))
        );
    }

    private List<GitResult> featureStart(Project project, String featureName) {
        setProgress(1, project);

        String featureBranch = getFeaturePrefix(project) + featureName;
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);
        List<GitResult> results = new ArrayList<>();

        for (GitRepository repository : repoManager.getRepositories()) {
            VirtualFile root = repository.getRoot();

            setProgress(2, project);
            // 1. checkout develop
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, getDevelopBranch(project))
            );

            setProgress(5, project);

            // 2. pull develop
            results.add(
                    executor.execute(root, GitCommand.PULL)
            );

            setProgress(7, project);

            // 3 + 4. create + checkout feature branch
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            "-b",
                            featureBranch,
                            getDevelopBranch(project)
                    )
            );

            setProgress(9, project);

            repository.update();
            setProgress(10, project);
        }
        return results;
    }

}
