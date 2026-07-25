package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ToggleCIAction extends AnAction {

    private final CIDataToolWindowPanel ciDataToolWindowPanel;
    private boolean isRunning = false;

    public ToggleCIAction(CIDataToolWindowPanel ciDataToolWindowPanel) {
        super("Start/Stop CI Monitoring", "Start or stop monitoring the CI server", AllIcons.Actions.Execute);
        this.ciDataToolWindowPanel = ciDataToolWindowPanel;
        // When monitoring stops automatically (build finished / error), reset state and refresh the toolbar button
        ciDataToolWindowPanel.setOnStopped(() -> {
            isRunning = false;
            ActivityTracker.getInstance().inc();
        });
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (isRunning) {
            ciDataToolWindowPanel.stopMonitoring();
            isRunning = false;
        } else {
            ciDataToolWindowPanel.startMonitoring();
            isRunning = true;
        }
        update(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Enabled only when the selected repository has a CI URL configured
        String selectedPath = ciDataToolWindowPanel.getSelectedRepoPath();
        boolean isActive;
        if (selectedPath != null) {
            isActive = GitFlowSettingsService.getInstance(project)
                    .isIntegrateWithCIForRepo(selectedPath);
        } else {
            // No specific selection → check if any repo has CI configured
            isActive = GitFlowSettingsService.getInstance(project).isIntegrateWithCI();
        }

        e.getPresentation().setEnabled(isActive);

        if (isRunning) {
            e.getPresentation().setIcon(AllIcons.Actions.Suspend);
            e.getPresentation().setText("Stop CI Monitoring");
        } else {
            e.getPresentation().setIcon(AllIcons.Actions.Execute);
            e.getPresentation().setText("Start CI Monitoring");
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
