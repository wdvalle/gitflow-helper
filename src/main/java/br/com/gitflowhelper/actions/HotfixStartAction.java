package br.com.gitflowhelper.actions;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.dialog.NameDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.GitFlowBranchType;
import br.com.gitflowhelper.util.NotificationUtil;
import com.G.G.B.B.GF;
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
public class HotfixStartAction extends BaseAction {

    public HotfixStartAction() {
//        super(actionTitle, GitFlowDescriptions.HOTFIX_START.getValue(), AllIcons.Actions.Execute);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        new NameDialog(project, GitFlowBranchType.HOTFIX.getValue() + " start", "Hotfix description", true, response ->
        {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                setLoading(true, true, project);
                try {
                    hotfixStart(project, response.getName(), response.getPushOnFinish());

                    GFTask selectedTask = response.getSelectedTask();
                    if (selectedTask != null && response.isActivateTask() && GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            TaskManager.getManager(project).activateTask(selectedTask.getTask(), true);
                        }, project.getDisposed());
                    }

                    NotificationUtil.showGitFlowSuccessNotification(project, "Success", "New hotfix created successfully");
                } catch (GitException ex) {
                    NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                } catch (Throwable ex) {
                    ExceptionUtil.handleException(project, ex);
                }
                setLoading(false, project);
            });
        }
        ).show();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(
                StringUtil.isNotEmpty(getMainBranch(e.getProject())) &&
                        getBranchName(e.getProject()) != null && getBranchName(e.getProject()).equals(getMainBranch(e.getProject()))
        );
    }

    private List<GitResult> hotfixStart(Project project, String hotfixName, boolean pushOnFinish) {
        setProgress(1, project);

        List<GitResult> results = new ArrayList<>();
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        String mainBranch = getMainBranch(project);
        String hotfixBranch = getHotfixPrefix(project) + hotfixName;

        for (GitRepository repository : repoManager.getRepositories()) {

            VirtualFile root = repository.getRoot();

            // 1) checkout main
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            mainBranch
                    )
            );
            setProgress(2, project);

            // 2) pull main
            results.add(
                    executor.execute(
                            root,
                            GitCommand.PULL,
                            "origin",
                            mainBranch
                    )
            );

            setProgress(5, project);

            // 3) create hotfix branch from main
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            "-b",
                            hotfixBranch,
                            mainBranch
                    )
            );

            setProgress(7, project);

            // 4) push hotfix (if needed)
            if (pushOnFinish) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                "-u",
                                "origin",
                                hotfixBranch
                        )
                );
            }

            setProgress(9, project);
            repository.update();
            setProgress(10, project);
        }

        return results;
    }
}
