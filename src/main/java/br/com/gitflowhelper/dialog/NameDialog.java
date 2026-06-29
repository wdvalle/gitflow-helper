package br.com.gitflowhelper.dialog;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflow.tracker.IssueResponse;
import br.com.gitflow.tracker.IssueTrackerConnector;
import br.com.gitflow.tracker.TrackerFactory;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.BranchNameRefiner;
import br.com.gitflowhelper.util.ExceptionUtil;
import br.com.gitflowhelper.util.PluginUtils;
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
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class NameDialog extends DialogWrapper {

    private final JTextField usernameField = new JTextField(40);
    private final JTextField nameField = new JTextField(40);
    private ComboBox<GFTask> taskComboBox;
    private JEditorPane taskDescriptionPane;
    private JBScrollPane taskDetailScrollPane;
    private final JBCheckBox pushOnFinish;
    private final JBCheckBox activateTaskCheckBox;
    private final Consumer<NameResponse> onOk;
    private final String label;
    private final boolean showPush;
    private final Project project;
    private boolean isComboLoading = false;
    private Optional<IssueTrackerConnector> trackerConnector = Optional.empty();

    public NameDialog(Project project, String titleText, String label, boolean showPush, Consumer<NameResponse> onOk) {
        super(project);
        this.project = project;
        this.onOk = onOk;
        setTitle(titleText);
        this.label = label;
        this.showPush = showPush;
        this.usernameField.setText(GitFlowSettingsService.getInstance(project).getState().getPreferredUsername());
        this.pushOnFinish = new JBCheckBox("Push local branch when finished");
        this.activateTaskCheckBox = new JBCheckBox("Set task as active/started");
        this.activateTaskCheckBox.setSelected(true);
        this.isComboLoading = true;
        ((AbstractDocument) nameField.getDocument()).setDocumentFilter( new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                if (text != null) {
                    text = text.replace(" ", "_");
                }
                super.insertString(fb, offset, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text != null) {
                    text = text.replace(" ", "_");
                }
                super.replace(fb, offset, length, text, attrs);
            }
        });

        initTasks();
        init();
    }

    private void initTasks() {
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            taskComboBox = new ComboBox<>(new CollectionComboBoxModel<>(new ArrayList<>()));
            return;
        }

        taskComboBox = new ComboBox<>();
        taskComboBox.setModel(new CollectionComboBoxModel<>(new ArrayList<>()));
        taskComboBox.setSelectedItem(null);

        loadTasksAsync();

        taskComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GFTask task) {
                    setText(task.getPresentableId() + ": " + task.getSummary());
                    setIcon(task.getIcon());
                } else {
                    setText(isComboLoading ? "Loading tasks..." : "Select a task...");
                    setIcon(isComboLoading ? AllIcons.Actions.BuildLoadChanges : null);
                }
                return this;
            }
        });

        taskComboBox.addActionListener(e -> {
            try {
                GFTask selectedTask = (GFTask) taskComboBox.getSelectedItem();
                if (selectedTask != null) {
                    nameField.setText(BranchNameRefiner.slugify(selectedTask));
                    updateTaskDetailPanel(selectedTask);
                } else {
                    if (taskDetailScrollPane != null) {
                        taskDetailScrollPane.setVisible(false);
                    }
                }
            } catch (Exception ex) {
                PluginUtils.logError(project, "Error loading task details: " +  PluginUtils.getStackTrace(ex));
            }
        });
    }

    private void loadTasksAsync() {
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

                List<String> projectPaths = getProjectPaths();
                List<GFTask> tasks = new ArrayList<>();
                tasks.add(null);

                if (projectPaths.isEmpty()) {
                    tasks.addAll(allTasks.stream()
                            .map(GFTask::new)
                            .toList());
                } else {
                    tasks.addAll(allTasks.stream()
                            .filter(task -> isTaskFromProject(task, projectPaths))
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

    private List<String> getProjectPaths() {
        List<String> paths = new ArrayList<>();
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        for (GitRepository repository : manager.getRepositories()) {
            for (GitRemote remote : repository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    String path = extractProjectPath(url);
                    if (path != null) {
                        paths.add(path);
                    }
                }
            }
        }
        return paths;
    }

    private String extractProjectPath(String url) {
        if (url == null) return null;
        String path = url;
        // Remove protocol
        if (path.contains("://")) {
            path = path.substring(path.indexOf("://") + 3);
        } else if (path.contains("@")) {
            path = path.substring(path.indexOf("@") + 1);
        }

        // Remove host
        if (path.contains("/")) {
            path = path.substring(path.indexOf("/") + 1);
        } else if (path.contains(":")) {
            path = path.substring(path.indexOf(":") + 1);
        }

        // Remove .git at the end
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }

        return path.toLowerCase();
    }

    private void updateTaskDetailPanel(GFTask task) {
        if (taskDetailScrollPane == null) return;

        // Adds padding/margin to the body tag so the text does not stick to the panel borders
        StringBuilder sb = new StringBuilder("<html><body style='font-family: sans-serif; font-size: 11pt; margin: 12px;'>");

        // Task header (ID + Summary) highlighted
        sb.append("<h2 style='margin-top: 0; margin-bottom: 8px; font-weight: normal;'>");
        sb.append("<b>").append(task.getPresentableId()).append("</b> &mdash; ");
        sb.append(task.getSummary());
        sb.append("</h2>");

        // Subtle separator line
        sb.append("<hr style='border: 0; border-top: 1px solid #888888; margin-bottom: 12px;'>");

        // Uses a table to perfectly align labels and values
        sb.append("<table border='0' cellpadding='4' cellspacing='0'>");

        // Inserts custom data (Assignee, State, Description, etc.)
        appendMyIssueData(sb, task);

        sb.append("</table>");
        sb.append("</body></html>");

        taskDescriptionPane.setText(sb.toString());
        taskDescriptionPane.setCaretPosition(0);
        taskDetailScrollPane.setVisible(true);
        pack();
    }

    public void appendMyIssueData(StringBuilder sb, GFTask gfTask) {
        //assumes only one issue tracker per project (why more than one?)
        if (trackerConnector.isEmpty())
            trackerConnector = TrackerFactory.getConnector(project, gfTask.getTask());
        if (gfTask.getAssignees().isEmpty()) {
            trackerConnector.ifPresent(connector -> {
                String issueId = gfTask.getLocalId();
                if (issueId.isEmpty()) issueId = gfTask.getId();
                IssueResponse issue = connector.getIssue(issueId);
                gfTask.setAssignees(issue.getAssigneesAsString());
            });
        }
        appendToTable(sb, "Assignees", gfTask.getAssignees());
        appendToTable(sb, "State", gfTask.getState());
        appendToTable(sb, "Description", gfTask.getDescriptionAsHtml());

        if (gfTask.getIssueUrl() != null) {
            appendLinkToTable(sb, "URL", gfTask.getIssueUrl());
        }
    }

    private void appendLinkToTable(StringBuilder sb, String label, String value) {
        String link = "<a href=\""+value+"\">"+value+"</a>";
        appendToTable(sb, label, link);
    }

    private void appendToTable(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append("<tr>");
            sb.append("<td valign='top' style='color: #888888;'><b>").append(label).append(":</b></td>");
            sb.append("<td valign='top'>").append(value).append("</td>");
            sb.append("</tr>");
        }
    }

    private boolean isTaskFromProject(Task task, List<String> projectPaths) {
        String issueUrl = task.getIssueUrl();
        if (issueUrl == null) return true; // Keep local tasks or tasks without URL

        String lowerUrl = issueUrl.toLowerCase();
        for (String path : projectPaths) {
            if (lowerUrl.contains(path.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int currentGridY = 0;

        // Preferred username field row
        if (GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            gbc.gridx = 0;
            gbc.gridy = currentGridY;
            gbc.weightx = 0;
            panel.add(new JLabel("Prefered userame:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = currentGridY;
            gbc.weightx = 1.0;
            panel.add(usernameField, gbc);
            currentGridY++;

            // Task selector row
            gbc.gridx = 0;
            gbc.gridy = currentGridY;
            gbc.weightx = 0;
            panel.add(new JLabel("Task:"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;

            JPanel taskPanel = new JPanel(new BorderLayout(5, 0));
            taskPanel.add(taskComboBox, BorderLayout.CENTER);

            panel.add(taskPanel, gbc);
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
            taskDetailScrollPane.setPreferredSize(new Dimension(500, 150));
            taskDetailScrollPane.setVisible(false);

            gbc.gridx = 1;
            gbc.gridy = currentGridY;
            gbc.weightx = 1.0;
            gbc.weighty = 0.5;
            gbc.fill = GridBagConstraints.BOTH;
            panel.add(taskDetailScrollPane, gbc);
            currentGridY++;

            gbc.gridx = 1;
            gbc.gridy = currentGridY;
            gbc.weightx = 1.0;
            gbc.weighty = 0.0;
            panel.add(activateTaskCheckBox, gbc);
            currentGridY++;
        }

        // Name field row
        gbc.gridx = 0;
        gbc.gridy = currentGridY;
        gbc.weightx = 0;
        panel.add(new JLabel(label + ":"), gbc);

        gbc.gridx = 1;
        gbc.gridy = currentGridY;
        gbc.weightx = 1.0;
        panel.add(nameField, gbc);
        currentGridY++;

        if (showPush) {
            gbc.gridx = 1;
            gbc.gridy = currentGridY;
            gbc.weightx = 1.0;
            panel.add(pushOnFinish, gbc);
            currentGridY++;
        }

        // Add a vertical filler to push components to the top
        gbc.gridx = 0;
        gbc.gridy = currentGridY;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    @Override
    protected void doOKAction() {
        String name = nameField.getText();
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        String username = usernameField.getText();
        GitFlowSettingsService.getInstance(project).getState().setPreferredUsername(username);

        if (GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            if (taskComboBox == null || taskComboBox.getSelectedItem() == null) {
                return;
            }
        }

        GFTask selectedTask = taskComboBox != null ? (GFTask) taskComboBox.getSelectedItem() : null;
        boolean activate = activateTaskCheckBox != null && activateTaskCheckBox.isSelected();
        onOk.accept(new NameResponse(name, pushOnFinish.isSelected(), selectedTask, activate, username));
        super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}
