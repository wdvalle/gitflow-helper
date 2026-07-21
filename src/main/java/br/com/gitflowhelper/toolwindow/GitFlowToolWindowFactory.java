package br.com.gitflowhelper.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class GitFlowToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // Logs folder
        ToolWindowPanel logsPanel = new ToolWindowPanel(project);
        Content logsContent = contentFactory.createContent(logsPanel, "Git Logs", false);
        toolWindow.getContentManager().addContent(logsContent);

        // Issues folder
        TasksToolWindowPanel tasksPanel = new TasksToolWindowPanel(project);
        Content tasksContent = contentFactory.createContent(tasksPanel, "Issues", false);
        tasksContent.setDisposer(tasksPanel);
        toolWindow.getContentManager().addContent(tasksContent);

        // Flow folder
        GitFlowGraphPanel flowPanel = new GitFlowGraphPanel(project);
        Content flowContent = contentFactory.createContent(flowPanel, "Flow", false);
        toolWindow.getContentManager().addContent(flowContent);

        // CI/CD folder
        CIDataToolWindowPanel ciDataPanel = new CIDataToolWindowPanel(project);
        Content ciDataContent = contentFactory.createContent(createCIContent(ciDataPanel), "CI/CD", false);
        ciDataContent.setDisposer(ciDataPanel);
        toolWindow.getContentManager().addContent(ciDataContent);

        // Show a green bullet next to the tab titles when new content arrives.
        // The bullet is removed when the user selects the respective tab.
        final String logsBaseName  = "Git Logs";
        final String ciBaseName    = "CI/CD";

        logsPanel.setOnNewContent(() -> {
            Content selected = toolWindow.getContentManager().getSelectedContent();
            if (!logsContent.equals(selected)) {
                logsContent.setDisplayName(logsBaseName + " \u26AB"); // ⦁
            }
        });

        ciDataPanel.setOnNewContent(() -> {
            Content selected = toolWindow.getContentManager().getSelectedContent();
            //if (!ciDataContent.equals(selected)) {
                ciDataContent.setDisplayName(ciBaseName + " \u26AB"); // ⦁
            //}
        });

        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                if (logsContent.equals(event.getContent())) {
                    logsContent.setDisplayName(logsBaseName);
                } else if (ciDataContent.equals(event.getContent())) {
                    ciDataContent.setDisplayName(ciBaseName);
                }
            }
        });
    }

    private JComponent createCIContent(CIDataToolWindowPanel ciDataPanel) {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new ToggleCIAction(ciDataPanel));
        actionGroup.add(new ClearCIAction(ciDataPanel));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "CIToolWindowToolbar",
                actionGroup,
                true
        );
        toolbar.setTargetComponent(panel);
        panel.add(toolbar.getComponent(), BorderLayout.NORTH);
        panel.add(ciDataPanel, BorderLayout.CENTER);
        return panel;
    }
}
