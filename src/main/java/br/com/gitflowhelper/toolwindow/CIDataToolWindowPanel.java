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
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CIDataToolWindowPanel extends JPanel implements Disposable {

    private final Project project;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer;
    private final JBTabbedPane tabbedPane = new JBTabbedPane();
    private final JBHtmlEditorPane emptyLogPane;

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_TABS = "TABS";

    private final Map<String, JBHtmlEditorPane> repoLogPanes = new ConcurrentHashMap<>();
    private final Map<String, Component> repoTabComponents = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> repoExecutors = new ConcurrentHashMap<>();
    private final Map<String, JenkinsConnector> repoConnectors = new ConcurrentHashMap<>();

    private Runnable onStopped;
    private Runnable onNewContent;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private boolean isProgrammaticTabChange = false;

    /**
     * Root path of the Git repository currently selected in the toolbar combo.
     * {@code null} means "use the first available entry".
     */
    @Nullable
    private String selectedRepoPath = null;

    public CIDataToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        mainContainer = new JPanel(cardLayout);

        // Empty state pane
        emptyLogPane = createHtmlEditorPane();
        updateEmptyText();
        mainContainer.add(new JBScrollPane(emptyLogPane), CARD_EMPTY);

        // Tabs pane
        mainContainer.add(tabbedPane, CARD_TABS);

        add(mainContainer, BorderLayout.CENTER);
        cardLayout.show(mainContainer, CARD_EMPTY);

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (isProgrammaticTabChange) return;
                int selectedIndex = tabbedPane.getSelectedIndex();
                if (selectedIndex >= 0) {
                    Component selectedComp = tabbedPane.getComponentAt(selectedIndex);
                    String title = tabbedPane.getTitleAt(selectedIndex);
                    if (title != null && title.endsWith(" •")) {
                        tabbedPane.setTitleAt(selectedIndex, title.substring(0, title.length() - 2));
                    }
                    for (Map.Entry<String, Component> entry : repoTabComponents.entrySet()) {
                        if (entry.getValue() == selectedComp) {
                            selectedRepoPath = entry.getKey();
                            break;
                        }
                    }
                }
            }
        });

        project.getMessageBus().connect(this).subscribe(GitFlowSettingsListener.TOPIC, this::updateEmptyText);
    }

    private JBHtmlEditorPane createHtmlEditorPane() {
        JBHtmlEditorPane pane = new JBHtmlEditorPane();
        pane.setEditable(false);
        pane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                pane.getCaret().setVisible(false);
            }
        });
        return pane;
    }

    // -----------------------------------------------------------------------
    // Repository selection (called by the toolbar combo)
    // -----------------------------------------------------------------------

    /**
     * Sets the repository whose CI/CD server will be monitored.
     * Selects the tab if it already exists for the repo.
     *
     * @param repoPath absolute root path of the repository, or {@code null} to use the first.
     */
    public void setSelectedRepoPath(@Nullable String repoPath) {
        if (!Objects.equals(this.selectedRepoPath, repoPath)) {
            this.selectedRepoPath = repoPath;
            updateEmptyText();
            if (repoPath != null && repoTabComponents.containsKey(repoPath)) {
                Component comp = repoTabComponents.get(repoPath);
                isProgrammaticTabChange = true;
                try {
                    tabbedPane.setSelectedComponent(comp);
                } finally {
                    isProgrammaticTabChange = false;
                }
            }
        }
    }

    @Nullable
    public String getSelectedRepoPath() {
        return selectedRepoPath;
    }

    @Nullable
    private String resolveRepoPath() {
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        if (selectedRepoPath != null) {
            RepoCiEntry entry = svc.getRepoCiEntry(selectedRepoPath);
            if (entry != null && entry.ciServer != null && entry.ciServer.isActive()) {
                return selectedRepoPath;
            }
        }
        for (RepoCiEntry entry : svc.getRepoCiEntries()) {
            if (entry.ciServer != null && entry.ciServer.isActive()) {
                return entry.repoPath;
            }
        }
        List<RepoCiEntry> entries = svc.getRepoCiEntries();
        return entries.isEmpty() ? selectedRepoPath : entries.get(0).repoPath;
    }

    @Nullable
    private CiServerConfig resolveConfig() {
        return resolveConfigForRepo(resolveRepoPath());
    }

    @Nullable
    private CiServerConfig resolveConfigForRepo(@Nullable String repoPath) {
        if (repoPath == null) return null;
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        RepoCiEntry entry = svc.getRepoCiEntry(repoPath);
        return entry != null ? entry.ciServer : null;
    }

    private String getRepoName(String repoPath) {
        if (repoPath == null) return "CI/CD";
        List<GitRepository> repos = GitRepositoryManager.getInstance(project).getRepositories();
        for (GitRepository repo : repos) {
            if (repo.getRoot().getPath().equals(repoPath)) {
                return repo.getRoot().getName();
            }
        }
        return new File(repoPath).getName();
    }

    private JBHtmlEditorPane getOrCreateTab(String repoPath) {
        if (repoLogPanes.containsKey(repoPath)) {
            Component comp = repoTabComponents.get(repoPath);
            isProgrammaticTabChange = true;
            try {
                tabbedPane.setSelectedComponent(comp);
            } finally {
                isProgrammaticTabChange = false;
            }
            return repoLogPanes.get(repoPath);
        }

        JBHtmlEditorPane logPane = createHtmlEditorPane();
        JBScrollPane scrollPane = new JBScrollPane(logPane);
        String tabLabel = getRepoName(repoPath);

        repoLogPanes.put(repoPath, logPane);
        repoTabComponents.put(repoPath, scrollPane);

        isProgrammaticTabChange = true;
        try {
            tabbedPane.addTab(tabLabel, scrollPane);
            tabbedPane.setSelectedComponent(scrollPane);
        } finally {
            isProgrammaticTabChange = false;
        }

        cardLayout.show(mainContainer, CARD_TABS);
        return logPane;
    }

    // -----------------------------------------------------------------------
    // Monitoring
    // -----------------------------------------------------------------------

    public static void startMonitoringForRepo(@NotNull Project project, @NotNull String repoPath) {
        ApplicationManager.getApplication().invokeLater(() -> {
            com.intellij.openapi.wm.ToolWindow toolWindow =
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitFlow");
            if (toolWindow != null) {
                com.intellij.ui.content.Content content = toolWindow.getContentManager().findContent("CI/CD");
                if (content != null) {
                    CIDataToolWindowPanel panel = findCIDataPanel(content.getComponent());
                    if (panel != null) {
                        panel.startMonitoring(repoPath);
                    }
                }
            }
        });
    }

    private static CIDataToolWindowPanel findCIDataPanel(Component comp) {
        if (comp instanceof CIDataToolWindowPanel) {
            return (CIDataToolWindowPanel) comp;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                CIDataToolWindowPanel found = findCIDataPanel(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public void startMonitoring() {
        startMonitoring(resolveRepoPath());
    }

    public void startMonitoring(String path) {
        if (path == null) {
            updateEmptyText();
            return;
        }
        CiServerConfig cfg = resolveConfigForRepo(path);
        if (cfg == null || !cfg.isActive()) {
            JBHtmlEditorPane pane = getOrCreateTab(path);
            appendPluginLog(path, "CI/CD integration is disabled — configure a server URL first.");
            updateEmptyText();
            return;
        }

        stopMonitoring(path);

        JBHtmlEditorPane logPane = getOrCreateTab(path);
        logPane.setText("");
        appendPluginLog(path, "Starting CI/CD monitoring...");

        if ("Jenkins".equals(cfg.getCiType())) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String token = GitFlowSettingsService.getInstance(project).getTokenForRepo(path);
                JenkinsConnector jenkinsConnector = new JenkinsConnector(
                        cfg.getCiUrl(),
                        cfg.getCiLogin(),
                        token != null ? token : ""
                );
                repoConnectors.put(path, jenkinsConnector);

                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                repoExecutors.put(path, executor);
                executor.scheduleWithFixedDelay(() -> checkBuildStatus(path), 0, 2, TimeUnit.SECONDS);
            });
        } else {
            repoConnectors.remove(path);
            appendPluginLog(path, cfg.getCiType() + " not yet supported.");
            stopMonitoring(path);
        }
    }

    private void checkBuildStatus(String repoPath) {
        CiServerConfig cfg = resolveConfigForRepo(repoPath);
        if (cfg == null || !cfg.isActive()) {
            stopMonitoring(repoPath);
            appendPluginLog(repoPath, "CI/CD integration disabled. Stopping monitor.");
            return;
        }

        JenkinsConnector connector = repoConnectors.get(repoPath);
        if (connector != null) {
            String chunk = connector.fetchNextChunk();

            if (!chunk.isEmpty()) {
                appendLog(repoPath, chunk);
            }

            // Stop when Jenkins signals no more data (build finished or error)
            if (!connector.hasMoreData()) {
                stopMonitoring(repoPath);
            }
        } else {
            appendPluginLog(repoPath, cfg.getCiType() + " not yet supported.");
            stopMonitoring(repoPath);
        }
    }

    private void appendPluginLog(String repoPath, String text) {
        appendLog(repoPath, text, true);
    }

    private void appendLog(String repoPath, String text) {
        appendLog(repoPath, text, false);
    }

    private void appendLog(String repoPath, String content, boolean isPluginLog) {
        ApplicationManager.getApplication().invokeLater(() -> {
            JBHtmlEditorPane logPane = repoLogPanes.get(repoPath);
            if (logPane == null) return;

            String timestamp = dtf.format(LocalDateTime.now());
            String toAppend;
            if (isPluginLog) {
                toAppend = "<font color='#FFFFFF'>" + timestamp + ": " + content + "</font><br>";
            } else {
                toAppend = content;
            }

            try {
                HTMLDocument doc = (HTMLDocument) logPane.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) logPane.getEditorKit();
                Element body = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
                if (body != null) {
                    kit.insertHTML(doc, body.getEndOffset() - 1, toAppend, 0, 0, null);
                } else {
                    kit.insertHTML(doc, doc.getLength(), toAppend, 0, 0, null);
                }
            } catch (Exception e) {
                // Fallback in case of unexpected HTML document structure issue
                String currentText = logPane.getText();
                String body = "";
                if (currentText != null && currentText.contains("<body>")) {
                    int bodyStart = currentText.indexOf("<body>") + 6;
                    int bodyEnd   = currentText.lastIndexOf("</body>");
                    if (bodyEnd > bodyStart) body = currentText.substring(bodyStart, bodyEnd);
                }
                logPane.setText(body + toAppend);
            }

            logPane.setCaretPosition(logPane.getDocument().getLength());

            Component comp = repoTabComponents.get(repoPath);
            if (comp != null) {
                int idx = tabbedPane.indexOfComponent(comp);
                if (idx >= 0 && tabbedPane.getSelectedIndex() != idx) {
                    String currentTitle = tabbedPane.getTitleAt(idx);
                    if (currentTitle != null && !currentTitle.endsWith(" •")) {
                        tabbedPane.setTitleAt(idx, currentTitle + " •");
                    }
                }
            }

            if (onNewContent != null) {
                onNewContent.run();
            }
        });
    }

    /** Returns true if monitoring is currently active for any repository. */
    public boolean isMonitoringActive() {
        for (ScheduledExecutorService exec : repoExecutors.values()) {
            if (exec != null && !exec.isShutdown()) {
                return true;
            }
        }
        return false;
    }

    /** Registers a callback invoked on the EDT whenever monitoring stops. */
    public void setOnStopped(Runnable onStopped) {
        this.onStopped = onStopped;
    }

    /** Registers a callback invoked on the EDT whenever new log content is appended. */
    public void setOnNewContent(Runnable onNewContent) {
        this.onNewContent = onNewContent;
    }

    public void stopMonitoring() {
        String path = resolveRepoPath();
        if (path != null) {
            stopMonitoring(path);
        } else {
            stopAllMonitoring();
        }
    }

    public void stopMonitoring(String repoPath) {
        ScheduledExecutorService exec = repoExecutors.remove(repoPath);
        if (exec != null && !exec.isShutdown()) {
            exec.shutdown();
            appendPluginLog(repoPath, "CI/CD monitoring stopped.");
        }
        repoConnectors.remove(repoPath);
        if (onStopped != null) {
            ApplicationManager.getApplication().invokeLater(onStopped);
        }
    }

    public void stopAllMonitoring() {
        for (String path : repoExecutors.keySet()) {
            ScheduledExecutorService exec = repoExecutors.remove(path);
            if (exec != null && !exec.isShutdown()) {
                exec.shutdown();
            }
        }
        repoExecutors.clear();
        repoConnectors.clear();
        if (onStopped != null) {
            ApplicationManager.getApplication().invokeLater(onStopped);
        }
    }

    public void clear() {
        ApplicationManager.getApplication().invokeLater(() -> {
            stopAllMonitoring();
            isProgrammaticTabChange = true;
            try {
                tabbedPane.removeAll();
            } finally {
                isProgrammaticTabChange = false;
            }
            repoLogPanes.clear();
            repoTabComponents.clear();
            cardLayout.show(mainContainer, CARD_EMPTY);
        });
    }

    private void updateEmptyText() {
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        boolean hasActiveConfig = svc.isIntegrateWithCI();

        StatusText emptyText = emptyLogPane.getEmptyText();
        emptyText.clear();
        if (!hasActiveConfig) {
            emptyText.setText("CI/CD integration is disabled.");
            emptyText.appendLine("Configure a server URL in Git Flow Helper settings",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> new ConfigDialog(project).show());
        } else {
            emptyText.setText("No CI/CD data to display.");
            emptyText.appendLine("Monitoring will display log output when a build starts.");
            emptyText.appendLine("Configure CI/CD settings",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> new ConfigDialog(project).show());
        }
    }

    @Override
    public void dispose() {
        stopAllMonitoring();
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