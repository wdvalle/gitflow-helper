package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.cicd.JenkinsConnector;
import br.com.gitflowhelper.dialog.ConfigDialog;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Root path of the Git repository currently selected in the toolbar combo.
     * {@code null} means "use the first available entry".
     */
    @Nullable
    private String selectedRepoPath = null;

    public CIDataToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        logPane = new JBHtmlEditorPane();
        logPane.setEditable(false);
        logPane.setFocusable(false);
        updateEmptyText();

        add(new JBScrollPane(logPane), BorderLayout.CENTER);

        project.getMessageBus().connect(this).subscribe(GitFlowSettingsListener.TOPIC, this::updateEmptyText);
    }

    // -----------------------------------------------------------------------
    // Repository selection (called by the toolbar combo)
    // -----------------------------------------------------------------------

    /**
     * Sets the repository whose CI/CD server will be monitored.
     * Stops any running monitor when the selection changes.
     *
     * @param repoPath absolute root path of the repository, or {@code null} to use the first.
     */
    public void setSelectedRepoPath(@Nullable String repoPath) {
        if (!java.util.Objects.equals(this.selectedRepoPath, repoPath)) {
            stopMonitoring();
            this.selectedRepoPath = repoPath;
            updateEmptyText();
        }
    }

    @Nullable
    public String getSelectedRepoPath() {
        return selectedRepoPath;
    }

    /**
     * Resolves the {@link CiServerConfig} for the currently selected repository.
     * Falls back to the first configured entry if the path is not found.
     */
    @Nullable
    private CiServerConfig resolveConfig() {
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        if (selectedRepoPath != null) {
            RepoCiEntry entry = svc.getRepoCiEntry(selectedRepoPath);
            if (entry != null) return entry.ciServer;
        }
        // Fall back to first available
        java.util.List<RepoCiEntry> entries = svc.getRepoCiEntries();
        return entries.isEmpty() ? null : entries.get(0).ciServer;
    }

    // -----------------------------------------------------------------------
    // Monitoring
    // -----------------------------------------------------------------------

    public void startMonitoring() {
        CiServerConfig cfg = resolveConfig();
        if (cfg == null || !cfg.isActive()) {
            appendLog("CI/CD integration is disabled — configure a server URL first.");
            updateEmptyText();
            return;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        clear();
        appendLog("Starting CI/CD monitoring (" + cfg.getCiType() + ") ...");
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkBuildStatus, 0, 5, TimeUnit.SECONDS);
    }

    private void checkBuildStatus() {
        CiServerConfig cfg = resolveConfig();
        if (cfg == null || !cfg.isActive()) {
            stopMonitoring();
            appendLog("CI/CD integration disabled. Stopping monitor.");
            return;
        }

        String jobName = project.getName();
        String status;
        if ("Jenkins".equals(cfg.getCiType())) {
            JenkinsConnector connector = new JenkinsConnector(cfg.getCiUrl(), cfg.getCiToken());
            status = connector.getBuildStatus(jobName);
        } else {
            status = cfg.getCiType() + " not yet supported.";
        }

        String previous = lastStatus.get();
        if (!status.equals(previous)) {
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
                int bodyEnd   = currentText.lastIndexOf("</body>");
                if (bodyEnd > bodyStart) body = currentText.substring(bodyStart, bodyEnd);
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
        CiServerConfig cfg = resolveConfig();
        StatusText emptyText = logPane.getEmptyText();
        emptyText.clear();
        if (cfg == null || !cfg.isActive()) {
            emptyText.setText("CI/CD integration is disabled.");
            emptyText.appendLine("Configure a server URL in Git Flow Helper settings",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> new ConfigDialog(project).show());
        } else {
            emptyText.setText("No CI/CD data to display.");
            emptyText.appendLine("Click the 'play' button to start monitoring.");
        }
    }

    @Override
    public void dispose() {
        stopMonitoring();
    }

    // -----------------------------------------------------------------------
    // Inner – HTML pane with empty-text support
    // -----------------------------------------------------------------------

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
        public @NotNull StatusText getEmptyText() { return emptyText; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            emptyText.paint(this, g);
        }
    }
}