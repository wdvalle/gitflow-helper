package br.com.gitflowhelper.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

public class UsageDialog extends DialogWrapper {

    public UsageDialog(@Nullable Project project) {
        super(project);
        setTitle("How to use GitFlow Helper");
        setResizable(true);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(JBUI.Borders.empty(12));
        root.setPreferredSize(new Dimension(650, 500));

        JEditorPane htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        
        HTMLEditorKit kit = HTMLEditorKitBuilder.simple();
        htmlPane.setEditorKit(kit);

        Color titleColor = JBColor.namedColor("Label.infoForeground", new JBColor(0x4a86e8, 0x4a86e8));
        Color sectionColor = JBColor.namedColor("Label.foreground", UIUtil.getLabelForeground());
        Color secondaryColor = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY);
        
        String titleHex = toHex(titleColor);
        String sectionHex = toHex(sectionColor);
        String secondaryHex = toHex(secondaryColor);
        String borderHex = toHex(JBColor.namedColor("Divider.background", JBColor.LIGHT_GRAY));
        String backgroundHex = toHex(UIUtil.getPanelBackground());

        String htmlContent = "<html>" +
                "<body style='font-family: sans-serif; padding: 10px; color: " + sectionHex + "; background-color: " + backgroundHex + ";'>" +
                "<h2 style='color: " + titleHex + "; border-bottom: 1px solid " + borderHex + "; padding-bottom: 5px; margin-bottom: 15px;'>Welcome to GitFlow Helper!</h2>" +
                "<p>This plugin helps you implement the classic <b>Git Flow</b> workflow directly within your IDE.</p>" +
                
                "<div style='margin-left: 5px;'>" +
                "<h3 style='color: " + sectionHex + "; margin-top: 20px; margin-bottom: 5px;'>1. Initialization</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li style='margin-bottom: 5px;'>If your project hasn't used Git Flow yet, start by clicking the <b>Init</b> action.</li>" +
                "  <li>Configure your main branch (e.g., <code>master</code> or <code>main</code>), development branch (e.g., <code>develop</code>), and prefixes.</li>" +
                "</ul>" +

                "<h3 style='color: " + sectionHex + "; margin-top: 15px; margin-bottom: 5px;'>2. Features</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li style='margin-bottom: 5px;'><b>Start a feature:</b> Creates a new branch from <code>develop</code>.</li>" +
                "  <li style='margin-bottom: 5px;'><b>Finish a feature:</b> Merges the feature branch back into <code>develop</code> and deletes the local branch.</li>" +
                "  <li><b>Publish/Sync:</b> Helps sharing features with remote repositories.</li>" +
                "</ul>" +

                "<h3 style='color: " + sectionHex + "; margin-top: 15px; margin-bottom: 5px;'>3. Releases</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li style='margin-bottom: 5px;'><b>Start a release:</b> Creates a new branch from <code>develop</code> for stabilization.</li>" +
                "  <li><b>Finish a release:</b> Merges into both <code>main</code> and <code>develop</code>, and creates a version tag.</li>" +
                "</ul>" +

                "<h3 style='color: " + sectionHex + "; margin-top: 15px; margin-bottom: 5px;'>4. Hotfixes</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li style='margin-bottom: 5px;'><b>Start a hotfix:</b> Creates a branch from <code>main</code> for urgent production fixes.</li>" +
                "  <li><b>Finish a hotfix:</b> Merges into both <code>main</code> and <code>develop</code>, and creates a version tag.</li>" +
                "</ul>" +

                "<h3 style='color: " + sectionHex + "; margin-top: 15px; margin-bottom: 5px;'>5. Task Integration</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li>Link your branches to tasks from supported issue trackers (Jira, GitHub, GitLab, etc.) if configured in the IDE.</li>" +
                "</ul>" +

                "<h3 style='color: " + sectionHex + "; margin-top: 15px; margin-bottom: 5px;'>6. Tool Window</h3>" +
                "<ul style='margin-top: 5px;'>" +
                "  <li>Use the <b>GitFlow tool window</b> to see a graphical representation of your branches and quick access to actions.</li>" +
                "</ul>" +
                "</div>" +
                "<p style='margin-top: 30px; color: " + secondaryHex + "; font-size: small; border-top: 1px solid " + borderHex + "; padding-top: 10px;'>" +
                "💡 <b>Tip:</b> Most actions are available in the VCS menu or through the GitFlow icon in the status bar.</p>" +
                "</body></html>";

        htmlPane.setText(htmlContent);
        htmlPane.setCaretPosition(0);
        htmlPane.setBackground(UIUtil.getPanelBackground());

        JBScrollPane scrollPane = new JBScrollPane(htmlPane);
        scrollPane.setBorder(JBUI.Borders.empty());
        root.add(scrollPane, BorderLayout.CENTER);

        return root;
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }
}
