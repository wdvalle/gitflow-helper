package br.com.gitflowhelper.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class UncommittedChangesDialog extends DialogWrapper {

    private final JBTextArea commitMessageArea = new JBTextArea(4, 35);

    public UncommittedChangesDialog(@Nullable Project project) {
        super(project);
        setTitle("Uncommitted Changes");
        setOKButtonText("Commit and continue...");
        setCancelButtonText("Cancel");
        init();
        setOKActionEnabled(false);
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

        JBLabel warningTitle = new JBLabel("⚠️ Pending changes");
        warningTitle.setForeground(warningText);
        warningTitle.setFont(warningTitle.getFont().deriveFont(Font.BOLD));
        warningTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningPanel.add(warningTitle);
        warningPanel.add(Box.createVerticalStrut(4));

        JBLabel warningDesc = new JBLabel("There is uncommitted code in your repository. Please provide a commit message to commit changes and continue.");
        warningDesc.setForeground(warningText);
        warningDesc.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        warningDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningPanel.add(warningDesc);

        commitMessageArea.setLineWrap(true);
        commitMessageArea.setWrapStyleWord(true);
        commitMessageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateOkAction();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateOkAction();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateOkAction();
            }

            private void updateOkAction() {
                setOKActionEnabled(StringUtil.isNotEmpty(commitMessageArea.getText().trim()));
            }
        });

        JBLabel label = new JBLabel("Commit message:");
        label.setBorder(JBUI.Borders.emptyBottom(4));

        JBScrollPane scrollPane = new JBScrollPane(commitMessageArea);
        scrollPane.setPreferredSize(new Dimension(420, 100));

        return FormBuilder.createFormBuilder()
                .addComponent(warningPanel)
                .addVerticalGap(10)
                .addComponent(label)
                .addComponent(scrollPane)
                .getPanel();
    }

    public String getCommitMessage() {
        return commitMessageArea.getText().trim();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return commitMessageArea;
    }
}
