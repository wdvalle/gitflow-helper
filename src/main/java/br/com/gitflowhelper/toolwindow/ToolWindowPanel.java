package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.IOException;

public class ToolWindowPanel extends JPanel {
    private final JTextPane textPane;
    private final HTMLDocument doc;
    private final HTMLEditorKit kit;
    private final Project project;

    public ToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        String htmlContent = "<html><body></body></html>";

        textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(htmlContent);
        textPane.setEditable(false); // Impede edição pelo usuário

        doc = (HTMLDocument) textPane.getDocument();
        kit = (HTMLEditorKit) textPane.getEditorKit();

        // Action Group
        ActionGroup actionGroup = new WindowActionGroup(textPane, GitFlowSettingsService.getInstance(project).getShowDetails());
        ActionToolbar toolbar = ActionManager.getInstance()
            .createActionToolbar(
                "MyToolWindowToolbar",
                actionGroup,
                true // true = horizontal
            );

        toolbar.setTargetComponent(this);
        toolbar.getComponent().setBorder(
                IdeBorderFactory.createBorder(SideBorder.BOTTOM)
        );

        // toolbar container
        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);

        JBScrollPane scrollPane = new JBScrollPane(textPane);
        add(scrollPane, BorderLayout.CENTER);
        add(toolbarPanel, BorderLayout.NORTH);
    }

    public void append(String text) {
        try {
            Element body = doc.getRootElements()[0].getElement(1); // html -> body
            // inserts before body closing
            kit.insertHTML(doc, body.getEndOffset() - 1, text, 0, 0,null);
            // scroll to the end
            textPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException | IOException e) {
            PluginUtils.logError(this.project, PluginUtils.getStackTrace(e));
        }
    }
}