package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ClearToolWindowAction extends AnAction {

    private final ToolWindowPanel toolWindowPanel;
    private final JTextPane textPane;

    public ClearToolWindowAction(ToolWindowPanel toolWindowPanel, JTextPane textPane) {
        super(
            "Clear",
            "Clear the text area",
            AllIcons.Actions.GC
        );
        this.toolWindowPanel = toolWindowPanel;
        this.textPane = textPane;
    }

    public ClearToolWindowAction(JTextPane textPane) {
        this(null, textPane);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        int result = Messages.showYesNoDialog(
            e.getProject(),
            "Clear all git flow logs?",
            "Confirmation",
            Messages.getQuestionIcon()
        );
        if (result == Messages.YES) {
            if (toolWindowPanel != null) {
                toolWindowPanel.clear();
            } else {
                this.textPane.setText("<html><body></body></html>");
            }
            Project project = e.getProject();
            if (project != null) {
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GitFlow");
                PluginUtils.clearLiveIndicator(toolWindow);
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Example: enable/disable dynamically
        boolean hasData = true; // replace with real logic
        e.getPresentation().setEnabled(hasData);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
