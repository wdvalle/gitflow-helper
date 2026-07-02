
package br.com.gitflowhelper.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

public class GitFlowToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // Logs folder
        ToolWindowPanel logsPanel = new ToolWindowPanel(project);
        Content logsContent = contentFactory.createContent(logsPanel, "Logs", false);
        toolWindow.getContentManager().addContent(logsContent);

        // New Issues folder
        TasksToolWindowPanel tasksPanel = new TasksToolWindowPanel(project);
        Content tasksContent = contentFactory.createContent(tasksPanel, "Issues", false);
        toolWindow.getContentManager().addContent(tasksContent);
    }
}
