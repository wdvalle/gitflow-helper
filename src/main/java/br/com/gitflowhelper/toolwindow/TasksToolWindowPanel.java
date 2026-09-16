package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflowhelper.events.GitFlowTaskListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.tasks.TasksBridge;
import br.com.gitflowhelper.util.PluginUtils;
import br.com.gitflowhelper.util.TaskFormatter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//import br.com.gitflowhelper.util.TaskProjectFilter;

public class TasksToolWindowPanel extends JPanel implements DataProvider, Disposable {
    public static final DataKey<GFTask> SELECTED_TASK = DataKey.create("SELECTED_TASK");
    private final Project project;
    private final JBList<GFTask> taskList;
    private final SearchTextField searchField;
    private final JBHtmlEditorPane taskDescriptionPane;
    private final TaskFormatter taskFormatter;
    private final List<GFTask> allTasks = new ArrayList<>();
    private boolean loading;

    public TasksToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.taskFormatter = new TaskFormatter(project);

        taskList = new JBList<>(new CollectionListModel<>());
        updateEmptyText();

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

        searchField = new SearchTextField();
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                filterTasks();
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

        JPanel listPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.setBorder(JBUI.Borders.empty(2));
        listPanel.add(searchPanel, BorderLayout.NORTH);
        listPanel.add(new JBScrollPane(taskList), BorderLayout.CENTER);

        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.3f);
        splitter.setFirstComponent(listPanel);
        splitter.setSecondComponent(new JBScrollPane(taskDescriptionPane));

        add(splitter, BorderLayout.CENTER);

        project.getMessageBus().connect(this).subscribe(GitFlowTaskListener.TOPIC, this::loadTasksAsync);

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
                boolean integrate = GitFlowSettingsService.getInstance(project).isIntegrateWithTasks();
                e.getPresentation().setEnabled(!loading && integrate);
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

    private Runnable onNewContent;

    /** Registers a callback invoked whenever task content is loaded/updated. */
    public void setOnNewContent(Runnable onNewContent) {
        this.onNewContent = onNewContent;
    }

    public void loadTasksAsync() {
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            allTasks.clear();
            filterTasks();
            return;
        }
        loading = true;
        updateEmptyText();
        ActivityTracker.getInstance().inc();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<GFTask> tasks = getTasks();
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    if (!project.isDisposed()) {
                        allTasks.clear();
                        allTasks.addAll(tasks);
                        filterTasks();
                        if (onNewContent != null) {
                            onNewContent.run();
                        }
                    }
                } finally {
                    loading = false;
                    updateEmptyText();
                    ActivityTracker.getInstance().inc();
                }
            });
        });
    }

    private void filterTasks() {
        String query = searchField.getText().toLowerCase();
        List<GFTask> filtered = allTasks.stream()
                .filter(task -> task.getPresentableId().toLowerCase().contains(query) ||
                                task.getSummary().toLowerCase().contains(query))
                .sorted(Comparator.comparing(GFTask::getPresentableId, String.CASE_INSENSITIVE_ORDER))
                .toList();
        taskList.setModel(new CollectionListModel<>(filtered));
        updateEmptyText();
    }

    private void updateEmptyText() {
        StatusText emptyText = taskList.getEmptyText();
        emptyText.clear();

        if (loading) {
            emptyText.setText("Loading tasks...");
            return;
        }

        if (PluginUtils.isTasksPluginMissing()) {
            emptyText.setText("Task Management plugin is not installed or disabled");
            emptyText.appendLine("Open ");
            emptyText.appendText("Plugins settings", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Plugins");
            });
            emptyText.appendLine(" to install it.");
            return;
        }

        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            emptyText.setText("Task integration is disabled");
            emptyText.appendLine("Enable it in ");
            emptyText.appendText("Git Flow Helper settings", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
                AnAction action = ActionManager.getInstance().getAction("GitFlowHelper.IntegrateWithTasksAction");
                if (action != null) {
                    DataContext dataContext = DataManager.getInstance().getDataContext(this);
                    AnActionEvent event = AnActionEvent.createFromAnAction(
                            action,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            dataContext
                    );
                    ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                }
            });
            return;
        }

        TasksBridge bridge = TasksBridge.getInstance();
        if (bridge == null || !bridge.hasConfiguredServers(project)) {
            emptyText.setText("No task servers configured");
            emptyText.appendLine("Go to ");
            emptyText.appendText("Settings -> Tools -> Tasks -> Servers", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Tasks");
                loadTasksAsync();
            });
            emptyText.appendLine(" to configure access to a task server.");
            return;
        }

        String query = searchField.getText().trim();
        if (!query.isEmpty()) {
            emptyText.setText("No tasks match '" + query + "'");
            emptyText.appendLine("Clear the search field to see all tasks.");
            return;
        }

        emptyText.setText("No tasks found");
        emptyText.appendLine("Check your task server queries or click Reload.");
    }

    private List<GFTask> getTasks() {
        TasksBridge bridge = TasksBridge.getInstance();
        if (bridge == null) return new ArrayList<>();
        // strip the leading null entry that getAvailableTasks adds for combo boxes
        return bridge.getAvailableTasks(project).stream()
                .filter(t -> t != null)
                .toList();
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
        if (SELECTED_TASK.is(dataId)) {
            return taskList.getSelectedValue();
        }
        return null;
    }

    @Override
    public void dispose() {
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
