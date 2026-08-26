package br.com.gitflowhelper.tasks;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflow.tracker.IssueTrackerConnector;
import br.com.gitflow.tracker.TrackerFactory;
import br.com.gitflowhelper.events.GitFlowTaskListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Concrete implementation of {@link TasksBridge} that depends on {@code com.intellij.tasks}.
 * <p>
 * This class is registered as an {@code applicationService} <strong>only</strong> inside
 * {@code plugin-tasks.xml}, which is loaded by the IntelliJ Platform <em>conditionally</em>
 * when the Task Management plugin ({@code com.intellij.tasks}) is installed. It is never
 * instantiated when Tasks is absent.
 * </p>
 */
public class TasksBridgeImpl implements TasksBridge {

    @Override
    public void startTask(GFTask selectedTask, boolean isActivateTask, String userName, Project project) {
        if (selectedTask == null || !isActivateTask) return;
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) return;

        markAsStarted(project, selectedTask, userName);

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                TaskManager.getManager(project).activateTask(selectedTask.getTask(), true);
            } catch (Throwable e) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", "Error activating task: " + e.getMessage());
            }
        }, project.getDisposed());
    }

    @Override
    public void finishTask(boolean closeTask, Project project) {
        if (!closeTask) return;
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) return;

        TaskManager taskManager = TaskManager.getManager(project);
        LocalTask activeTask = taskManager.getActiveTask();

        if (!activeTask.isDefault()) {
            markAsFinished(project, activeTask);
            for (LocalTask task : taskManager.getLocalTasks()) {
                if (task.isDefault()) {
                    ApplicationManager.getApplication().invokeLater(
                            () -> taskManager.activateTask(task, false),
                            project.getDisposed()
                    );
                    break;
                }
            }
        }

        project.getMessageBus().syncPublisher(GitFlowTaskListener.TOPIC).tasksChanged();
    }

    @Override
    public String getActiveTaskName(Project project) {
        LocalTask activeTask = TaskManager.getManager(project).getActiveTask();
        if (activeTask != null && !activeTask.isDefault()) {
            return activeTask.getPresentableName();
        }
        return null;
    }

    @Override
    public boolean hasActiveTask(Project project) {
        LocalTask activeTask = TaskManager.getManager(project).getActiveTask();
        return activeTask != null && !activeTask.isDefault();
    }

    @Override
    public void openActiveTaskInBrowser(Project project) {
        LocalTask activeTask = TaskManager.getManager(project).getActiveTask();
        if (activeTask != null && !activeTask.isDefault()) {
            String url = activeTask.getIssueUrl();
            if (url != null && !url.isEmpty()) {
                BrowserUtil.browse(url);
            }
        }
    }

    @Override
    public List<GFTask> getAvailableTasks(Project project) {
        try {
            TaskManager taskManager = TaskManager.getManager(project);
            List<Task> allTasks = new ArrayList<>(
                    taskManager.getIssues("", 0, 100, false, new EmptyProgressIndicator(), false)
            );
            List<GFTask> tasks = new ArrayList<>();
            tasks.add(null); // blank "select a task" entry
            allTasks.stream()
                    .map(GFTask::new)
                    .sorted(Comparator.comparing(GFTask::getPresentableId, String.CASE_INSENSITIVE_ORDER))
                    .forEach(tasks::add);
            return tasks;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void markAsStarted(Project project, GFTask selectedTask, String username) {
        Optional<IssueTrackerConnector> connectorOpt = TrackerFactory.getConnector(project, selectedTask.getTask());
        connectorOpt.ifPresent(connector -> {
            try {
                connector.startIssue(selectedTask.getLocalId());
                connector.assignIssue(selectedTask.getLocalId(), username);
            } catch (Exception e) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", "Error starting issue: " + e.getMessage());
            }
        });

        if (connectorOpt.isEmpty()) {
            NotificationUtil.showGitFlowErrorNotification(project, "Error", "No issue tracker connector found.");
        }
    }

    private void markAsFinished(Project project, LocalTask task) {
        Optional<IssueTrackerConnector> connectorOpt = TrackerFactory.getConnector(project, task);
        connectorOpt.ifPresent(connector -> {
            try {
                //only Jira uses id
                connector.closeIssue(task.getNumber(), task.getId());
            } catch (Exception e) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", "Error closing issue: " + e.getMessage());
            }
        });

        if (connectorOpt.isEmpty()) {
            NotificationUtil.showGitFlowErrorNotification(project, "Error", "No issue tracker connector found.");
        }
    }
}
