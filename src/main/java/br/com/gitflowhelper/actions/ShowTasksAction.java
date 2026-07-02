package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.ShowTasksDialog;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowTasksAction extends BaseAction {

    public ShowTasksAction() {
        super("Show project tasks", "Show issues from integrations", AllIcons.Actions.Find);
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
    }

    @Override
    protected void actionPerformedImpl(@NotNull AnActionEvent e) throws Exception {
        Project project = e.getProject();
        if (project == null) return;

        new ShowTasksDialog(project).show();
    }
}
