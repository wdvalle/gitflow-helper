package br.com.gitflowhelper.dialog;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.PluginUtils;
import br.com.gitflowhelper.util.TaskFormatter;
import br.com.gitflowhelper.util.TaskProjectFilter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ShowTasksDialog extends DialogWrapper {

    private final Project project;
    private ComboBox<GFTask> taskComboBox;
    private JEditorPane taskDescriptionPane;
    private JBScrollPane taskDetailScrollPane;
    private JBCheckBox showAllIssuesCheckBox;
    private final TaskFormatter taskFormatter;
    private final TaskProjectFilter taskProjectFilter;
    private boolean isComboLoading = false;
    private Timer loadingTimer;
    private int loadingDots = 0;

    public ShowTasksDialog(Project project) {
        super(project);
        this.project = project;
        this.taskFormatter = new TaskFormatter(project);
        this.taskProjectFilter = new TaskProjectFilter(project);
        setTitle("Show project tasks");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int currentGridY = 0;

        // Show all issues checkbox
        showAllIssuesCheckBox = new JBCheckBox("Show all issues (uncheck to show only project issues)");
        showAllIssuesCheckBox.addActionListener(e -> loadTasksAsync());
        gbc.gridx = 0;
        gbc.gridy = currentGridY;
        gbc.gridwidth = 2;
        panel.add(showAllIssuesCheckBox, gbc);
        currentGridY++;

        // Task selector row
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = currentGridY;
        gbc.weightx = 0;
        panel.add(new JLabel("Task:"), gbc);

        taskComboBox = new ComboBox<>();
        taskComboBox.setModel(new CollectionComboBoxModel<>(new ArrayList<>()));
        taskComboBox.setSelectedItem(null);
        initTaskComboBox();

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(taskComboBox, gbc);
        currentGridY++;

        // Task detail row
        taskDescriptionPane = new JEditorPane("text/html", "");
        taskDescriptionPane.setEditable(false);
        taskDescriptionPane.setBackground(panel.getBackground());
        taskDescriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        taskDescriptionPane.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                BrowserUtil.browse(e.getURL());
            }
        });

        taskDetailScrollPane = new JBScrollPane(taskDescriptionPane);
        taskDetailScrollPane.setPreferredSize(new Dimension(600, 300));

        gbc.gridx = 0;
        gbc.gridy = currentGridY;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(taskDetailScrollPane, gbc);

        loadTasksAsync();

        return panel;
    }

    private void initTaskComboBox() {
        taskComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GFTask task) {
                    setText(task.getPresentableId() + ": " + task.getSummary());
                    setIcon(task.getIcon());
                } else {
                    if (isComboLoading) {
                        setText("Loading tasks" + ".".repeat(loadingDots));
                        setIcon(AllIcons.Actions.BuildLoadChanges);
                    } else {
                        setText("Select a task...");
                        setIcon(null);
                    }
                }
                return this;
            }
        });

        taskComboBox.addActionListener(e -> {
            try {
                GFTask selectedTask = (GFTask) taskComboBox.getSelectedItem();
                if (selectedTask != null) {
                    taskDescriptionPane.setText(taskFormatter.formatTaskDetail(selectedTask));
                    taskDescriptionPane.setCaretPosition(0);
                } else {
                    taskDescriptionPane.setText("");
                }
            } catch (Exception ex) {
                PluginUtils.logError(project, "Error loading task details: " + PluginUtils.getStackTrace(ex));
            }
        });
    }

    private void loadTasksAsync() {
        isComboLoading = true;
        taskComboBox.setModel(new CollectionComboBoxModel<>(new ArrayList<>()));
        taskComboBox.setSelectedItem(null);

        if (loadingTimer == null) {
            loadingTimer = new Timer(250, e -> {
                loadingDots = (loadingDots + 1) % 4;
                if (taskComboBox != null) {
                    taskComboBox.repaint();
                }
            });
            loadingTimer.start();
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<GFTask> tasks = getTaskModel();
            if (tasks != null && !project.isDisposed() && taskComboBox != null) {
                taskComboBox.setModel(new CollectionComboBoxModel<>(tasks));
                taskComboBox.setSelectedItem(null);
                taskComboBox.revalidate();
                isComboLoading = false;
                taskComboBox.repaint();
            }
        });
    }

    private List<GFTask> getTaskModel() {
        return ApplicationManager.getApplication().runReadAction((Computable<List<GFTask>>) () -> {
            try {
                TaskManager taskManager = TaskManager.getManager(project);
                List<Task> allTasks = new ArrayList<>(taskManager.getIssues("", 0, 100, false, new EmptyProgressIndicator(), false));

                boolean showAll = showAllIssuesCheckBox.isSelected();
                List<String> projectPaths = taskProjectFilter.getProjectPaths();
                List<GFTask> tasks = new ArrayList<>();
                tasks.add(null);

                if (showAll || projectPaths.isEmpty()) {
                    tasks.addAll(allTasks.stream()
                            .map(GFTask::new)
                            .toList());
                } else {
                    tasks.addAll(allTasks.stream()
                            .filter(task -> taskProjectFilter.isTaskFromProject(task, projectPaths))
                            .map(GFTask::new)
                            .toList());
                }
                return tasks;
            } catch (Exception ex) {
                ExceptionUtil.handleException(project, ex);
            }
            return null;
        });
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected void dispose() {
        if (loadingTimer != null) {
            loadingTimer.stop();
        }
        super.dispose();
    }
}
