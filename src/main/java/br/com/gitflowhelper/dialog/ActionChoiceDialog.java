package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ActionChoiceDialog extends DialogWrapper {

    private ComboBox<String> actionComboBox;
    private JBCheckBox keepLocalBranch;
    private JBCheckBox keepRemoteBranch;
    private JBCheckBox squashCommit;
    private JBCheckBox closeAssociatedTask;
    private JBTextArea commitMessage;
    private String branchName;
    private String targetBranch;
    private String log;
    private Project project;
    private java.util.List<String> warnings;
    private boolean isBehind = false;
    
    public static final String INTEGRATE = "Integrate immediately";
    public static final String AUTO_CREATE = "Create merge request (Gitlab only)";
    public static final String SELF_CREATE = "I will create a merge/pull request";

    public ActionChoiceDialog(@Nullable Project project, String branchName, String targetBranch,
                              String log, java.util.List<String> warnings) {
        this(project, branchName, targetBranch, log, warnings, false);
    }

    public ActionChoiceDialog(@Nullable Project project, String branchName, String targetBranch,
                              String log, java.util.List<String> warnings, boolean isBehind) {
        super(project); // true = modal
        this.project = project;
        this.branchName = branchName;
        this.targetBranch = targetBranch;
        this.log = log;
        this.warnings = warnings;
        this.isBehind = isBehind;
        setTitle("Finish feature");
        setOKButtonText("Yes");
        setCancelButtonText("No");
        init();
        if (isBehind) {
            setOKActionEnabled(false);
        }
        int dialogHeight = (warnings != null && !warnings.isEmpty()) ? 620 : 540;
        setSize(500, dialogHeight);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // Inicializa a Combo com as opções
        actionComboBox = new ComboBox<>(
                new String[]{
                        INTEGRATE,
                        AUTO_CREATE,
                        SELF_CREATE});
        actionComboBox.setSize(350, 40);
        keepLocalBranch = new JBCheckBox("Keep local branch when finished");
        keepRemoteBranch = new JBCheckBox("Keep remote branch when finished");
        squashCommit = new JBCheckBox("Squash commits (one commit message only)");
        closeAssociatedTask = new JBCheckBox("Close associated task");
        closeAssociatedTask.setSelected(true);
        commitMessage = new JBTextArea();
        commitMessage.setRows(10);
        commitMessage.setLineWrap(true);
        commitMessage.setWrapStyleWord(true);
        String defaultMsg = String.format("Merge branch '%s' into %s", branchName, targetBranch);
        commitMessage.setText(defaultMsg);

        squashCommit.addActionListener(e -> {
            if (squashCommit.isSelected()) {
                commitMessage.setText(commitMessage.getText()+"\n\n"+log);
            } else {
                commitMessage.setText(defaultMsg);
            }
        });
        actionComboBox.addActionListener(e -> {
            if (!actionComboBox.getSelectedItem().equals(ActionChoiceDialog.INTEGRATE)) {
                keepRemoteBranch.setSelected(true);
                keepRemoteBranch.setEnabled(false);
            } else {
                keepRemoteBranch.setEnabled(true);
            }
        });

        JBLabel commitLabel = new JBLabel("Commit message:");
        commitLabel.setBorder(JBUI.Borders.emptyBottom(4));

        JBScrollPane commitScroll = new JBScrollPane(commitMessage);
        commitScroll.setPreferredSize(new Dimension(460, 160));

        JBLabel branchLabel = new JBLabel("Finishing feature: "+branchName);
        Font font = branchLabel.getFont();
        Font newFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
        branchLabel.setFont(newFont);

        JBLabel explanationLabel = new JBLabel("Select the option above that defines the approval workflow.");
        explanationLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        explanationLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
        explanationLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

        // Creates layout using FormBuilder (IntelliJ pattern for alignment)
        FormBuilder builder = FormBuilder.createFormBuilder();

        if (warnings != null && !warnings.isEmpty()) {
            // Material Design amber-50 background with amber-200 border
            Color warningBg = new JBColor(new Color(0xFF, 0xF8, 0xE1), new Color(0x4A, 0x3F, 0x1B));
            Color warningBorder = new JBColor(new Color(0xFF, 0xE0, 0x82), new Color(0x7A, 0x6A, 0x2E));
            Color warningText = new JBColor(new Color(0xE6, 0x5C, 0x00), new Color(0xFF, 0xB7, 0x4D));

            JPanel warningPanel = new JPanel();
            warningPanel.setLayout(new BoxLayout(warningPanel, BoxLayout.Y_AXIS));
            warningPanel.setBackground(warningBg);
            warningPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(warningBorder, 1, true),
                    JBUI.Borders.empty(10, 12)
            ));

            JBLabel warningTitle = new JBLabel("⚠️ Warnings:");
            warningTitle.setForeground(warningText);
            warningTitle.setFont(warningTitle.getFont().deriveFont(Font.BOLD));
            warningTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            warningPanel.add(warningTitle);
            warningPanel.add(Box.createVerticalStrut(4));

            for (String warning : warnings) {
                JBLabel wLabel = new JBLabel("• " + warning);
                wLabel.setForeground(warningText);
                wLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
                wLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                warningPanel.add(wLabel);
            }

            builder.addComponent(warningPanel);
            builder.addVerticalGap(10);
        }

        builder.addComponent(branchLabel)
                .addLabeledComponent("What to do when finished:", actionComboBox)
                .addComponent(explanationLabel)
                .addVerticalGap(10)
                .addComponent(keepLocalBranch)
                .addComponent(keepRemoteBranch)
                .addComponent(squashCommit);

        if (project != null && GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            builder.addComponent(closeAssociatedTask);
        }

        return builder.addVerticalGap(10)
                .addComponent(commitLabel)
                .addComponent(commitScroll)
                .getPanel();
    }

    public String getSelectedAction() {
        return (String) actionComboBox.getSelectedItem();
    }
    public Boolean getKeepLocalBranch() { return keepLocalBranch.isSelected(); }
    public Boolean getKeepRemoteBranch() { return keepRemoteBranch.isSelected(); }
    public Boolean getSquashCommit() { return squashCommit.isSelected(); }
    public Boolean getCloseAssociatedTask() {
        return closeAssociatedTask.isSelected() && (project == null || GitFlowSettingsService.getInstance(project).isIntegrateWithTasks());
    }
    public String getCommitMessage() { return commitMessage.getText(); }
    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }
    public void setWarnings(java.util.List<String> warnings) { this.warnings = warnings; }
}
