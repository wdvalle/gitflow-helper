package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.dialog.NameDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
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
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class FeatureStartAction extends BaseAction {

    public FeatureStartAction() {
//        super(GitFlowDescriptions.FEATURE_START::getValue, AllIcons.Actions.Execute);
    }

    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = getProject();
        new NameDialog(project, GitFlowBranchType.FEATURE.getValue() + " start", "Feature description", false, response ->
        {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                setLoading(true, true);
                try {
                    featureStart(project, response.getName());

                    Task selectedTask = response.getSelectedTask();
                    if (selectedTask != null && response.isActivateTask() && GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
                        TaskManager.getManager(project).activateTask(selectedTask, true);
                    }

                    NotificationUtil.showGitFlowSuccessNotification(project, "Success", "New feature created successfully");
                } catch (GitException ex) {
                    NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                }
                setLoading(false);
            });
        }
        ).show();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch()) &&
                        getBranchName() != null && getBranchName().equals(getDevelopBranch())
        );
    }

    private List<GitResult> featureStart(Project project, String featureName) {
        setProgress(1);

        String featureBranch = getFeaturePrefix() + featureName;
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);
        List<GitResult> results = new ArrayList<>();

        for (GitRepository repository : repoManager.getRepositories()) {
            VirtualFile root = repository.getRoot();

            setProgress(2);
            // 1. checkout develop
            results.add(
                    executor.execute(root, GitCommand.CHECKOUT, getDevelopBranch())
            );

            setProgress(5);

            // 2. pull develop
            results.add(
                    executor.execute(root, GitCommand.PULL)
            );

            setProgress(7);

            // 3 + 4. create + checkout feature branch
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            "-b",
                            featureBranch,
                            getDevelopBranch()
                    )
            );

            setProgress(9);

            repository.update();
            setProgress(10);
        }
        return results;
    }

}
