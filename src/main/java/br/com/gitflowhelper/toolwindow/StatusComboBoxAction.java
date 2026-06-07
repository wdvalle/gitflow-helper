package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.HtmlGitCleaner;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.io.StringReader;
import java.io.StringWriter;

public class StatusComboBoxAction extends ComboBoxAction {

    private boolean showingDetails = true;
    private JTextPane textPane;

    public StatusComboBoxAction(JTextPane textPane, Boolean showDetails) {
        this.textPane = textPane;
        this.showingDetails = showDetails;
    }

    @Override
    protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button, DataContext context) {
        DefaultActionGroup group = new DefaultActionGroup();

        group.add(new AnAction("Show params") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                showingDetails = true;
                GitFlowSettingsService.getInstance(e.getProject()).setShowDetails(showingDetails);
                try {
                    HTMLDocument doc = (HTMLDocument) textPane.getDocument();
                    HTMLEditorKit kit = new HTMLEditorKit();
                    StringWriter writer = new StringWriter();
                    kit.write(writer, doc, 0, doc.getLength());
                    String htmlOriginal = writer.toString();
                    String htmlModificado = htmlOriginal.replaceAll("<!--", "").replaceAll("-->\n", "");
                    doc.remove(0, doc.getLength());
                    kit.read(new StringReader(htmlModificado), doc, 0);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                button.repaint();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                showingDetails = GitFlowSettingsService.getInstance(e.getProject()).getShowDetails();
                Presentation presentation = e.getPresentation();
                presentation.setEnabled(!showingDetails);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        });

        group.add(new AnAction("Hide params") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                showingDetails = false;
                GitFlowSettingsService.getInstance(e.getProject()).setShowDetails(showingDetails);
                try {
                    HTMLDocument doc = (HTMLDocument) textPane.getDocument();
                    HTMLEditorKit kit = new HTMLEditorKit();
                    StringWriter writer = new StringWriter();
                    kit.write(writer, doc, 0, doc.getLength());
                    String htmlOriginal = writer.toString();
                    String htmlModificado = HtmlGitCleaner.commentGitCParams(htmlOriginal);;
                    doc.remove(0, doc.getLength());
                    kit.read(new StringReader(htmlModificado), doc, 0);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                button.repaint();
            }
            @Override
            public void update(@NotNull AnActionEvent e) {
                showingDetails = GitFlowSettingsService.getInstance(e.getProject()).getShowDetails();
                Presentation presentation = e.getPresentation();
                presentation.setEnabled(showingDetails);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        });

        return group;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        String text = showingDetails ? "Show params" : "Hide params";
        e.getPresentation().setText(text);
    }


    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}