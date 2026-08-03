package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.ConfigDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ConfigAction extends BaseAction {

    public ConfigAction(String actionTitle) {
        super(actionTitle, "Git Flow Helper Settings", AllIcons.General.Settings);
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    protected void actionPerformedImpl(@NotNull AnActionEvent e) {
        new ConfigDialog(e.getProject()).show();
    }
}