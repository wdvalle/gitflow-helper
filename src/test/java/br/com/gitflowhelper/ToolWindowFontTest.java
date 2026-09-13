package br.com.gitflowhelper;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.GitFlowSettingsState;
import br.com.gitflowhelper.toolwindow.ToolWindowPanel;
import br.com.gitflowhelper.util.HtmlGitCleaner;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class ToolWindowFontTest {

    @Test
    public void testGitFlowSettingsStateFontFields() {
        GitFlowSettingsState state = new GitFlowSettingsState();
        assertNull(state.getLogFontFamily());
        assertNull(state.getLogFontSize());

        state.setLogFontFamily("JetBrains Mono");
        state.setLogFontSize(14);
        assertEquals("JetBrains Mono", state.getLogFontFamily());
        assertEquals(14, state.getLogFontSize());

        GitFlowSettingsState copy = new GitFlowSettingsState();
        copy.setLogFontFamily("JetBrains Mono");
        copy.setLogFontSize(14);
        assertEquals(state, copy);
        assertEquals(state.hashCode(), copy.hashCode());
    }

    @Test
    public void testDefaultsFallbackWhenUnset() {
        String defaultFont = GitFlowSettingsService.getDefaultFontFamily();
        assertNotNull(defaultFont);
        assertFalse(defaultFont.isEmpty());

        int defaultSize = GitFlowSettingsService.getDefaultFontSize();
        assertTrue(defaultSize > 0);
    }

    @Test
    public void testMultipleFontChangesDoNotBreakGitCommands() throws Exception {
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText("<html><body></body></html>");
        HTMLDocument doc = (HTMLDocument) textPane.getDocument();
        HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();

        String rawCommand = "git -c diff.mnemonicprefix=false -c core.quotepath=false checkout main";
        String commented = HtmlGitCleaner.commentGitCParams(rawCommand);
        String htmlToInsert = "<pre style=\"margin:0; padding:0\">$ <font color=\"#4a8dff\">" + commented + "</font></pre>";

        Element body = doc.getRootElements()[0].getElement(1);
        kit.insertHTML(doc, body.getEndOffset() - 1, htmlToInsert, 0, 0, null);

        // Apply 12pt font
        StyleSheet styleSheet = doc.getStyleSheet();
        styleSheet.addRule("body, pre, code { font-family: 'JetBrains Mono', monospace; font-size: 12pt; }");
        textPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        textPane.setSize(new Dimension(500, 500));
        textPane.doLayout();
        View view12 = textPane.getUI().getRootView(textPane);
        view12.setSize(500, 500);
        float h12 = view12.getPreferredSpan(View.Y_AXIS);

        int initialDocLen = doc.getLength();
        String initialDocText = doc.getText(0, initialDocLen);

        // Simulate 5 consecutive font changes (family and size)
        String[] fonts = {"Courier New", "Monospaced", "JetBrains Mono", "Menlo", "Consolas"};
        int[] sizes = {14, 16, 11, 20, 12};

        for (int i = 0; i < fonts.length; i++) {
            styleSheet.addRule("body, pre, code { font-family: '" + fonts[i] + "', monospace; font-size: " + sizes[i] + "pt; }");
            textPane.setFont(new Font(fonts[i], Font.PLAIN, sizes[i]));
            textPane.setUI(textPane.getUI());
            textPane.revalidate();
            textPane.repaint();

            // Check that the text in document has NOT mutated or added newlines
            assertEquals(initialDocLen, doc.getLength(), "Document length must remain constant on font switch " + i);
            assertEquals(initialDocText, doc.getText(0, doc.getLength()), "Document text must not change on font switch " + i);
        }

        // Final check that view span responds to size changes
        styleSheet.addRule("body, pre, code { font-family: 'JetBrains Mono', monospace; font-size: 24pt; }");
        textPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 24));
        textPane.setUI(textPane.getUI());
        textPane.setSize(new Dimension(500, 500));
        textPane.doLayout();
        View view24 = textPane.getUI().getRootView(textPane);
        view24.setSize(500, 500);
        float h24 = view24.getPreferredSpan(View.Y_AXIS);

        assertTrue(h24 > h12, "Height must grow when font is enlarged to 24pt");
    }
}
