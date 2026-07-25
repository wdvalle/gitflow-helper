package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Configuration dialog for GitFlow Helper CI/CD settings.
 *
 * <p>A combo at the top lists every Git repository found in the current project.
 * Selecting a repository loads its {@link CiServerConfig}; the user edits
 * the fields and clicks OK to persist all changes.
 *
 * <p>CI/CD integration is considered active automatically whenever the URL
 * field is non-empty — no checkbox required.
 */
public class ConfigDialog extends DialogWrapper {

    private final Project project;

    // -----------------------------------------------------------------------
    // Repository selector
    // -----------------------------------------------------------------------
    private final ComboBox<String> repoCombo = new ComboBox<>();

    // -----------------------------------------------------------------------
    // CI/CD server fields
    // -----------------------------------------------------------------------
    private final ComboBox<String> ciTypeComboBox =
            new ComboBox<>(new String[]{"Jenkins", "GitLab", "GitHub"});
    private final JTextField ciUrlField   = new JTextField();
    private final JTextField ciTokenField = new JTextField();

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------
    private List<GitRepository> repositories;
    /** Working copy per repository index; flushed to the service only on OK. */
    private CiServerConfig[]    workingConfigs;
    private int                 currentIndex  = -1;
    private boolean             updatingCombo = false;

    private final JPanel             panel = new JPanel(new GridBagLayout());
    private final GridBagConstraints gbc   = new GridBagConstraints();
    private int row = 0;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ConfigDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Git Flow Helper – CI/CD Configuration");
        init();

        if (project != null) {
            loadRepositories();
            populateCombo();
        }

        repoCombo.addActionListener(e -> {
            if (!updatingCombo) {
                int newIdx = repoCombo.getSelectedIndex();
                if (newIdx >= 0 && newIdx != currentIndex) {
                    saveCurrentFields();
                    currentIndex = newIdx;
                    loadCurrentFields();
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    private void loadRepositories() {
        repositories   = GitRepositoryManager.getInstance(project).getRepositories();
        workingConfigs = new CiServerConfig[repositories.size()];

        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        for (int i = 0; i < repositories.size(); i++) {
            GitRepository repo = repositories.get(i);
            String path = repo.getRoot().getPath();
            String name = repo.getRoot().getName();
            // Deep-copy so edits don't mutate live state until OK is clicked
            workingConfigs[i] = svc.getCiServerForRepo(path, name).copy();
        }
    }

    private void populateCombo() {
        updatingCombo = true;
        repoCombo.removeAllItems();

        if (repositories == null || repositories.isEmpty()) {
            repoCombo.addItem("(no Git repositories found)");
        } else {
            for (GitRepository repo : repositories) {
                String branchPart = repo.getCurrentBranch() != null
                        ? "\u2387 " + repo.getCurrentBranch().getName()
                        : "(No current branch)";
                repoCombo.addItem("<html>" + repo.getRoot().getName()
                        + "&nbsp;&nbsp;&nbsp;<font color='#888888'>"
                        + branchPart + "</font></html>");
            }
        }
        updatingCombo = false;

        if (repositories != null && !repositories.isEmpty()) {
            repoCombo.setSelectedIndex(0);
            currentIndex = 0;
            loadCurrentFields();
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
            cfg.setCiToken(ciTokenField.getText().trim());
        }
    }

    private void loadCurrentFields() {
        if (currentIndex >= 0 && workingConfigs != null && currentIndex < workingConfigs.length) {
            CiServerConfig cfg = workingConfigs[currentIndex];
            ciTypeComboBox.setSelectedItem(cfg.getCiType());
            ciUrlField.setText(cfg.getCiUrl());
            ciTokenField.setText(cfg.getCiToken());
        }
    }

    // -----------------------------------------------------------------------
    // DialogWrapper
    // -----------------------------------------------------------------------

    @Override
    protected JComponent createCenterPanel() {
        gbc.insets  = new Insets(4, 4, 4, 4);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        panel.setPreferredSize(new Dimension(460, 200));

        // ---- Repository selector ----
        addRow("Repository:", repoCombo);

        // ---- Separator ----
        gbc.gridy    = row++;
        gbc.gridx    = 0;
        gbc.gridwidth = 2;
        gbc.weightx  = 1;
        panel.add(new JSeparator(), gbc);

        // ---- CI/CD fields ----
        gbc.gridwidth = 1;
        addRow("CI/CD Platform:", ciTypeComboBox);
        addRow("URL:", ciUrlField);
        addRow("Token:", ciTokenField);

        // ---- Hint ----
        gbc.gridy    = row++;
        gbc.gridx    = 0;
        gbc.gridwidth = 2;
        gbc.weightx  = 1;
        JLabel hint = new JLabel("<html><i>Leave URL empty to disable CI/CD for this repository.</i></html>");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(hint, gbc);

        return panel;
    }

    private void addRow(String labelText, JComponent field) {
        gbc.gridy    = row++;
        gbc.gridx    = 0;
        gbc.gridwidth = 1;
        gbc.weightx  = 0;
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx    = 1;
        gbc.gridwidth = 1;
        gbc.weightx  = 1;
        panel.add(field, gbc);
    }

    private void applyChanges() {
        saveCurrentFields();

        if (project != null && repositories != null) {
            GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
            for (int i = 0; i < repositories.size(); i++) {
                GitRepository repo = repositories.get(i);
                svc.setCiServerForRepo(
                        repo.getRoot().getPath(),
                        repo.getRoot().getName(),
                        workingConfigs[i]);
            }
        }
    }

    @Override
    protected void doOKAction() {
        applyChanges();
        super.doOKAction();
    }

    @Override
    protected Action[] createActions() {
        Action[] defaultActions = super.createActions();
        Action applyAction = new AbstractAction("Apply") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyChanges();
            }
        };

        Action[] actions = new Action[defaultActions.length + 1];
        System.arraycopy(defaultActions, 0, actions, 0, defaultActions.length);
        actions[defaultActions.length] = applyAction;
        return actions;
    }
}