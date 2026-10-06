package br.com.gitflowhelper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class StopCIAction extends AnAction {

    private final CIDataToolWindowPanel ciDataToolWindowPanel;

    public StopCIAction(CIDataToolWindowPanel ciDataToolWindowPanel) {
        super("Stop CI/CD Execution", "Stop pipeline execution on CI server and cease monitoring", AllIcons.Actions.Suspend);
        this.ciDataToolWindowPanel = ciDataToolWindowPanel;
        ciDataToolWindowPanel.setOnStopped(() -> {
            ActivityTracker.getInstance().inc();
        });
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ciDataToolWindowPanel.stopMonitoring();
        ActivityTracker.getInstance().inc();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        String selected = ciDataToolWindowPanel.getSelectedRepoPath();
        boolean active = ciDataToolWindowPanel.isRunningForRepo(selected);
        e.getPresentation().setEnabled(active);
        e.getPresentation().setIcon(AllIcons.Actions.Suspend);
        e.getPresentation().setText("Stop CI/CD Execution");
        e.getPresentation().setDescription(active
                ? "Stop pipeline execution on CI server and cease monitoring"
                : "No pipeline is currently running");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
