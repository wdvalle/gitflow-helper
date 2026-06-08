package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.BranchNameRefiner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NameDialog extends DialogWrapper {

    private final JTextField nameField = new JTextField(40);
    private ComboBox<Task> taskComboBox;
    private JBCheckBox pushOnFinish;
    private JBCheckBox activateTaskCheckBox;
    private final Consumer<NameResponse> onOk;
    private String label;
    private boolean showPush;
    private final Project project;

    public NameDialog(Project project, String titleText, String label, boolean showPush, Consumer<NameResponse> onOk) {
        super(project);
        this.project = project;
        this.onOk = onOk;
        setTitle(titleText);
        this.label = label;
        this.showPush = showPush;
        this.pushOnFinish = new JBCheckBox("Push local branch when finished");
        this.activateTaskCheckBox = new JBCheckBox("Set task as active/started");
        this.activateTaskCheckBox.setSelected(true);
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
        updateTaskModel();

        // Chamada automática para recarregar tasks assim que o diálogo abre
        reloadTasks();

        taskComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Task) {
                    setText(((Task) value).getPresentableId() + ": " + ((Task) value).getSummary());
                } else {
                    setText("Select a task...");
                }
                return this;
            }
        });

        taskComboBox.addActionListener(e -> {
            Task selectedTask = (Task) taskComboBox.getSelectedItem();
            if (selectedTask != null) {
                nameField.setText(BranchNameRefiner.slugify(selectedTask));
            }
        });
    }

    private void updateTaskModel() {
        TaskManager taskManager = TaskManager.getManager(project);
        List<Task> allTasks = new ArrayList<>(taskManager.getIssues(""));

        List<String> projectPaths = getProjectPaths();
        List<Task> tasks;

        if (projectPaths.isEmpty()) {
            tasks = allTasks;
        } else {
            tasks = allTasks.stream()
                    .filter(task -> isTaskFromProject(task, projectPaths))
                    .collect(Collectors.toList());
        }

        taskComboBox.setModel(new CollectionComboBoxModel<>(tasks));
    }

    private void reloadTasks() {
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            return;
        }

        TaskManager taskManager = TaskManager.getManager(project);

        taskManager.updateIssues(() -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                updateTaskModel();
            });
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
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int currentGridY = 0;
        if (GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
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

            gbc.gridx = 1;
            gbc.gridy = currentGridY;
            gbc.weightx = 1.0;
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
        Task selectedTask = taskComboBox != null ? (Task) taskComboBox.getSelectedItem() : null;
        boolean activate = activateTaskCheckBox != null && activateTaskCheckBox.isSelected();
        onOk.accept(new NameResponse(nameField.getText(), pushOnFinish.isSelected(), selectedTask, activate));
        super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}
