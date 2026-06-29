package br.com.gitflowhelper.util;

import com.intellij.openapi.project.Project;

public class ExceptionUtil {

    public static void handleException(Project project, Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        NotificationUtil.showGitFlowErrorNotification(project, "Error", message);
        PluginUtils.logError(project, PluginUtils.getStackTrace(ex));
    }
}
