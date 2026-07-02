package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.TaskFormatter;
import br.com.gitflowhelper.util.TaskProjectFilter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TasksToolWindowPanel extends JPanel {
    private final Project project;
    private final JBList<GFTask> taskList;
    private final JEditorPane taskDescriptionPane;
    private final TaskFormatter taskFormatter;
    private final TaskProjectFilter taskProjectFilter;
    private boolean showAllIssues = false;

    public TasksToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.taskFormatter = new TaskFormatter(project);
        this.taskProjectFilter = new TaskProjectFilter(project);

        taskList = new JBList<>(new CollectionListModel<>());
        taskList.getEmptyText().setText("No tasks found");
        taskList.getEmptyText().appendLine("Go to ");
        taskList.getEmptyText().appendText("Settings -> Tools -> Tasks -> Servers", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Tasks");
        });
        taskList.getEmptyText().appendLine(" to configure access to a task server.");

        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GFTask task) {
                    setText(task.getPresentableId() + ": " + task.getSummary());
                    setIcon(task.getIcon());
                }
                return this;
            }
        });

        taskDescriptionPane = new JEditorPane("text/html", "");
        taskDescriptionPane.setEditable(false);
        taskDescriptionPane.setBackground(taskList.getBackground());
        taskDescriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        taskDescriptionPane.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                com.intellij.ide.BrowserUtil.browse(e.getURL());
            }
        });

        taskList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                GFTask selectedTask = taskList.getSelectedValue();
                if (selectedTask != null) {
                    taskDescriptionPane.setText(taskFormatter.formatTaskDetail(selectedTask));
                    taskDescriptionPane.setCaretPosition(0);
                } else {
                    taskDescriptionPane.setText("");
                }
            }
        });

        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.3f);
        splitter.setFirstComponent(new JBScrollPane(taskList));
        splitter.setSecondComponent(new JBScrollPane(taskDescriptionPane));

        add(splitter, BorderLayout.CENTER);

        setupToolbar();
        loadTasksAsync();
    }

    private void setupToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        
        ToggleAction toggleFilterAction = new ToggleAction("Show All Issues", "Show all issues or only project issues", AllIcons.General.Filter) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return showAllIssues;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                showAllIssues = state;
                loadTasksAsync();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        actionGroup.add(toggleFilterAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "TasksToolWindowToolbar",
                actionGroup,
                true
        );
        toolbar.setTargetComponent(this);
        toolbar.getComponent().setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
        
        add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    private void loadTasksAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<GFTask> tasks = getTasks();
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    taskList.setModel(new CollectionListModel<>(tasks));
                }
            });
        });
    }

    private List<GFTask> getTasks() {
        return ApplicationManager.getApplication().runReadAction((Computable<List<GFTask>>) () -> {
            try {
                TaskManager taskManager = TaskManager.getManager(project);
                List<Task> allTasks = new ArrayList<>(taskManager.getIssues("", 0, 100, false, new EmptyProgressIndicator(), false));

                List<String> projectPaths = taskProjectFilter.getProjectPaths();
                List<GFTask> tasks = new ArrayList<>();

                if (showAllIssues || projectPaths.isEmpty()) {
                    tasks.addAll(allTasks.stream().map(GFTask::new).toList());
                } else {
                    tasks.addAll(allTasks.stream()
                            .filter(task -> taskProjectFilter.isTaskFromProject(task, projectPaths))
                            .map(GFTask::new).toList());
                }
                return tasks;
            } catch (Exception ex) {
                ExceptionUtil.handleException(project, ex);
            }
            return new ArrayList<>();
        });
    }
}
