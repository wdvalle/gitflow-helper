package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.tasks.TasksBridge;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
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
            TasksBridge bridge = TasksBridge.getInstance();
            boolean hasTask = bridge != null && bridge.hasActiveTask(project);
            e.getPresentation().setEnabled(hasTask);
            if (hasTask) {
                e.getPresentation().setText("Open Task: " + bridge.getActiveTaskName(project));
            } else {
                e.getPresentation().setText("Open Current Task");
            }
        }
    }

    @Override
    protected void actionPerformedImpl(@NotNull AnActionEvent e) throws Exception {
        Project project = e.getProject();
        if (project == null) return;

        TasksBridge bridge = TasksBridge.getInstance();
        if (bridge != null) {
            bridge.openActiveTaskInBrowser(project);
        }
    }
}
