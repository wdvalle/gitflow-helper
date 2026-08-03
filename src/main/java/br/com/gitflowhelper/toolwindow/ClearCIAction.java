package br.com.gitflowhelper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ClearCIAction extends AnAction {

    private final CIDataToolWindowPanel ciDataToolWindowPanel;

    public ClearCIAction(CIDataToolWindowPanel ciDataToolWindowPanel) {
        super("Clear Log", "Clear the CI log", AllIcons.Actions.GC);
        this.ciDataToolWindowPanel = ciDataToolWindowPanel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ciDataToolWindowPanel.clear();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
