package br.com.gitflowhelper.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import git4idea.GitCommit;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class UnpushedCommitsDialog extends DialogWrapper {

    private final String branchName;
    private final List<GitCommit> unpushedCommits;

    public UnpushedCommitsDialog(@Nullable Project project, String branchName, List<GitCommit> unpushedCommits) {
        super(project);
        this.branchName = branchName;
        this.unpushedCommits = unpushedCommits;
        setTitle("Unpushed Commits");
        setOKButtonText("Push commits and continue...");
        setCancelButtonText("Cancel");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
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

        JBLabel warningTitle = new JBLabel("⚠️ Unpushed Commits");
        warningTitle.setForeground(warningText);
        warningTitle.setFont(warningTitle.getFont().deriveFont(Font.BOLD));
        warningTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningPanel.add(warningTitle);
        warningPanel.add(Box.createVerticalStrut(4));

        int count = unpushedCommits != null ? unpushedCommits.size() : 0;
        JBLabel warningDesc = new JBLabel("Branch '" + branchName + "' has " + count + " unpushed commit" + (count > 1 ? "s" : "") + ".");
        warningDesc.setForeground(warningText);
        warningDesc.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        warningDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningPanel.add(warningDesc);

        JBTextArea commitsArea = new JBTextArea(8, 45);
        commitsArea.setEditable(false);
        commitsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        commitsArea.setLineWrap(true);
        commitsArea.setWrapStyleWord(true);

        if (unpushedCommits != null && !unpushedCommits.isEmpty()) {
            String commitsText = unpushedCommits.stream()
                    .map(c -> "• " + c.getId().toShortString() + ": " + c.getSubject())
                    .collect(Collectors.joining("\n"));
            commitsArea.setText(commitsText);
            commitsArea.setCaretPosition(0);
        }

        JBLabel label = new JBLabel("Unpushed commits list:");
        label.setBorder(JBUI.Borders.emptyBottom(4));

        JBScrollPane scrollPane = new JBScrollPane(commitsArea);
        scrollPane.setPreferredSize(new Dimension(460, 140));

        return FormBuilder.createFormBuilder()
                .addComponent(warningPanel)
                .addVerticalGap(10)
                .addComponent(label)
                .addComponent(scrollPane)
                .getPanel();
    }
}
