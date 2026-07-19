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
import java.util.concurrent.atomic.AtomicReference;

public class CIDataToolWindowPanel extends JPanel implements Disposable {

    private final Project project;
    private final JBHtmlEditorPane logPane;
    private ScheduledExecutorService executor;
    private final AtomicReference<String> lastStatus = new AtomicReference<>("");
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");


    public CIDataToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        logPane = new JBHtmlEditorPane();
        logPane.setEditable(false);
        logPane.setFocusable(false); // Prevent caret from appearing when focused
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
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkBuildStatus, 0, 5, TimeUnit.SECONDS);
    }

    private void checkBuildStatus() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        if (!settings.isIntegrateWithCI()) {
            stopMonitoring();
            appendLog("CI/CD integration disabled. Stopping monitor.");
            return;
        }

        String ciType = settings.getCiType();
        String url = settings.getCiUrl();
        String token = settings.getCiToken();
        String jobName = project.getName();

        String status;
        if ("Jenkins".equals(ciType)) {
            JenkinsConnector connector = new JenkinsConnector(url, token);
            status = connector.getBuildStatus(jobName);
        } else {
            status = ciType + " not yet supported.";
        }

        String previousStatus = lastStatus.get();
        if (!status.equals(previousStatus)) {
            lastStatus.set(status);
            appendLog(status);
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

    public void stopMonitoring() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            appendLog("CI/CD monitoring stopped.");
        }
        executor = null;
    }

    public void clear() {
        ApplicationManager.getApplication().invokeLater(() -> {
            logPane.setText("");
            lastStatus.set("");
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