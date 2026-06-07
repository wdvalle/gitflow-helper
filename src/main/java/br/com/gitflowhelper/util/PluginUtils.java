package br.com.gitflowhelper.util;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.toolwindow.ToolWindowPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class PluginUtils {

    public static void logError(Project project, String message) {
        logToMyWindow(project, "<pre style=\"margin:0; padding:0;\">"+
                "<font color=\"orange\">"+message+"</font>" +
                "</pre>");
    }

    public static void logCommand(Project project, String message) {
        var show = GitFlowSettingsService.getInstance(project).getShowDetails();
        if (show != null && !show) {
            message = HtmlGitCleaner.commentGitCParams(message);
        }
        logToMyWindow(project, "<pre style=\"margin:0; padding:0\">$ "+
                "<font color=\"#4a8dff\">"+message+"</font>" +
                "</pre>");
    }

    public static void logOutput(Project project, String message) {
        logToMyWindow(project, "<pre style=\"margin:0; padding:0;\">"+message+"</pre>");
    }

    private static void logToMyWindow(Project project, String message) {
        SwingUtilities.invokeLater(() -> {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow("GitFlowOutput");

            if (toolWindow != null && toolWindow.getContentManager().getContentCount() > 0) {
                Content content = toolWindow.getContentManager().getContent(0);

                if (content != null && content.getComponent() instanceof ToolWindowPanel) {
                    ToolWindowPanel panel = (ToolWindowPanel) content.getComponent();
                    panel.append(message);
                }
            }
        });
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

}