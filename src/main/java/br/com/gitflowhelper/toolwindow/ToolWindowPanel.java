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
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToolWindowPanel extends JPanel {
    private static final Pattern GIT_C_DIR_PATTERN = Pattern.compile("-C\\s+[\"']?([^\"'\\s]+)[\"']?");
    private static final String REPO_PREFIX_COLOR = "#59A869";

    private final JTextPane textPane;
    private final HTMLDocument doc;
    private final HTMLEditorKit kit;
    private final Project project;
    private Runnable onNewContent;
    private String currentRepoName;

    public ToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        String htmlContent = "<html><body></body></html>";

        textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(htmlContent);
        textPane.setEditable(false); // Prevents user editing
        // Hide the blinking caret while keeping text selection enabled.
        textPane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                textPane.getCaret().setVisible(false);
            }
        });

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

    public void setCurrentRepoName(@Nullable String currentRepoName) {
        this.currentRepoName = currentRepoName;
    }

    @Nullable
    public String getCurrentRepoName() {
        return currentRepoName;
    }

    public void append(String text) {
        append(null, text);
    }

    public void append(@Nullable String repoName, String text) {
        String resolvedRepo = resolveRepoName(repoName, text);
        if (resolvedRepo != null && !resolvedRepo.isEmpty()) {
            this.currentRepoName = resolvedRepo;
        }

        String formattedText = formatTextWithRepo(resolvedRepo, text);

        try {
            Element body = doc.getRootElements()[0].getElement(1); // html -> body
            // inserts before body closing
            kit.insertHTML(doc, body.getEndOffset() - 1, formattedText, 0, 0, null);
            // scroll to the end
            textPane.setCaretPosition(doc.getLength());
            if (onNewContent != null) {
                onNewContent.run();
            }
        } catch (BadLocationException | IOException e) {
            PluginUtils.logError(this.project, PluginUtils.getStackTrace(e));
        }
    }

    @Nullable
    private String resolveRepoName(@Nullable String repoName, @Nullable String text) {
        if (repoName != null && !repoName.trim().isEmpty()) {
            return repoName.trim();
        }

        // Try to extract from "-C <path>" if present in command text
        if (text != null) {
            Matcher matcher = GIT_C_DIR_PATTERN.matcher(text);
            if (matcher.find()) {
                String path = matcher.group(1);
                return new File(path).getName();
            }
        }

        if (this.currentRepoName != null && !this.currentRepoName.trim().isEmpty()) {
            return this.currentRepoName.trim();
        }

        if (project != null && !project.isDisposed()) {
            try {
                GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
                List<GitRepository> repos = repoManager.getRepositories();
                if (repos.size() == 1) {
                    return repos.get(0).getRoot().getName();
                }
                List<GitRepository> selected = GitFlowSettingsService.getInstance(project).getSelectedRepositories();
                if (selected.size() == 1) {
                    return selected.get(0).getRoot().getName();
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String formatTextWithRepo(@Nullable String repoName, String text) {
        if (text == null || text.isEmpty() || repoName == null || repoName.trim().isEmpty()) {
            return text;
        }
        String prefix = "<font color=\"" + REPO_PREFIX_COLOR + "\">[" + repoName.trim() + "]</font> ";

        int preStart = text.indexOf("<pre");
        int preClose = text.lastIndexOf("</pre>");
        if (preStart >= 0 && preClose > preStart) {
            int preEnd = text.indexOf('>', preStart);
            if (preEnd >= 0 && preEnd < preClose) {
                String openTag = text.substring(0, preEnd + 1);
                String closeTag = text.substring(preClose);
                String inner = text.substring(preEnd + 1, preClose);

                String formattedInner;
                if (inner.startsWith("<font") && inner.endsWith("</font>")) {
                    int fontEnd = inner.indexOf('>');
                    int fontClose = inner.lastIndexOf("</font>");
                    if (fontEnd >= 0 && fontClose > fontEnd) {
                        String fontOpen = inner.substring(0, fontEnd + 1);
                        String fontCloseTag = inner.substring(fontClose);
                        String fontInner = inner.substring(fontEnd + 1, fontClose);
                        formattedInner = fontOpen + prefixLines(fontInner, prefix) + fontCloseTag;
                    } else {
                        formattedInner = prefixLines(inner, prefix);
                    }
                } else {
                    formattedInner = prefixLines(inner, prefix);
                }
                return openTag + formattedInner + closeTag;
            }
        }

        return prefixLines(text, prefix);
    }

    private String prefixLines(String content, String prefix) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String delimiter = content.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = content.split(delimiter, -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                sb.append(delimiter);
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !isAlreadyPrefixed(line)) {
                sb.append(prefix).append(line);
            } else {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private boolean isAlreadyPrefixed(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("$ [")) {
            return true;
        }
        if ((trimmed.startsWith("<font") || trimmed.startsWith("$ <font")) && trimmed.contains(">[")) {
            return true;
        }
        return false;
    }

    /** Registers a callback invoked whenever new content is appended. */
    public void setOnNewContent(Runnable onNewContent) {
        this.onNewContent = onNewContent;
    }
}
