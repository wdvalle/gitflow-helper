package br.com.gitflowhelper.toolwindow.ci;

import br.com.gitflow.cicd.StepLogProvider;
import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import com.intellij.icons.AllIcons;
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
 * - North: PipelineHeaderPanel with build status, timer, and actions
 * - Center: Single panel (Pipeline DAG canvas only) or Split panel (DAG on left, Console on right)
 */
public class RepoCiDashboardPanel extends JPanel {

    private final Project project;
    private final String repoPath;

    private final PipelineHeaderPanel headerPanel = new PipelineHeaderPanel();
    private final PipelineDagCanvas dagCanvas = new PipelineDagCanvas();
    private final JBHtmlEditorPane consolePane = new JBHtmlEditorPane();
    private final JBScrollPane dagScrollPane;
    private final JBScrollPane consoleScrollPane;
    private final JPanel consoleContainer;
    private final JBSplitter splitter;
    private final JPanel centerPanel = new JPanel(new BorderLayout());

    private boolean splitMode = false;
    private boolean autoScroll = true;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private final StringBuilder rawLogsBuffer = new StringBuilder();
    private PipelineRun lastRun;
    private String lastPlatformName;

    private Runnable onRerunTrigger;
    private Runnable onStopMonitoring;
    private Runnable onNewContent;
    private Runnable onClear;

    public RepoCiDashboardPanel(@Nullable Project project, @NotNull String repoPath) {
        super(new BorderLayout());
        this.project = project;
        this.repoPath = repoPath;
        this.dagCanvas.setProject(project);

        // Top: Pipeline Header
        add(headerPanel, BorderLayout.NORTH);

        // DAG Canvas scroll pane
        dagScrollPane = new JBScrollPane(dagCanvas);
        dagScrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Console container with mini toolbar
        consoleContainer = new JPanel(new BorderLayout());
        consoleScrollPane = new JBScrollPane(consolePane);
        consoleScrollPane.setBorder(BorderFactory.createEmptyBorder());
        consoleContainer.add(consoleScrollPane, BorderLayout.CENTER);
        consoleContainer.add(createConsoleToolbar(), BorderLayout.NORTH);

        // Center Splitter: Left = DAG Graph, Right = Console Output
        splitter = new JBSplitter(false, 0.48f);

        // Center holder starts in single-panel mode (DAG only)
        add(centerPanel, BorderLayout.CENTER);
        setSplitMode(false);

        headerPanel.setCallbacks(
                () -> {
                    if (onRerunTrigger != null) onRerunTrigger.run();
                },
                () -> {
                    if (onStopMonitoring != null) onStopMonitoring.run();
                },
                () -> setSplitMode(!splitMode),
                () -> {
                    if (onClear != null) {
                        onClear.run();
                    } else {
                        if (onStopMonitoring != null) {
                            onStopMonitoring.run();
                        }
                        restoreIdlePipeline();
                    }
                }
        );
    }

    /**
     * Toggles between single panel (DAG only) and two-panel split view (DAG + Console).
     *
     * @param split true to show two panels side-by-side; false to show single panel DAG
     */
    public void setSplitMode(boolean split) {
        if (this.splitMode == split && centerPanel.getComponentCount() > 0) return;
        this.splitMode = split;
        dagCanvas.setSplitMode(split);

        centerPanel.removeAll();
        if (split) {
            splitter.setFirstComponent(dagScrollPane);
            splitter.setSecondComponent(consoleContainer);
            centerPanel.add(splitter, BorderLayout.CENTER);
        } else {
            splitter.setFirstComponent(null);
            splitter.setSecondComponent(null);
            centerPanel.add(dagScrollPane, BorderLayout.CENTER);
        }
        headerPanel.setSplitMode(split);

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    public boolean isSplitMode() {
        return splitMode;
    }

    public void setStepLogProvider(@Nullable StepLogProvider provider) {
        dagCanvas.setStepLogProvider(provider);
    }

    public void setIdle(boolean idle) {
        dagCanvas.setIdle(idle);
    }

    public boolean isIdle() {
        return dagCanvas.isIdle();
    }

    public boolean isRunning() {
        return headerPanel.isRunning();
    }

    public void setRunning(boolean running) {
        headerPanel.setRunning(running);
    }

    public PipelineHeaderPanel getHeaderPanel() {
        return headerPanel;
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
        scrollBtn.setToolTipText("Scroll to bottom");
        scrollBtn.setPreferredSize(new Dimension(24, 24));
        scrollBtn.addActionListener(e -> {
            autoScroll = true;
            scrollToBottom();
        });
        toolbar.add(scrollBtn);

        JButton copyBtn = new JButton(AllIcons.Actions.Copy);
        copyBtn.setToolTipText("Copy Console Output");
        copyBtn.setPreferredSize(new Dimension(24, 24));
        copyBtn.addActionListener(e -> {
            String logText;
            synchronized (rawLogsBuffer) {
                logText = rawLogsBuffer.toString();
            }
            if (!logText.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(logText), null);
            }
        });
        toolbar.add(copyBtn);

        JButton clearBtn = new JButton(AllIcons.Actions.GC);
        clearBtn.setToolTipText("Clear Console");
        clearBtn.setPreferredSize(new Dimension(24, 24));
        clearBtn.addActionListener(e -> clearConsole());
        toolbar.add(clearBtn);

        bar.add(toolbar, BorderLayout.EAST);
        return bar;
    }

    public String getRawLogs() {
        synchronized (rawLogsBuffer) {
            return rawLogsBuffer.toString();
        }
    }

    public @Nullable PipelineRun getLastRun() {
        return lastRun;
    }

    private void runOnEdt(Runnable r) {
        if (ApplicationManager.getApplication() != null) {
            ApplicationManager.getApplication().invokeLater(r);
        } else if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    public void clearConsole() {
        Runnable r = () -> {
            synchronized (rawLogsBuffer) {
                rawLogsBuffer.setLength(0);
            }
            consolePane.setText("");
        };
        runOnEdt(r);
    }

    private static String toPlainText(@Nullable String htmlChunk) {
        if (htmlChunk == null || htmlChunk.isEmpty()) {
            return "";
        }
        return htmlChunk
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }

    /**
     * Scrolls the console scroll pane directly to the bottom.
     */
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            try {
                int len = consolePane.getDocument().getLength();
                if (len > 0) {
                    consolePane.setCaretPosition(len);
                }
            } catch (Exception ignored) {
            }
            JScrollBar vertical = consoleScrollPane.getVerticalScrollBar();
            if (vertical != null) {
                vertical.setValue(vertical.getMaximum());
            }
        });
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onContent) {
        setCallbacks(onRerun, onStop, onContent, null);
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onContent, @Nullable Runnable onClear) {
        this.onRerunTrigger = onRerun;
        this.onStopMonitoring = onStop;
        this.onNewContent = onContent;
        this.onClear = onClear;
    }

    public void setPlatformName(@NotNull String platformName) {
        headerPanel.setPlatformName(platformName);
    }

    public void startLoading() {
        Runnable r = () -> {
            clearConsole();
            dagCanvas.setIdle(false);
            dagCanvas.setLoading(true);
            headerPanel.updatePipelineRun(null, null);
            headerPanel.setRunning(true);
        };
        runOnEdt(r);
    }

    public void stopLoading() {
        Runnable r = () -> {
            dagCanvas.setLoading(false);
            headerPanel.setRunning(false);
        };
        runOnEdt(r);
    }

    /**
     * Stops loading and marks pipeline execution as ABORTED on canvas and header badge.
     */
    public void markExecutionStopped() {
        Runnable r = () -> {
            dagCanvas.markAborted();
            headerPanel.markAborted();
            headerPanel.setRunning(false);
            appendPluginLog("Pipeline execution stopped by user.");
        };
        runOnEdt(r);
    }

    public void updatePipelineRun(@Nullable PipelineRun run, @Nullable String platformName) {
        if (run != null) {
            this.lastRun = run;
            if (platformName != null) {
                this.lastPlatformName = platformName;
            }
        }
        Runnable r = () -> {
            headerPanel.updatePipelineRun(run, platformName);
            if (run != null && !run.getStages().isEmpty()) {
                dagCanvas.setLoading(false);
            }
            dagCanvas.updatePipelineRun(run);
            if (onNewContent != null) {
                onNewContent.run();
            }
        };
        runOnEdt(r);
    }

    public void appendConsoleLog(@NotNull String content) {
        appendConsoleLog(content, false);
    }

    public void appendPluginLog(@NotNull String text) {
        appendConsoleLog(text, true);
    }

    public void appendConsoleLog(@NotNull String content, boolean isPluginLog) {
        Runnable r = () -> {
            String timestamp = dtf.format(LocalDateTime.now());
            String toAppend;
            if (isPluginLog) {
                toAppend = "<font color='#FFFFFF'>" + timestamp + ": " + content + "</font><br>";
                synchronized (rawLogsBuffer) {
                    rawLogsBuffer.append(timestamp).append(": ").append(content).append("\n");
                }
            } else {
                toAppend = content;
                String plain = toPlainText(content);
                synchronized (rawLogsBuffer) {
                    rawLogsBuffer.append(plain);
                }
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
                scrollToBottom();
            }

            if (onNewContent != null) {
                onNewContent.run();
            }
        };

        runOnEdt(r);
    }

    /**
     * Clears console output and restores the DAG and header to idle mode displaying only the stages,
     * without any data from the last execution.
     */
    public void restoreIdlePipeline() {
        Runnable r = () -> {
            clearConsole();
            dagCanvas.setLoading(false);
            headerPanel.setRunning(false);
            if (lastRun != null && !lastRun.getStages().isEmpty()) {
                PipelineRun idleRun = createIdleRun(lastRun);
                dagCanvas.updatePipelineRun(idleRun);
                dagCanvas.setIdle(true);
            } else {
                dagCanvas.setIdle(true);
                dagCanvas.updatePipelineRun(null);
            }
            headerPanel.updatePipelineRun(null, lastPlatformName != null ? lastPlatformName : headerPanel.getPlatformName());
            setSplitMode(false);
        };
        runOnEdt(r);
    }

    public static @NotNull PipelineRun createIdleRun(@NotNull PipelineRun sourceRun) {
        PipelineRun idleRun = new PipelineRun(null, "No Build", PipelineStatus.NOT_STARTED);
        for (PipelineStage stage : sourceRun.getStages()) {
            PipelineStage idleStage = new PipelineStage(stage.getId(), stage.getName(), PipelineStatus.NOT_STARTED, 0);
            idleRun.addStage(idleStage);
        }
        return idleRun;
    }

    public void clear() {
        restoreIdlePipeline();
    }

    public @NotNull PipelineDagCanvas getDagCanvas() {
        return dagCanvas;
    }

    public @NotNull JEditorPane getConsolePane() {
        return consolePane;
    }

    public void dispose() {
        dagCanvas.dispose();
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
