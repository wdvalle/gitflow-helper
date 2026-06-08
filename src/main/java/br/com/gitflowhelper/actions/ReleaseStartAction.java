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
public class ReleaseStartAction extends BaseAction {

    public ReleaseStartAction() {
//        super(actionTitle, GitFlowDescriptions.RELEASE_START.getValue(), AllIcons.Actions.Execute);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = getProject();
        new NameDialog(project, GitFlowBranchType.RELEASE.getValue() + " start", "Version description", true, (response) ->
        {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                setLoading(true, true);
                try {
                    releaseStart(project, response.getName(), response.getPushOnFinish());

                    Task selectedTask = response.getSelectedTask();
                    if (selectedTask != null && response.isActivateTask() && GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
                        TaskManager.getManager(project).activateTask(selectedTask, true);
                    }

                    NotificationUtil.showGitFlowSuccessNotification(project, "Success", "New release created successfully");
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

    public List<GitResult> releaseStart(Project project, String releaseName, boolean push) {
        setProgress(1);
        String releaseBranch = getReleasePrefix() + releaseName;
        List<GitResult> results = new ArrayList<>();
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        for (GitRepository repository : repoManager.getRepositories()) {

            VirtualFile root = repository.getRoot();

            // 1 checkout develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            getDevelopBranch()
                    )
            );
            setProgress(3);

            // 2 pull develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.PULL,
                            REMOTE,
                            getDevelopBranch()
                    )
            );

            setProgress(5);

            // 3 create and checkout release branch
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            "-b",
                            releaseBranch
                    )
            );

            setProgress(7);

            // 4 push release (opcional)
            if (push) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                "-u",
                                REMOTE,
                                releaseBranch
                        )
                );
            }

            setProgress(9);
            // 5 sync with IntelliJ
            repository.update();
            setProgress(10);
        }

        return results;
    }
}
