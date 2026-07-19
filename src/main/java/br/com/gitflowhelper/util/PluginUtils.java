package br.com.gitflowhelper.util;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.statusbar.GitFlowStatusBarWidget;
import br.com.gitflowhelper.toolwindow.ToolWindowPanel;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
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
            ToolWindow toolWindow = toolWindowManager.getToolWindow("GitFlow");

            if (toolWindow != null && toolWindow.getContentManager().getContentCount() > 0) {
                toolWindow.setIcon(icons.PluginIcons.GitFlowGrayLive);
                Content content = toolWindow.getContentManager().getContent(0);

                if (content != null && content.getComponent() instanceof ToolWindowPanel) {
                    ToolWindowPanel panel = (ToolWindowPanel) content.getComponent();
                    panel.append(message);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Task Management plugin detection
    // -------------------------------------------------------------------------

    private static final String TASKS_PLUGIN_ID = "com.intellij.tasks";

    /**
     * Returns {@code true} if the "Task Management" plugin ({@code com.intellij.tasks})
     * is installed <em>and</em> enabled in the current IDE instance.
     * <p>
     * This check is safe to call even when the plugin is absent — it uses
     * {@link PluginManagerCore}, which is always part of the IntelliJ Platform.
     * </p>
     */
    public static boolean isTasksPluginInstalled() {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(TASKS_PLUGIN_ID));
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Convenience inverse of {@link #isTasksPluginInstalled()}.
     * Returns {@code true} when the plugin is absent <em>or</em> disabled.
     */
    public static boolean isTasksPluginMissing() {
        return !isTasksPluginInstalled();
    }

    // -------------------------------------------------------------------------
    // Stack-trace helper
    // -------------------------------------------------------------------------

    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static void setLoading(boolean loading, Project project) {
        setLoading(loading, false, project);
    }

    public static void setLoading(boolean loading, boolean progress, Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
            if (statusBar != null) {
                GitFlowStatusBarWidget sbw = (GitFlowStatusBarWidget) statusBar.getWidget("GitFlowWidget");
                if (sbw != null) {
                    sbw.setLoading(loading);
                    if (progress) {
                        setProgressImpl(0, statusBar, sbw);
                    } else {
                        sbw.setCurrentValue("GitFlowHelper");
                    }
                    statusBar.updateWidget("GitFlowWidget");
                }
            }
        }, project.getDisposed());
    }

    public static void setProgress(Integer value, Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
            if (statusBar != null) {
                GitFlowStatusBarWidget sbw = (GitFlowStatusBarWidget) statusBar.getWidget("GitFlowWidget");
                if (sbw != null) {
                    setProgressImpl(value, statusBar, sbw);
                }
            }
        }, project.getDisposed());
    }

    private static void setProgressImpl(Integer value, StatusBar statusBar, GitFlowStatusBarWidget sbw) {
        sbw.setProgress(value);
        statusBar.updateWidget("GitFlowWidget");
    }

}