package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.cicd.JenkinsConnector;
import br.com.gitflowhelper.dialog.ConfigDialog;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CIDataToolWindowPanel extends JPanel implements Disposable {

    private final Project project;
    private final JBHtmlEditorPane logPane;
    private ScheduledExecutorService executor;
    private JenkinsConnector jenkinsConnector;
    private Runnable onStopped;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");


    public CIDataToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        logPane = new JBHtmlEditorPane();
        logPane.setEditable(false);
        // Hide the blinking caret while keeping text selection enabled.
        // A FocusListener is used instead of setCaret() in the constructor
        // to avoid potential exceptions during component initialization.
        logPane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                logPane.getCaret().setVisible(false);
            }
        });
        updateEmptyText();

        add(new JBScrollPane(logPane), BorderLayout.CENTER);

        project.getMessageBus().connect(this).subscribe(GitFlowSettingsListener.TOPIC, this::updateEmptyText);
    }

    public void startMonitoring() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        if (!settings.isIntegrateWithCI()) {
            appendLog("CI/CD integration is disabled.");
            updateEmptyText();
            return;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        clear();
        appendLog("Starting CI/CD monitoring...");

        if ("Jenkins".equals(settings.getCiType())) {
            jenkinsConnector = new JenkinsConnector(
                    settings.getCiUrl(),
                    settings.getCiLogin(),
                    settings.getCiToken()
            );
        } else {
            jenkinsConnector = null;
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkBuildStatus, 0, 2, TimeUnit.SECONDS);
    }

    private void checkBuildStatus() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        if (!settings.isIntegrateWithCI()) {
            stopMonitoring();
            appendLog("CI/CD integration disabled. Stopping monitor.");
            return;
        }

        if (jenkinsConnector != null) {
            String chunk = jenkinsConnector.fetchNextChunk();

            if (!chunk.isEmpty()) {
                appendLog(chunk);
            }

            // Stop when Jenkins signals no more data (build finished or error)
            if (!jenkinsConnector.hasMoreData()) {
                stopMonitoring();
            }
        } else {
            appendLog(settings.getCiType() + " not yet supported.");
            stopMonitoring();
        }
    }

    private void appendLog(String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String timestamp = dtf.format(LocalDateTime.now());
            String currentText = logPane.getText();
            String body = "";
            if (currentText != null && currentText.contains("<body>")) {
                int bodyStart = currentText.indexOf("<body>") + 6;
                int bodyEnd = currentText.lastIndexOf("</body>");
                if (bodyEnd > bodyStart) {
                    body = currentText.substring(bodyStart, bodyEnd);
                }
            }
            logPane.setText(body + timestamp + ": " + text + "<br>");
            logPane.setCaretPosition(logPane.getDocument().getLength());
        });
    }

    /** Registers a callback invoked on the EDT whenever monitoring stops. */
    public void setOnStopped(Runnable onStopped) {
        this.onStopped = onStopped;
    }

    public void stopMonitoring() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            appendLog("CI/CD monitoring stopped.");
        }
        executor = null;
        jenkinsConnector = null;
        if (onStopped != null) {
            ApplicationManager.getApplication().invokeLater(onStopped);
        }
    }

    public void clear() {
        ApplicationManager.getApplication().invokeLater(() -> {
            logPane.setText("");
        });
    }

    private void updateEmptyText() {
        StatusText emptyText = logPane.getEmptyText();
        emptyText.clear();
        if (!GitFlowSettingsService.getInstance(project).isIntegrateWithCI()) {
            emptyText.setText("CI/CD integration is disabled.");
            emptyText.appendLine("Enable it in Git Flow Helper settings", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
                new ConfigDialog(project).show();
            });
        } else {
            emptyText.setText("No CI/CD data to display.");
            emptyText.appendLine("Click the 'play' button to start monitoring.");
        }
    }

    @Override
    public void dispose() {
        stopMonitoring();
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