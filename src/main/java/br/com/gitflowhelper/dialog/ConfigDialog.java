package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

/**
 * Configuration dialog for GitFlow Helper CI/CD settings.
 *
 * <p>A combo at the top lists every Git repository found in the current project,
 * plus a default empty selection. Repositories with active CI/CD URLs are displayed
 * in green font.
 *
 * <p>Selecting a repository loads its {@link CiServerConfig}; clicking "Delete"
 * clears its server settings. Clicking OK or Apply persists non-empty configs
 * and removes any cleared/empty server configurations.
 */
public class ConfigDialog extends DialogWrapper {

    private final Project project;

    // -----------------------------------------------------------------------
    // Repository selector & Delete button
    // -----------------------------------------------------------------------
    private final ComboBox<String> repoCombo    = new ComboBox<>();
    private final JButton          deleteButton = new JButton(AllIcons.Actions.GC);

    // -----------------------------------------------------------------------
    // CI/CD server fields
    // -----------------------------------------------------------------------
    private final ComboBox<String> ciTypeComboBox =
            new ComboBox<>(new String[]{"Jenkins", "GitLab (soon)", "GitHub (soon)"});
    private final JBTextField     ciUrlField     = new JBTextField();
    private final JBPasswordField ciTokenField   = new JBPasswordField();
    private final JBTextField     ciLoginField   = new JBTextField();

    // -----------------------------------------------------------------------
    // Internal state & Actions
    // -----------------------------------------------------------------------
    private List<GitRepository> repositories;
    /** Working copy per repository index; flushed to the service only on OK/Apply. */
    private CiServerConfig[]    workingConfigs;
    /** Working tokens parallel to workingConfigs; flushed to PasswordSafe only on OK/Apply. */
    private String[]            workingTokens;
    private int                 currentIndex   = -1;
    private boolean             updatingCombo  = false;
    private boolean             updatingFields = false;

    private Action applyAction;

    private final JPanel             panel = new JPanel(new GridBagLayout());
    private final GridBagConstraints gbc   = new GridBagConstraints();
    private int row = 0;

    public ConfigDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Git Flow Helper – CI/CD Configuration");
        init();

        deleteButton.setToolTipText("Delete configuration");

        ciUrlField.getEmptyText().setText("e.g. https://jenkins.example.com/job/mypipeline");
        ciLoginField.getEmptyText().setText("e.g. username");
        ciTokenField.getEmptyText().setText("API token or password");

        if (project != null) {
            loadRepositories();
            populateCombo();
        }

        repoCombo.addActionListener(e -> {
            if (!updatingCombo) {
                int selectedIdx = repoCombo.getSelectedIndex();
                // Item 0 is default empty option ("-- Select Repository --")
                int newRepoIdx = selectedIdx - 1;
                if (newRepoIdx != currentIndex) {
                    saveCurrentFields();
                    currentIndex = newRepoIdx;
                    loadCurrentFields();
                }
            }
        });

        deleteButton.addActionListener(e -> {
            if (currentIndex >= 0 && workingConfigs != null && currentIndex < workingConfigs.length) {
                workingConfigs[currentIndex] = new CiServerConfig();
                workingTokens[currentIndex]  = "";
                loadCurrentFields();
                updateComboItem(currentIndex);
                setModified(true);
            }
        });

        ciTypeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null && value.toString().contains("(soon)")) {
                    c.setEnabled(false);
                    if (isSelected && index >= 0) {
                        c.setBackground(list.getBackground());
                        c.setForeground(UIManager.getColor("Label.disabledForeground"));
                    }
                } else {
                    c.setEnabled(true);
                }
                return c;
            }
        });

        ciTypeComboBox.addActionListener(e -> {
            String selected = (String) ciTypeComboBox.getSelectedItem();
            if (selected != null && selected.contains("(soon)")) {
                ciTypeComboBox.setSelectedItem("Jenkins");
                return;
            }
            updateEnabledState();
            if (!updatingFields) {
                setModified(true);
            }
        });

        DocumentListener changeListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onFieldChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onFieldChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onFieldChanged(); }

            private void onFieldChanged() {
                if (!updatingFields && !updatingCombo) {
                    setModified(true);
                    if (currentIndex >= 0 && workingConfigs != null && currentIndex < workingConfigs.length) {
                        workingConfigs[currentIndex].setCiUrl(ciUrlField.getText().trim());
                        updateComboItem(currentIndex);
                    }
                }
            }
        };

        ciUrlField.getDocument().addDocumentListener(changeListener);
        ciLoginField.getDocument().addDocumentListener(changeListener);
        ciTokenField.getDocument().addDocumentListener(changeListener);
    }

    private void setModified(boolean modified) {
        if (applyAction != null) {
            applyAction.setEnabled(modified);
        }
    }

    // -----------------------------------------------------------------------
    // Initialisation & Combo management
    // -----------------------------------------------------------------------

    private void loadRepositories() {
        repositories   = GitRepositoryManager.getInstance(project).getRepositories();
        workingConfigs = new CiServerConfig[repositories.size()];
        workingTokens  = new String[repositories.size()];

        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        for (int i = 0; i < repositories.size(); i++) {
            GitRepository repo = repositories.get(i);
            String path = repo.getRoot().getPath();
            String name = repo.getRoot().getName();
            workingConfigs[i] = svc.getCiServerForRepo(path, name).copy();
            workingTokens[i]  = null; // null indicates token is currently loading
        }

        // Fetch tokens asynchronously off the EDT to avoid SlowOperations exception
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (int i = 0; i < repositories.size(); i++) {
                String path = repositories.get(i).getRoot().getPath();
                String token = svc.getTokenForRepo(path);
                final int index = i;
                final String loadedToken = token != null ? token : "";
                //com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    if (workingTokens != null && index < workingTokens.length) {
                        workingTokens[index] = loadedToken;
                        if (index == currentIndex) {
                            updatingFields = true;
                            try {
                                ciTokenField.setText(loadedToken);
                            } finally {
                                updatingFields = false;
                            }
                        }
                    }
                //});
            }
        });
    }

    private String getComboItemText(int repoIndex) {
        GitRepository repo = repositories.get(repoIndex);
        String repoName = repo.getRoot().getName();
        String branchPart = repo.getCurrentBranch() != null
                ? "\u2387 " + repo.getCurrentBranch().getName()
                : "(No current branch)";

        boolean isConfigured = workingConfigs != null
                && repoIndex < workingConfigs.length
                && workingConfigs[repoIndex] != null
                && workingConfigs[repoIndex].isActive();

        if (isConfigured) {
            return "<html><font color='#388E3C'><b>" + repoName + "</b></font>&nbsp;&nbsp;&nbsp;<font color='#888888'>"
                    + branchPart + "</font></html>";
        } else {
            return "<html>" + repoName + "&nbsp;&nbsp;&nbsp;<font color='#888888'>"
                    + branchPart + "</font></html>";
        }
    }

    private void populateCombo() {
        updatingCombo = true;
        repoCombo.removeAllItems();

        // Default empty option
        repoCombo.addItem("-- Select Repository --");

        if (repositories != null) {
            for (int i = 0; i < repositories.size(); i++) {
                repoCombo.addItem(getComboItemText(i));
            }
        }
        updatingCombo = false;

        // Default selection: empty option
        repoCombo.setSelectedIndex(0);
        currentIndex = -1;
        loadCurrentFields();
    }

    private void updateComboItem(int repoIndex) {
        if (repoIndex >= 0 && repositories != null && repoIndex < repositories.size()) {
            updatingCombo = true;
            int comboIndex = repoIndex + 1; // +1 for the default empty item
            if (comboIndex < repoCombo.getItemCount()) {
                repoCombo.removeItemAt(comboIndex);
                repoCombo.insertItemAt(getComboItemText(repoIndex), comboIndex);
                repoCombo.setSelectedIndex(comboIndex);
            }
            updatingCombo = false;
        }
    }

    // -----------------------------------------------------------------------
    // Field ↔ workingConfigs sync
    // -----------------------------------------------------------------------

    private void saveCurrentFields() {
        if (currentIndex >= 0 && workingConfigs != null && currentIndex < workingConfigs.length) {
            CiServerConfig cfg = workingConfigs[currentIndex];
            cfg.setCiType((String) ciTypeComboBox.getSelectedItem());
            cfg.setCiUrl(ciUrlField.getText().trim());
            cfg.setCiLogin(ciLoginField.getText().trim());
            if (workingTokens[currentIndex] != null) {
                workingTokens[currentIndex] = new String(ciTokenField.getPassword()).trim();
            }
        }
    }

    private void loadCurrentFields() {
        updatingFields = true;
        try {
            if (currentIndex >= 0 && workingConfigs != null && currentIndex < workingConfigs.length) {
                CiServerConfig cfg = workingConfigs[currentIndex];
                ciTypeComboBox.setSelectedItem(cfg.getCiType());
                ciUrlField.setText(cfg.getCiUrl());
                ciLoginField.setText(cfg.getCiLogin());
                ciTokenField.setText(workingTokens[currentIndex] != null ? workingTokens[currentIndex] : "");
            } else {
                // Default empty option selected → clear fields and disable controls
                ciTypeComboBox.setSelectedItem("Jenkins");
                ciUrlField.setText("");
                ciLoginField.setText("");
                ciTokenField.setText("");
            }
            updateEnabledState();
        } finally {
            updatingFields = false;
        }
    }

    private void updateEnabledState() {
        boolean hasSelectedRepo = currentIndex >= 0;
        boolean isJenkins       = hasSelectedRepo && "Jenkins".equals(ciTypeComboBox.getSelectedItem());

        ciTypeComboBox.setEnabled(hasSelectedRepo);
        ciUrlField.setEnabled(hasSelectedRepo);
        ciTokenField.setEnabled(hasSelectedRepo);
        ciLoginField.setEnabled(isJenkins);
        deleteButton.setEnabled(hasSelectedRepo);
    }

    // -----------------------------------------------------------------------
    // DialogWrapper
    // -----------------------------------------------------------------------

    @Override
    protected JComponent createCenterPanel() {
        gbc.insets  = new Insets(4, 4, 4, 4);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        panel.setPreferredSize(new Dimension(640, 230));

        // ---- Repository selector & Delete button row ----
        JPanel repoRowPanel = new JPanel(new BorderLayout(5, 0));
        repoRowPanel.add(repoCombo, BorderLayout.CENTER);
        repoRowPanel.add(deleteButton, BorderLayout.EAST);
        addRow("Repository:", repoRowPanel);

        // ---- Separator ----
        gbc.gridy     = row++;
        gbc.gridx     = 0;
        gbc.gridwidth = 2;
        gbc.weightx   = 1;
        panel.add(new JSeparator(), gbc);

        // ---- CI/CD fields ----
        gbc.gridwidth = 1;
        addRow("CI/CD Platform:", ciTypeComboBox);
        addRow("URL:", ciUrlField);
        addRow("Login:", ciLoginField);
        addRow("Token:", ciTokenField);

        // ---- Hint ----
        gbc.gridy     = row++;
        gbc.gridx     = 0;
        gbc.gridwidth = 2;
        gbc.weightx   = 1;
        JLabel hint = new JLabel("<html><i>Leave URL empty or click Delete to disable CI/CD for a repository.</i></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(hint, gbc);

        return panel;
    }

    private void addRow(String labelText, JComponent field) {
        gbc.gridy     = row++;
        gbc.gridx     = 0;
        gbc.gridwidth = 1;
        gbc.weightx   = 0;
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx     = 1;
        gbc.gridwidth = 1;
        gbc.weightx   = 1;
        panel.add(field, gbc);
    }

    private void applyChanges() {
        saveCurrentFields();

        if (project != null && repositories != null) {
            GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
            final CiServerConfig[] configsToSave = workingConfigs.clone();
            final String[] tokensToSave = workingTokens.clone();
            final java.util.List<GitRepository> reposToSave = new java.util.ArrayList<>(repositories);

            for (int i = 0; i < reposToSave.size(); i++) {
                GitRepository repo = reposToSave.get(i);
                String path = repo.getRoot().getPath();
                if (configsToSave[i] != null && configsToSave[i].isActive()) {
                    svc.setCiServerForRepo(path, repo.getRoot().getName(), configsToSave[i]);
                } else {
                    // URL is empty or cleared via Delete -> remove CI/CD server data completely
                    svc.removeCiServerForRepo(path);
                }
            }

            // Save tokens to PasswordSafe asynchronously off the EDT
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
                for (int i = 0; i < reposToSave.size(); i++) {
                    GitRepository repo = reposToSave.get(i);
                    String path = repo.getRoot().getPath();
                    if (configsToSave[i] != null && configsToSave[i].isActive()) {
                        String t = tokensToSave[i];
                        svc.saveTokenForRepo(path, t != null ? t : "");
                    }
                }
            });
        }

        setModified(false);
    }

    @Override
    protected void doOKAction() {
        applyChanges();
        super.doOKAction();
    }

    @Override
    protected Action[] createActions() {
        Action[] defaultActions = super.createActions();
        applyAction = new AbstractAction("Apply") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyChanges();
            }
        };
        applyAction.setEnabled(false);

        Action[] actions = new Action[defaultActions.length + 1];
        System.arraycopy(defaultActions, 0, actions, 0, defaultActions.length);
        actions[defaultActions.length] = applyAction;
        return actions;
    }
}