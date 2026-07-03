package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.PluginUtils;
import br.com.gitflowhelper.util.TaskFormatter;
import com.intellij.ide.ActivityTracker;
//import br.com.gitflowhelper.util.TaskProjectFilter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TasksToolWindowPanel extends JPanel implements DataProvider {
    public static final DataKey<GFTask> SELECTED_TASK = DataKey.create("SELECTED_TASK");
    private final Project project;
    private final JBList<GFTask> taskList;
    private final JBHtmlEditorPane taskDescriptionPane;
    private final TaskFormatter taskFormatter;
    private boolean loading;

    public TasksToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.taskFormatter = new TaskFormatter(project);

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

        taskDescriptionPane = new JBHtmlEditorPane();
        taskDescriptionPane.getEmptyText().setText("Select a task to see its description");
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
                ActivityTracker.getInstance().inc();
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
        AnAction act = new AnAction("Reload Tasks", "Reload tasks from server", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                loadTasksAsync();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(!loading);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };

        actionGroup.add(act);
        act.getTemplatePresentation().setEnabled(false);

        AnAction featureStartAction = ActionManager.getInstance().getAction("GitFlowHelper.FeatureStartAction");
        if (featureStartAction != null) {
            actionGroup.add(featureStartAction);
        }

        AnAction hotfixStartAction = ActionManager.getInstance().getAction("GitFlowHelper.HotfixStartAction");
        if (hotfixStartAction != null) {
            actionGroup.add(hotfixStartAction);
        }

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
        loading = true;
        ActivityTracker.getInstance().inc();
        PluginUtils.setLoading(true, project);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PluginUtils.setProgress(4, project);
            List<GFTask> tasks = getTasks();
            PluginUtils.setProgress(8, project);
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    if (!project.isDisposed()) {
                        taskList.setModel(new CollectionListModel<>(tasks));
                    }
                } finally {
                    loading = false;
                    ActivityTracker.getInstance().inc();
                    PluginUtils.setLoading(false, project);
                }
            });
        });
    }

    private List<GFTask> getTasks() {
        try {
            TaskManager taskManager = TaskManager.getManager(project);
            List<Task> allTasks = taskManager.getIssues("", 0, 100, false, new EmptyProgressIndicator(), false);
            return allTasks.stream().map(GFTask::new).toList();
        } catch (Exception ex) {
            ExceptionUtil.handleException(project, ex);
        }
        return new ArrayList<>();
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
        if (SELECTED_TASK.is(dataId)) {
            return taskList.getSelectedValue();
        }
        return null;
    }

    private static class JBHtmlEditorPane extends JEditorPane implements ComponentWithEmptyText {
        private final StatusText emptyText = new StatusText(this) {
            @Override
            protected boolean isStatusVisible() {
                return getDocument().getLength() == 0;
            }
        };

        public JBHtmlEditorPane() {
            super("text/html", "");
        }

        @Override
        public @NotNull StatusText getEmptyText() {
            return emptyText;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            emptyText.paint(this, g);
        }
    }
}
