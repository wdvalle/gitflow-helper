package br.com.gitflowhelper.tasks;

import br.com.gitflow.tracker.GFTask;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * Bridge interface to decouple the main plugin from the optional {@code com.intellij.tasks} plugin.
 * <p>
 * The implementation ({@link TasksBridgeImpl}) is registered as an application service only
 * inside {@code plugin-tasks.xml}, which is loaded conditionally when the Task Management plugin
 * is installed. When the plugin is absent, {@link ApplicationManager#getService(Class)} returns
 * {@code null} and all task-related operations silently become no-ops.
 * </p>
 */
public interface TasksBridge {

    /**
     * Returns {@code true} if the {@code com.intellij.tasks} plugin is installed and enabled.
     */
    static boolean isAvailable() {
        PluginId id = PluginId.getId("com.intellij.tasks");
        var plugin = PluginManagerCore.getPlugin(id);
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Convenient accessor — returns {@code null} when Tasks plugin is not installed.
     */
    static TasksBridge getInstance() {
        return ApplicationManager.getApplication().getService(TasksBridge.class);
    }

    /**
     * Activates the given task and marks it as started on the remote tracker.
     *
     * @param selectedTask  task selected by the user (may be {@code null})
     * @param isActivateTask whether to call {@code TaskManager.activateTask}
     * @param userName       current Git user name
     * @param project        current project
     */
    void startTask(GFTask selectedTask, boolean isActivateTask, String userName, Project project);

    /**
     * Closes the active task and switches back to the default context.
     *
     * @param closeTask whether the user opted to close the task
     * @param project   current project
     */
    void finishTask(boolean closeTask, Project project);

    /**
     * Returns the presentable name of the currently active task, or {@code null} when
     * there is no active (non-default) task.
     */
    String getActiveTaskName(Project project);

    /**
     * Returns {@code true} when there is a non-default active task in the current project.
     */
    boolean hasActiveTask(Project project);

    /**
     * Opens the URL of the currently active task in the default browser.
     */
    void openActiveTaskInBrowser(Project project);

    /**
     * Returns all available tasks (open issues) from the configured task servers.
     * Returns an empty list when there are no configured servers or an error occurs.
     */
    List<GFTask> getAvailableTasks(Project project);
}
