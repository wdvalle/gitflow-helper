package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ConfigDialog extends DialogWrapper {

    private final Project project;
    private final JBCheckBox integrateWithCICheckBox = new JBCheckBox("Integrate with CI/CD");
    private final ComboBox<String> ciTypeComboBox = new ComboBox<>(new String[]{"Jenkins", "GitLab", "GitHub"});
    private final JTextField ciUrlField = new JTextField();
    private final JTextField ciTokenField = new JTextField();

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final GridBagConstraints gbc = new GridBagConstraints();
    private int row = 0;

    public ConfigDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Git Flow Helper Configuration");
        init();
        loadSettings();
        updateEnabledState();

        integrateWithCICheckBox.addActionListener(e -> updateEnabledState());
    }

    private void loadSettings() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        integrateWithCICheckBox.setSelected(settings.isIntegrateWithCI());
        ciTypeComboBox.setSelectedItem(settings.getCiType());
        ciUrlField.setText(settings.getCiUrl());
        ciTokenField.setText(settings.getCiToken());
    }

    private void updateEnabledState() {
        boolean enabled = integrateWithCICheckBox.isSelected();
        ciTypeComboBox.setEnabled(enabled);
        ciUrlField.setEnabled(enabled);
        ciTokenField.setEnabled(enabled);
    }

    @Override
    protected JComponent createCenterPanel() {
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        panel.setPreferredSize(new Dimension(400, 200));

        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(integrateWithCICheckBox, gbc);

        gbc.gridwidth = 1;
        addRow("CI/CD Platform:", ciTypeComboBox);
        addRow("URL:", ciUrlField);
        addRow("Token:", ciTokenField);

        return panel;
    }

    private void addRow(String labelText, JComponent field) {
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    @Override
    protected void doOKAction() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        settings.setIntegrateWithCI(integrateWithCICheckBox.isSelected());
        settings.setCiType((String) ciTypeComboBox.getSelectedItem());
        settings.setCiUrl(ciUrlField.getText());
        settings.setCiToken(ciTokenField.getText());
        super.doOKAction();
    }
}