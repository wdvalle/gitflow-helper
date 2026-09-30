package br.com.gitflowhelper.toolwindow.ci;

import br.com.gitflow.cicd.model.PipelineRun;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Hybrid dashboard panel for a single repository CI/CD pipeline:
 * - North: PipelineHeaderPanel with build status, timer and actions
 * - Splitter Left: PipelineDagCanvas (DAG visualization with connected stages and stacked steps)
 * - Splitter Right: Console log viewer with streaming output
 */
public class RepoCiDashboardPanel extends JPanel {

    private final Project project;
    private final String repoPath;

    private final PipelineHeaderPanel headerPanel = new PipelineHeaderPanel();
    private final PipelineDagCanvas dagCanvas = new PipelineDagCanvas();
    private final JBHtmlEditorPane consolePane = new JBHtmlEditorPane();
    private final JBScrollPane consoleScrollPane;
    private final JBSplitter splitter;

    private boolean autoScroll = true;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private Runnable onRerunTrigger;
    private Runnable onStopMonitoring;
    private Runnable onNewContent;

    public RepoCiDashboardPanel(@NotNull Project project, @NotNull String repoPath) {
        super(new BorderLayout());
        this.project = project;
        this.repoPath = repoPath;

        // Top: Pipeline Header
        add(headerPanel, BorderLayout.NORTH);

        // Center Splitter: Left = DAG Graph, Right = Console Output
        splitter = new JBSplitter(false, 0.48f);

        JBScrollPane dagScrollPane = new JBScrollPane(dagCanvas);
        dagScrollPane.setBorder(BorderFactory.createEmptyBorder());
        splitter.setFirstComponent(dagScrollPane);

        // Right side: Console output with mini toolbar
        JPanel consoleContainer = new JPanel(new BorderLayout());
        consoleScrollPane = new JBScrollPane(consolePane);
        consoleScrollPane.setBorder(BorderFactory.createEmptyBorder());
        consoleContainer.add(consoleScrollPane, BorderLayout.CENTER);
        consoleContainer.add(createConsoleToolbar(), BorderLayout.NORTH);

        splitter.setSecondComponent(consoleContainer);

        add(splitter, BorderLayout.CENTER);

        headerPanel.setCallbacks(
                () -> {
                    if (onRerunTrigger != null) onRerunTrigger.run();
                },
                () -> {
                    if (onStopMonitoring != null) onStopMonitoring.run();
                }
        );
    }

    private JComponent createConsoleToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        toolbar.setBackground(consolePane.getBackground());

        JLabel label = new JLabel("Console Output");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(Color.GRAY);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.setOpaque(false);
        left.add(label);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(left, BorderLayout.WEST);

        JButton scrollBtn = new JButton(AllIcons.Actions.MoveDown);
        scrollBtn.setToolTipText("Auto-scroll to bottom");
        scrollBtn.setPreferredSize(new Dimension(24, 24));
        scrollBtn.addActionListener(e -> {
            autoScroll = !autoScroll;
            if (autoScroll) {
                consolePane.setCaretPosition(consolePane.getDocument().getLength());
            }
        });
        toolbar.add(scrollBtn);

        JButton copyBtn = new JButton(AllIcons.Actions.Copy);
        copyBtn.setToolTipText("Copy Console Output");
        copyBtn.setPreferredSize(new Dimension(24, 24));
        copyBtn.addActionListener(e -> {
            String text = consolePane.getText();
            if (text != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            }
        });
        toolbar.add(copyBtn);

        JButton clearBtn = new JButton(AllIcons.Actions.GC);
        clearBtn.setToolTipText("Clear Console");
        clearBtn.setPreferredSize(new Dimension(24, 24));
        clearBtn.addActionListener(e -> consolePane.setText(""));
        toolbar.add(clearBtn);

        bar.add(toolbar, BorderLayout.EAST);
        return bar;
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onContent) {
        this.onRerunTrigger = onRerun;
        this.onStopMonitoring = onStop;
        this.onNewContent = onContent;
    }

    public void setPlatformName(@NotNull String platformName) {
        headerPanel.setPlatformName(platformName);
    }

    public void updatePipelineRun(@Nullable PipelineRun run, @Nullable String platformName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            headerPanel.updatePipelineRun(run, platformName);
            dagCanvas.updatePipelineRun(run);
            if (onNewContent != null) {
                onNewContent.run();
            }
        });
    }

    public void appendConsoleLog(@NotNull String content) {
        appendConsoleLog(content, false);
    }

    public void appendPluginLog(@NotNull String text) {
        appendConsoleLog(text, true);
    }

    public void appendConsoleLog(@NotNull String content, boolean isPluginLog) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String timestamp = dtf.format(LocalDateTime.now());
            String toAppend;
            if (isPluginLog) {
                toAppend = "<font color='#FFFFFF'>" + timestamp + ": " + content + "</font><br>";
            } else {
                toAppend = content;
            }

            try {
                HTMLDocument doc = (HTMLDocument) consolePane.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) consolePane.getEditorKit();
                Element body = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
                if (body != null) {
                    kit.insertHTML(doc, body.getEndOffset() - 1, toAppend, 0, 0, null);
                } else {
                    kit.insertHTML(doc, doc.getLength(), toAppend, 0, 0, null);
                }
            } catch (Exception e) {
                String currentText = consolePane.getText();
                String body = "";
                if (currentText != null && currentText.contains("<body>")) {
                    int bodyStart = currentText.indexOf("<body>") + 6;
                    int bodyEnd = currentText.lastIndexOf("</body>");
                    if (bodyEnd > bodyStart) body = currentText.substring(bodyStart, bodyEnd);
                }
                consolePane.setText(body + toAppend);
            }

            if (autoScroll) {
                consolePane.setCaretPosition(consolePane.getDocument().getLength());
            }

            if (onNewContent != null) {
                onNewContent.run();
            }
        });
    }

    public void clear() {
        ApplicationManager.getApplication().invokeLater(() -> {
            consolePane.setText("");
            dagCanvas.updatePipelineRun(null);
            headerPanel.updatePipelineRun(null, null);
        });
    }

    public @NotNull PipelineDagCanvas getDagCanvas() {
        return dagCanvas;
    }

    public @NotNull JEditorPane getConsolePane() {
        return consolePane;
    }

    private static class JBHtmlEditorPane extends JEditorPane implements ComponentWithEmptyText {
        private final StatusText emptyText = new StatusText(this) {
            @Override
            protected boolean isStatusVisible() {
                return getDocument().getLength() == 0;
            }
        };

        public JBHtmlEditorPane() {
            super("text/html", "");
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize()));
            setBackground(UIManager.getColor("TextField.background"));
            setEditable(false);
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    getCaret().setVisible(false);
                }
            });
        }

        @Override
        public @NotNull StatusText getEmptyText() {
            return emptyText;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            emptyText.paint(this, g);
        }
    }
}
