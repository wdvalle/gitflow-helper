package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.NotNull;

public class ViewTaskAction extends BaseAction {

    public ViewTaskAction() {
        super("Open Current Task", "Open the current task in browser", AllIcons.General.Web);
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        boolean integrate = GitFlowSettingsService.getInstance(project).isIntegrateWithTasks();
        e.getPresentation().setVisible(integrate);

        if (integrate) {
            LocalTask activeTask = TaskManager.getManager(project).getActiveTask();
            boolean hasTask = activeTask != null && !activeTask.isDefault();
            e.getPresentation().setEnabled(hasTask);
            if (hasTask) {
                e.getPresentation().setText("Open Task: " + activeTask.getPresentableName());
            } else {
                e.getPresentation().setText("Open Current Task");
            }
        }
    }

    @Override
    protected void actionPerformedImpl(@NotNull AnActionEvent e) throws Exception {
        Project project = e.getProject();
        if (project == null) return;

        LocalTask activeTask = TaskManager.getManager(project).getActiveTask();
        if (activeTask != null && !activeTask.isDefault()) {
            String url = activeTask.getIssueUrl();
            if (url != null && !url.isEmpty()) {
                BrowserUtil.browse(url);
            }
        }
    }
}
