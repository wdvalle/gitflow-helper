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
        super("Stop CI Monitoring", "Stop monitoring the CI server", AllIcons.Actions.Suspend);
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
        boolean active = ciDataToolWindowPanel.isMonitoringActive();
        e.getPresentation().setEnabled(active);
        e.getPresentation().setIcon(AllIcons.Actions.Suspend);
        e.getPresentation().setText("Stop CI Monitoring");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
