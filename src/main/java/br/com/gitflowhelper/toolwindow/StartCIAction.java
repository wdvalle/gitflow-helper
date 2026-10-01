package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class StartCIAction extends AnAction {

    private final CIDataToolWindowPanel ciDataToolWindowPanel;

    public StartCIAction(CIDataToolWindowPanel ciDataToolWindowPanel) {
        super("Start CI/CD Pipeline", "Trigger build on CI server and start monitoring", AllIcons.Actions.Execute);
        this.ciDataToolWindowPanel = ciDataToolWindowPanel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String selectedPath = ciDataToolWindowPanel.getSelectedRepoPath();
        ciDataToolWindowPanel.triggerBuildAndMonitor(selectedPath);
        ActivityTracker.getInstance().inc();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String selectedPath = ciDataToolWindowPanel.getSelectedRepoPath();
        boolean hasCI = selectedPath != null
                ? GitFlowSettingsService.getInstance(project).isIntegrateWithCIForRepo(selectedPath)
                : GitFlowSettingsService.getInstance(project).isIntegrateWithCI();

        boolean running = ciDataToolWindowPanel.isRunningForRepo(selectedPath);

        // Start is enabled only if configured AND pipeline is not currently running
        e.getPresentation().setEnabled(hasCI && !running);
        e.getPresentation().setIcon(AllIcons.Actions.Execute);
        e.getPresentation().setText("Start CI/CD Pipeline");
        e.getPresentation().setDescription(!running
                ? "Trigger build on CI server and start monitoring"
                : "Pipeline is currently running");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
