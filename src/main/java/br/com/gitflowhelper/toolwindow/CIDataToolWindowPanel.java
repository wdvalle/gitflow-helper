package br.com.gitflowhelper.toolwindow;

import br.com.gitflow.cicd.CiConnector;
import br.com.gitflow.cicd.JenkinsConnector;
import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflowhelper.dialog.ConfigDialog;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import br.com.gitflowhelper.toolwindow.ci.RepoCiDashboardPanel;
import br.com.gitflowhelper.util.NotificationUtil;
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
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
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

    private final Map<String, RepoCiDashboardPanel> repoDashboards = new ConcurrentHashMap<>();
    private final Map<String, Component> repoTabComponents = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> repoExecutors = new ConcurrentHashMap<>();
    private final Map<String, CiConnector> repoConnectors = new ConcurrentHashMap<>();

    private Runnable onStopped;
    private Runnable onNewContent;

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

        // Initialize tabs based on active CI configuration
        syncTabsWithSettings();

        project.getMessageBus().connect(this).subscribe(GitFlowSettingsListener.TOPIC, this::syncTabsWithSettings);
    }

    /**
     * Synchronizes tabs with the current CI/CD settings:
     * - If no CI/CD is configured, displays the empty state message.
     * - If at least one CI/CD is configured, displays a tab for each configured repo in single-panel mode.
     */
    public void syncTabsWithSettings() {
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        List<RepoCiEntry> configuredEntries = new ArrayList<>();
        for (RepoCiEntry entry : svc.getRepoCiEntries()) {
            if (entry.ciServer != null && entry.ciServer.isActive()) {
                configuredEntries.add(entry);
            }
        }

        if (configuredEntries.isEmpty()) {
            stopAllMonitoring();
            updateEmptyText();
            cardLayout.show(mainContainer, CARD_EMPTY);
            return;
        }

        // Show tabs for each configured CI repository
        for (RepoCiEntry entry : configuredEntries) {
            RepoCiDashboardPanel dashboard = getOrCreateTab(entry.repoPath);
            dashboard.setPlatformName(entry.ciServer.getCiType());

            // If not actively monitoring/executing, keep in single panel mode
            if (!repoExecutors.containsKey(entry.repoPath)) {
                dashboard.setSplitMode(false);
            }

            loadInitialPipelineRunIfPossible(entry.repoPath, entry.ciServer);
        }

        // Remove unconfigured tabs if they are not actively executing
        for (String existingRepoPath : new ArrayList<>(repoDashboards.keySet())) {
            boolean stillConfigured = configuredEntries.stream().anyMatch(e -> e.repoPath.equals(existingRepoPath));
            if (!stillConfigured && !repoExecutors.containsKey(existingRepoPath)) {
                removeTab(existingRepoPath);
            }
        }

        // Select the active/target tab
        String targetPath = selectedRepoPath;
        if (targetPath == null || !repoDashboards.containsKey(targetPath)) {
            targetPath = configuredEntries.get(0).repoPath;
            selectedRepoPath = targetPath;
        }
        Component comp = repoTabComponents.get(targetPath);
        if (comp != null) {
            isProgrammaticTabChange = true;
            try {
                tabbedPane.setSelectedComponent(comp);
            } finally {
                isProgrammaticTabChange = false;
            }
        }

        cardLayout.show(mainContainer, CARD_TABS);
    }

    private void loadInitialPipelineRunIfPossible(String repoPath, CiServerConfig cfg) {
        if (cfg == null || !cfg.isActive() || !"Jenkins".equals(cfg.getCiType())) return;
        RepoCiDashboardPanel dashboard = repoDashboards.get(repoPath);
        if (dashboard == null) return;

        // Skip background query if actively executing
        if (repoExecutors.containsKey(repoPath)) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String token = GitFlowSettingsService.getInstance(project).getTokenForRepo(repoPath);
                JenkinsConnector connector = new JenkinsConnector(
                        cfg.getCiUrl(),
                        cfg.getCiLogin(),
                        token != null ? token : ""
                );
                PipelineRun run = connector.fetchPipelineRun();
                if (run != null) {
                    GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
                    for (GitRepository repo : repoManager.getRepositories()) {
                        if (repo.getRoot().getPath().equals(repoPath)) {
                            run.setBranch(repo.getCurrentBranchName());
                            break;
                        }
                    }
                    dashboard.updatePipelineRun(run, connector.getPlatformName());
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void removeTab(String repoPath) {
        Component comp = repoTabComponents.remove(repoPath);
        RepoCiDashboardPanel dashboard = repoDashboards.remove(repoPath);
        if (comp != null) {
            isProgrammaticTabChange = true;
            try {
                tabbedPane.remove(comp);
            } finally {
                isProgrammaticTabChange = false;
            }
        }
        if (dashboard != null) {
            dashboard.dispose();
        }
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
            if (repoPath != null && repoTabComponents.containsKey(repoPath)) {
                Component comp = repoTabComponents.get(repoPath);
                isProgrammaticTabChange = true;
                try {
                    tabbedPane.setSelectedComponent(comp);
                } finally {
                    isProgrammaticTabChange = false;
                }
            } else {
                syncTabsWithSettings();
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

    private RepoCiDashboardPanel getOrCreateTab(String repoPath) {
        if (repoDashboards.containsKey(repoPath)) {
            Component comp = repoTabComponents.get(repoPath);
            isProgrammaticTabChange = true;
            try {
                tabbedPane.setSelectedComponent(comp);
            } finally {
                isProgrammaticTabChange = false;
            }
            return repoDashboards.get(repoPath);
        }

        RepoCiDashboardPanel dashboard = new RepoCiDashboardPanel(project, repoPath);
        CiServerConfig cfg = resolveConfigForRepo(repoPath);
        if (cfg != null && cfg.getCiType() != null) {
            dashboard.setPlatformName(cfg.getCiType());
        }

        dashboard.setCallbacks(
                () -> triggerBuildAndMonitor(repoPath),
                () -> stopMonitoring(repoPath),
                () -> markTabWithNewContent(repoPath)
        );

        String tabLabel = getRepoName(repoPath);

        repoDashboards.put(repoPath, dashboard);
        repoTabComponents.put(repoPath, dashboard);

        isProgrammaticTabChange = true;
        try {
            tabbedPane.addTab(tabLabel, dashboard);
            tabbedPane.setSelectedComponent(dashboard);
        } finally {
            isProgrammaticTabChange = false;
        }

        cardLayout.show(mainContainer, CARD_TABS);
        return dashboard;
    }

    private void markTabWithNewContent(String repoPath) {
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
    }

    // -----------------------------------------------------------------------
    // Trigger Build & Monitoring
    // -----------------------------------------------------------------------

    public static void startMonitoringForRepo(@NotNull Project project, @NotNull String repoPath) {
        ApplicationManager.getApplication().invokeLater(() -> {
            com.intellij.openapi.wm.ToolWindow toolWindow =
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitFlow");
            if (toolWindow != null) {
                toolWindow.show();
                com.intellij.ui.content.Content content = toolWindow.getContentManager().findContent("CI/CD");
                if (content != null) {
                    toolWindow.getContentManager().setSelectedContent(content);
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

    public void triggerBuildAndMonitor() {
        triggerBuildAndMonitor(resolveRepoPath());
    }

    /**
     * Remotely triggers a build on the configured CI server and starts real-time monitoring.
     * Equivalent to the "Run now" action in CI/CD settings.
     */
    public void triggerBuildAndMonitor(@Nullable String path) {
        if (path == null) {
            path = resolveRepoPath();
        }
        if (path == null) {
            syncTabsWithSettings();
            return;
        }

        CiServerConfig cfg = resolveConfigForRepo(path);
        if (cfg == null || !cfg.isActive()) {
            RepoCiDashboardPanel dashboard = getOrCreateTab(path);
            dashboard.setSplitMode(true);
            dashboard.appendPluginLog("CI/CD integration is disabled — configure a server URL first.");
            NotificationUtil.showGitFlowWarningNotification(project, "CI/CD", "CI/CD integration is disabled. Configure a server URL first.");
            return;
        }

        final String repoPath = path;
        stopMonitoring(repoPath);

        RepoCiDashboardPanel dashboard = getOrCreateTab(repoPath);
        dashboard.clear();
        dashboard.setPlatformName(cfg.getCiType());
        dashboard.setSplitMode(true);
        dashboard.appendPluginLog("Triggering build on " + cfg.getCiType() + "...");

        if ("Jenkins".equals(cfg.getCiType())) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    String token = GitFlowSettingsService.getInstance(project).getTokenForRepo(repoPath);
                    JenkinsConnector jenkinsConnector = new JenkinsConnector(
                            cfg.getCiUrl(),
                            cfg.getCiLogin(),
                            token != null ? token : ""
                    );
                    repoConnectors.put(repoPath, jenkinsConnector);

                    jenkinsConnector.triggerBuild();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        dashboard.appendPluginLog("Build triggered successfully on " + cfg.getCiType() + ". Monitoring execution...");
                        NotificationUtil.showGitFlowSuccessNotification(project, "CI/CD", "Build triggered successfully on " + cfg.getCiType() + ".");
                    });

                    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                    repoExecutors.put(repoPath, executor);
                    executor.scheduleWithFixedDelay(() -> checkBuildStatus(repoPath), 0, 2, TimeUnit.SECONDS);

                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        dashboard.appendPluginLog("Failed to trigger build: " + ex.getMessage());
                        NotificationUtil.showGitFlowErrorNotification(project, "CI/CD Error", "Failed to trigger build: " + ex.getMessage());
                        stopMonitoring(repoPath);
                    });
                }
            });
        } else {
            dashboard.appendPluginLog(cfg.getCiType() + " is not yet supported.");
            stopMonitoring(repoPath);
        }
    }

    public void startMonitoring() {
        startMonitoring(resolveRepoPath());
    }

    public void startMonitoring(String path) {
        if (path == null) {
            syncTabsWithSettings();
            return;
        }
        CiServerConfig cfg = resolveConfigForRepo(path);
        if (cfg == null || !cfg.isActive()) {
            RepoCiDashboardPanel dashboard = getOrCreateTab(path);
            dashboard.setSplitMode(true);
            dashboard.appendPluginLog("CI/CD integration is disabled — configure a server URL first.");
            return;
        }

        stopMonitoring(path);

        RepoCiDashboardPanel dashboard = getOrCreateTab(path);
        dashboard.clear();
        dashboard.setPlatformName(cfg.getCiType());
        // Switch to two-panel (split view) upon execution
        dashboard.setSplitMode(true);
        dashboard.appendPluginLog("Starting CI/CD monitoring...");

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
            dashboard.appendPluginLog(cfg.getCiType() + " is not yet supported.");
            stopMonitoring(path);
        }
    }

    private void checkBuildStatus(String repoPath) {
        CiServerConfig cfg = resolveConfigForRepo(repoPath);
        if (cfg == null || !cfg.isActive()) {
            stopMonitoring(repoPath);
            appendPluginLog(repoPath, "CI/CD integration is disabled. Stopping monitoring.");
            return;
        }

        CiConnector connector = repoConnectors.get(repoPath);
        RepoCiDashboardPanel dashboard = repoDashboards.get(repoPath);

        if (connector != null && dashboard != null) {
            // Update pipeline run state (stages, steps, status, duration)
            PipelineRun run = connector.fetchPipelineRun();
            if (run != null) {
                // Populate current Git branch name if available
                GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
                for (GitRepository repo : repoManager.getRepositories()) {
                    if (repo.getRoot().getPath().equals(repoPath)) {
                        run.setBranch(repo.getCurrentBranchName());
                        break;
                    }
                }
                dashboard.updatePipelineRun(run, connector.getPlatformName());
            }

            // Fetch progressive logs
            String chunk = connector.fetchNextChunk();
            if (!chunk.isEmpty()) {
                dashboard.appendConsoleLog(chunk);
            }

            // Stop when connector signals no more data (build finished or error)
            if (!connector.hasMoreData()) {
                stopMonitoring(repoPath);
            }
        } else {
            appendPluginLog(repoPath, cfg.getCiType() + " is not yet supported.");
            stopMonitoring(repoPath);
        }
    }

    private void appendPluginLog(String repoPath, String text) {
        RepoCiDashboardPanel dashboard = repoDashboards.get(repoPath);
        if (dashboard != null) {
            dashboard.appendPluginLog(text);
        }
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
        CiConnector connector = repoConnectors.remove(repoPath);
        if (connector != null) {
            connector.stop();
        }
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
        for (CiConnector connector : repoConnectors.values()) {
            if (connector != null) {
                connector.stop();
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
            for (RepoCiDashboardPanel dashboard : repoDashboards.values()) {
                dashboard.clear();
                dashboard.setSplitMode(false);
            }
            syncTabsWithSettings();
        });
    }

    private void updateEmptyText() {
        GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
        boolean hasActiveConfig = svc.isIntegrateWithCI();

        StatusText emptyText = emptyLogPane.getEmptyText();
        emptyText.clear();
        if (!hasActiveConfig) {
            emptyText.setText("CI/CD integration is not configured.");
            emptyText.appendLine("Configure a server URL in Git Flow Helper settings to view pipelines",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> new ConfigDialog(project).show());
        } else {
            emptyText.setText("No CI/CD data to display.");
            emptyText.appendLine("Monitoring will display pipeline stages, steps, and log output when a build starts.");
            emptyText.appendLine("Configure CI/CD settings",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> new ConfigDialog(project).show());
        }
    }

    @Override
    public void dispose() {
        stopAllMonitoring();
        for (RepoCiDashboardPanel dashboard : repoDashboards.values()) {
            dashboard.dispose();
        }
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
