package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.UsageDialog;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class UsageAction extends BaseAction {

    public UsageAction() {
        this("Usage...");
    }

    public UsageAction(String actionTitle) {
        super(actionTitle, GitFlowDescriptions.USAGE.getValue(), AllIcons.General.ContextHelp);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        UsageDialog dialog = new UsageDialog(project);
        dialog.show();
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
    }
}
