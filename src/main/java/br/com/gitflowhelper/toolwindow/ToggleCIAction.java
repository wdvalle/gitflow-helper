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

    public ToggleCIAction(CIDataToolWindowPanel ciDataToolWindowPanel) {
        super("Start/Stop CI Execution", "Start or stop execution and monitoring of the CI server", AllIcons.Actions.Execute);
        this.ciDataToolWindowPanel = ciDataToolWindowPanel;
        // When monitoring stops automatically (build finished / error), reset state and refresh the toolbar button
        ciDataToolWindowPanel.setOnStopped(() -> {
            ActivityTracker.getInstance().inc();
        });
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String selectedPath = ciDataToolWindowPanel.getSelectedRepoPath();
        boolean running = ciDataToolWindowPanel.isRunningForRepo(selectedPath);
        if (running) {
            ciDataToolWindowPanel.stopMonitoring();
        } else {
            ciDataToolWindowPanel.triggerBuildAndMonitor(selectedPath);
        }
        ActivityTracker.getInstance().inc();
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

        boolean running = ciDataToolWindowPanel.isRunningForRepo(selectedPath);

        e.getPresentation().setEnabled(isActive);

        if (running) {
            e.getPresentation().setIcon(AllIcons.Actions.Suspend);
            e.getPresentation().setText("Stop Pipeline Execution");
        } else {
            e.getPresentation().setIcon(AllIcons.Actions.Execute);
            e.getPresentation().setText("Start CI/CD Pipeline");
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
