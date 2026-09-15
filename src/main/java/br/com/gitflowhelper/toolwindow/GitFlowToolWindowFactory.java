package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import br.com.gitflowhelper.statusbar.GitFlowGuideManager;
import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.concurrency.AppExecutorUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GitFlowToolWindowFactory implements ToolWindowFactory {

    public static final String GOT_IT_LOGS_TAB_ID = "gitflow.tab.logs.guide.v2.9.0";
    public static final String GOT_IT_ISSUES_TAB_ID = "gitflow.tab.issues.guide.v2.9.0";
    public static final String GOT_IT_FLOW_TAB_ID = "gitflow.tab.flow.guide.v2.9.0";
    public static final String GOT_IT_CICD_TAB_ID = "gitflow.tab.cicd.guide.v2.9.0";

    /** Backwards-compatible alias for the Flow tab tooltip */
    public static final String GOT_IT_FLOW_REDESIGN_ID = GOT_IT_FLOW_TAB_ID;

    private static final AtomicBoolean TABS_TOUR_ACTIVE = new AtomicBoolean(false);

    public static void resetTourState() {
        TABS_TOUR_ACTIVE.set(false);
    }

    public static boolean isInitialGuidePending() {
        return GitFlowGuideManager.isInitialGuidePending();
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // Logs tab
        ToolWindowPanel logsPanel = new ToolWindowPanel(project);
        Content logsContent = contentFactory.createContent(logsPanel, "Logs", false);
        toolWindow.getContentManager().addContent(logsContent);
        logsPanel.setOnNewContent(() -> notifyNewContent(toolWindow, logsContent, "Logs"));

        // Issues tab
        TasksToolWindowPanel tasksPanel = new TasksToolWindowPanel(project);
        Content tasksContent = contentFactory.createContent(tasksPanel, "Issues", false);
        tasksContent.setDisposer(tasksPanel);
        toolWindow.getContentManager().addContent(tasksContent);
        tasksPanel.setOnNewContent(() -> notifyNewContent(toolWindow, tasksContent, "Issues"));

        // Flow tab
        GitFlowGraphPanel flowPanel = new GitFlowGraphPanel(project);
        Content flowContent = contentFactory.createContent(flowPanel, "Flow", false);
        toolWindow.getContentManager().addContent(flowContent);
        flowPanel.setOnNewContent(() -> notifyNewContent(toolWindow, flowContent, "Flow"));

        // CI/CD tab
        CIDataToolWindowPanel ciDataPanel = new CIDataToolWindowPanel(project);
        Content ciDataContent = contentFactory.createContent(
                createCIContent(project, ciDataPanel), "CI/CD", false);
        ciDataContent.setDisposer(ciDataPanel);
        toolWindow.getContentManager().addContent(ciDataContent);
        ciDataPanel.setOnNewContent(() -> notifyNewContent(toolWindow, ciDataContent, "CI/CD"));

        // Listen for tab selection changes
        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
                    Content selected = event.getContent();
                    if (selected != null) {
                        String displayName = selected.getDisplayName();
                        if (displayName != null && displayName.endsWith(" \u2022")) {
                            selected.setDisplayName(displayName.substring(0, displayName.length() - 2));
                        }
                        if (selected == logsContent) {
                            PluginUtils.clearLiveIndicator(toolWindow);
                        }
                        checkAndShowTabsTour(toolWindow, logsContent, tasksContent, flowContent, ciDataContent, false);
                    }
                }
            }
        });

        // Listen for tool window show / state change to clear live indicator and show tabs tour
        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(
                ToolWindowManagerListener.TOPIC,
                new ToolWindowManagerListener() {
                    @Override
                    public void toolWindowShown(@NotNull ToolWindow tw) {
                        if ("GitFlow".equals(tw.getId())) {
                            checkAndClearLiveIcon(tw, logsContent);
                            checkAndShowTabsTour(tw, logsContent, tasksContent, flowContent, ciDataContent, false);
                        }
                    }

                    @Override
                    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
                        ToolWindow tw = toolWindowManager.getToolWindow("GitFlow");
                        if (tw != null && tw.isVisible()) {
                            checkAndClearLiveIcon(tw, logsContent);
                            checkAndShowTabsTour(tw, logsContent, tasksContent, flowContent, ciDataContent, false);
                        }
                    }
                }
        );

        // Detect when logsPanel becomes visible on screen
        logsPanel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && logsPanel.isShowing()) {
                checkAndClearLiveIcon(toolWindow, logsContent);
                checkAndShowTabsTour(toolWindow, logsContent, tasksContent, flowContent, ciDataContent, false);
            }
        });

        // Detect when the tool window component itself becomes visible on screen
        toolWindow.getComponent().addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && toolWindow.getComponent().isShowing()) {
                checkAndShowTabsTour(toolWindow, logsContent, tasksContent, flowContent, ciDataContent, false);
            }
        });

        if (toolWindow.isVisible()) {
            checkAndClearLiveIcon(toolWindow, logsContent);
            checkAndShowTabsTour(toolWindow, logsContent, tasksContent, flowContent, ciDataContent, false);
        }
    }

    /**
     * Checks if a GotItTooltip for the given ID is eligible to be shown.
     */
    public static boolean canShowTooltip(@NotNull String id) {
        if (ApplicationManager.getApplication() == null) return false;
        PropertiesComponent props = PropertiesComponent.getInstance();
        if (props == null) return false;
        return props.getInt("got.it.tooltip." + id, 0) < 1;
    }

    /**
     * Triggers the tabs onboarding tour if the GitFlow tool window is currently visible.
     */
    public static void triggerTabsTourIfVisible(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GitFlow");
        if (toolWindow != null && toolWindow.isVisible()) {
            checkAndShowTabsTour(toolWindow, null, null, null, null, true);
        }
    }

    /**
     * Backwards-compatible alias for triggerTabsTourIfVisible.
     */
    public static void triggerFlowGotItIfVisible(@NotNull Project project) {
        triggerTabsTourIfVisible(project);
    }

    public static void checkAndShowTabsTour(
            @NotNull ToolWindow toolWindow,
            @Nullable Content logsContent,
            @Nullable Content tasksContent,
            @Nullable Content flowContent,
            @Nullable Content ciDataContent
    ) {
        checkAndShowTabsTour(toolWindow, logsContent, tasksContent, flowContent, ciDataContent, false);
    }

    /**
     * Guides the user through each tool window tab in sequential order:
     * Logs -> Issues -> Flow -> CI/CD.
     */
    public static void checkAndShowTabsTour(
            @NotNull ToolWindow toolWindow,
            @Nullable Content logsContent,
            @Nullable Content tasksContent,
            @Nullable Content flowContent,
            @Nullable Content ciDataContent,
            boolean force
    ) {
        if (toolWindow.isDisposed() || !toolWindow.isVisible()) return;

        // If not forced, do not start tabs tour while the initial status bar / stripe guides are pending
        // or if a tabs tour sequence is already currently active.
        if (!force && (isInitialGuidePending() || TABS_TOUR_ACTIVE.get())) {
            return;
        }

        // If none of the tab tooltips can be shown, do nothing
        if (!canShowTooltip(GOT_IT_LOGS_TAB_ID) &&
            !canShowTooltip(GOT_IT_ISSUES_TAB_ID) &&
            !canShowTooltip(GOT_IT_FLOW_TAB_ID) &&
            !canShowTooltip(GOT_IT_CICD_TAB_ID)) {
            TABS_TOUR_ACTIVE.set(false);
            return;
        }

        ContentManager cm = toolWindow.getContentManager();
        Content logs = logsContent != null ? logsContent : findContentByPrefix(cm, "Logs");
        Content tasks = tasksContent != null ? tasksContent : findContentByPrefix(cm, "Issues");
        Content flow = flowContent != null ? flowContent : findContentByPrefix(cm, "Flow");
        Content cicd = ciDataContent != null ? ciDataContent : findContentByPrefix(cm, "CI/CD");

        if (logs == null || tasks == null || flow == null || cicd == null) return;

        TABS_TOUR_ACTIVE.set(true);

        Runnable showCiCd = () -> {
            if (!canShowTooltip(GOT_IT_CICD_TAB_ID)) {
                TABS_TOUR_ACTIVE.set(false);
                return;
            }
            cm.setSelectedContent(cicd);
            showTabGotIt(
                    toolWindow, cicd, "CI/CD", GOT_IT_CICD_TAB_ID,
                    "CI/CD Tab",
                    "Monitors and manages continuous integration pipelines directly within the IDE. Track live build statuses, inspect pipeline runs, and restart jobs seamlessly.",
                    "Got It",
                    null
            );
        };

        Runnable showFlow = () -> {
            if (!canShowTooltip(GOT_IT_FLOW_TAB_ID)) {
                showCiCd.run();
                return;
            }
            cm.setSelectedContent(flow);
            showTabGotIt(
                    toolWindow, flow, "Flow", GOT_IT_FLOW_TAB_ID,
                    "Flow Tab",
                    "Interactive visual graph of your branch workflow. Visualizes main, develop, feature, release, and hotfix branches with commit history, divergence, and quick actions.",
                    "Next",
                    showCiCd
            );
        };

        Runnable showIssues = () -> {
            if (!canShowTooltip(GOT_IT_ISSUES_TAB_ID)) {
                showFlow.run();
                return;
            }
            cm.setSelectedContent(tasks);
            showTabGotIt(
                    toolWindow, tasks, "Issues", GOT_IT_ISSUES_TAB_ID,
                    "Issues Tab",
                    "Integrates with your issue tracking platforms (GitHub, GitLab, Jira, Redmine). Browse assigned tasks, view details, and instantly start feature branches directly from issues.",
                    "Next",
                    showFlow
            );
        };

        Runnable showLogs = () -> {
            if (!canShowTooltip(GOT_IT_LOGS_TAB_ID)) {
                showIssues.run();
                return;
            }
            cm.setSelectedContent(logs);
            showTabGotIt(
                    toolWindow, logs, "Logs", GOT_IT_LOGS_TAB_ID,
                    "Logs Tab",
                    "Monitors real-time Git executions. View the full command history, terminal outputs, and live process statuses whenever Git Flow operations run.",
                    "Next",
                    showIssues
            );
        };

        showLogs.run();
    }

    /**
     * Backwards-compatible alias for showing the Flow tab got it.
     */
    public static void checkAndShowFlowGotIt(ToolWindow toolWindow, Content flowContent) {
        checkAndShowTabsTour(toolWindow, null, null, flowContent, null, false);
    }

    private static void showTabGotIt(
            @NotNull ToolWindow toolWindow,
            @NotNull Content targetContent,
            @NotNull String title,
            @NotNull String tooltipId,
            @NotNull String header,
            @NotNull String message,
            @NotNull String buttonLabel,
            @Nullable Runnable onNext
    ) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) {
                TABS_TOUR_ACTIVE.set(false);
                return;
            }

            GotItTooltip tooltip = new GotItTooltip(
                    tooltipId,
                    message,
                    toolWindow.getDisposable()
            )
            .withHeader(header)
            .withPosition(Balloon.Position.above)
            .withButtonLabel(buttonLabel)
            .withLink("Switch to " + title, () -> {
                if (!toolWindow.isDisposed()) {
                    toolWindow.getContentManager().setSelectedContent(targetContent);
                }
            });

            if (!tooltip.canShow()) {
                if (onNext != null) {
                    onNext.run();
                } else {
                    TABS_TOUR_ACTIVE.set(false);
                }
                return;
            }

            AtomicBoolean nextTriggered = new AtomicBoolean(false);
            Runnable triggerNext = () -> {
                if (nextTriggered.compareAndSet(false, true)) {
                    if (onNext != null) {
                        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                            if (!toolWindow.isDisposed()) {
                                ApplicationManager.getApplication().invokeLater(onNext);
                            } else {
                                TABS_TOUR_ACTIVE.set(false);
                            }
                        }, 120, TimeUnit.MILLISECONDS);
                    } else {
                        TABS_TOUR_ACTIVE.set(false);
                    }
                }
            };

            tooltip.withGotItButtonAction(() -> {
                triggerNext.run();
                return kotlin.Unit.INSTANCE;
            });

            tooltip.setOnBalloonCreated(balloon -> {
                balloon.addListener(new JBPopupListener() {
                    @Override
                    public void onClosed(@NotNull LightweightWindowEvent event) {
                        triggerNext.run();
                    }
                });
                return kotlin.Unit.INSTANCE;
            });

            displayTooltipOnTab(toolWindow, targetContent, title, tooltip);
        });
    }

    private static void displayTooltipOnTab(
            ToolWindow toolWindow,
            Content targetContent,
            String title,
            GotItTooltip tooltip
    ) {
        JComponent targetTab = findTabComponent(getToolWindowRoot(toolWindow), targetContent, title);
        if (targetTab != null && targetTab.isShowing() && targetTab.getWidth() > 0 && targetTab.getHeight() > 0) {
            tooltip.show(targetTab, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
        } else if (targetTab != null) {
            targetTab.addHierarchyListener(new HierarchyListener() {
                @Override
                public void hierarchyChanged(HierarchyEvent e) {
                    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && targetTab.isShowing()) {
                        targetTab.removeHierarchyListener(this);
                        if (tooltip.canShow()) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                if (targetTab.isShowing() && targetTab.getWidth() > 0 && tooltip.canShow()) {
                                    tooltip.show(targetTab, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
                                }
                            });
                        }
                    }
                }
            });
        } else {
            if (toolWindow.isVisible()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (toolWindow.isDisposed() || !tooltip.canShow()) return;
                    JComponent retryTab = findTabComponent(getToolWindowRoot(toolWindow), targetContent, title);
                    if (retryTab != null && retryTab.isShowing() && retryTab.getWidth() > 0) {
                        tooltip.show(retryTab, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
                    }
                });
            }
        }
    }

    private static @Nullable Content findContentByPrefix(@NotNull ContentManager cm, @NotNull String prefix) {
        for (Content c : cm.getContents()) {
            if (c.getDisplayName() != null && c.getDisplayName().startsWith(prefix)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Traverses the tool window Swing hierarchy (including the header in InternalDecorator)
     * to find the exact tab label representing the tab.
     */
    private static Container getToolWindowRoot(ToolWindow toolWindow) {
        Component comp = toolWindow.getComponent();
        Container root = (comp instanceof Container) ? (Container) comp : null;
        while (comp != null && !(comp instanceof Window)) {
            if (comp instanceof Container container) {
                root = container;
                if (comp.getClass().getName().contains("InternalDecorator")) {
                    break;
                }
            }
            comp = comp.getParent();
        }
        return root;
    }

    private static JComponent findTabComponent(Container container, Content targetContent, String title) {
        if (container == null) return null;

        for (Component comp : container.getComponents()) {
            // 1. Direct ContentTabLabel matching our Content
            if (comp.getClass().getName().contains("ContentTabLabel")) {
                try {
                    Method m = comp.getClass().getMethod("getContent");
                    if (m.invoke(comp) == targetContent) {
                        return (JComponent) comp;
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. TabLabel (JBTabs in New UI) matching tab text
            if (comp.getClass().getName().contains("TabLabel")) {
                try {
                    Method m = comp.getClass().getMethod("getInfo");
                    Object info = m.invoke(comp);
                    if (info != null) {
                        Method getText = info.getClass().getMethod("getText");
                        Object text = getText.invoke(info);
                        if (text != null && text.toString().trim().startsWith(title)) {
                            return (JComponent) comp;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 3. Any JLabel / ContentLabel whose text starts with title (e.g. "Flow" or "Flow •")
            if (comp instanceof JLabel label) {
                String text = label.getText();
                if (text != null && text.trim().startsWith(title)) {
                    if (label.getParent() instanceof JComponent parent &&
                            (parent.getClass().getName().contains("Tab") || parent.getClass().getName().contains("Label"))) {
                        return parent;
                    }
                    return label;
                }
            }

            // 4. Accessible context matching tab title
            if (comp.getAccessibleContext() != null) {
                String name = comp.getAccessibleContext().getAccessibleName();
                if (name != null && name.trim().startsWith(title)) {
                    if (comp instanceof JComponent jComp) return jComp;
                }
            }

            // Recurse into children
            if (comp instanceof Container childContainer) {
                JComponent found = findTabComponent(childContainer, targetContent, title);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void checkAndClearLiveIcon(ToolWindow toolWindow, Content logsContent) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) return;
            PluginUtils.clearLiveIndicator(toolWindow);
            Content selected = toolWindow.getContentManager().getSelectedContent();
            if (selected == logsContent) {
                String displayName = logsContent.getDisplayName();
                if (displayName != null && displayName.endsWith(" \u2022")) {
                    logsContent.setDisplayName(displayName.substring(0, displayName.length() - 2));
                }
            }
        });
    }

    private void notifyNewContent(ToolWindow toolWindow, Content content, String baseTitle) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) return;
            Content selected = toolWindow.getContentManager().getSelectedContent();
            if (selected != content) {
                String currentName = content.getDisplayName();
                if (currentName != null && !currentName.endsWith(" \u2022")) {
                    content.setDisplayName(baseTitle + " \u2022");
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // CI/CD tab content
    // -----------------------------------------------------------------------

    private JComponent createCIContent(Project project, CIDataToolWindowPanel ciDataPanel) {
        JPanel panel = new JPanel(new BorderLayout());

        // ---- Action toolbar (stop / clear) ----
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        // actionGroup.add(new ToggleCIAction(ciDataPanel));
        actionGroup.add(new StopCIAction(ciDataPanel));
        actionGroup.add(new ClearCIAction(ciDataPanel));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "CIToolWindowToolbar", actionGroup, true);
        toolbar.setTargetComponent(panel);

        // ---- Repository selection combo ----
        ComboBox<String> repoCombo = new ComboBox<>();
        populateRepoCombo(repoCombo, project, ciDataPanel, null);

        // Refresh combo when settings change (user edits config in ConfigDialog)
        project.getMessageBus().connect(ciDataPanel).subscribe(
                GitFlowSettingsListener.TOPIC,
                new GitFlowSettingsListener() {
                    @Override
                    public void settingsChanged() {
                        String previousPath = ciDataPanel.getSelectedRepoPath();
                        populateRepoCombo(repoCombo, project, ciDataPanel, previousPath);
                    }
                });

        repoCombo.addActionListener(e -> {
            int idx = repoCombo.getSelectedIndex();
            List<GitRepository> repos =
                    GitRepositoryManager.getInstance(project).getRepositories();
            if (idx >= 0 && idx < repos.size()) {
                ciDataPanel.setSelectedRepoPath(repos.get(idx).getRoot().getPath());
            }
        });

        // ---- North panel: toolbar (repo combo commented out) ----
        JPanel northPanel = new JPanel(new BorderLayout(4, 0));
        northPanel.add(toolbar.getComponent(), BorderLayout.WEST);

        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(ciDataPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Fills the repo combo with the Git repositories that have a CI/CD configuration.
     * Repositories without any config are also included (user may have just added one).
     *
     * @param previousPath repo path to re-select after refresh; {@code null} → select first.
     */
    private void populateRepoCombo(ComboBox<String> combo,
                                   Project project,
                                   CIDataToolWindowPanel ciDataPanel,
                                   @org.jetbrains.annotations.Nullable String previousPath) {
        combo.removeAllItems();

        List<GitRepository> repos =
                GitRepositoryManager.getInstance(project).getRepositories();

        if (repos.isEmpty()) {
            combo.addItem("(no repositories)");
            ciDataPanel.setSelectedRepoPath(null);
            return;
        }

        int restoreIdx = 0;
        for (int i = 0; i < repos.size(); i++) {
            GitRepository repo = repos.get(i);
            String path = repo.getRoot().getPath();
            String name = repo.getRoot().getName();

            // Suffix "[CI]" when a URL has been configured, for quick visual feedback
            GitFlowSettingsService svc = GitFlowSettingsService.getInstance(project);
            RepoCiEntry entry = svc.getRepoCiEntry(path);
            boolean hasCI = entry != null && entry.ciServer.isActive();
            combo.addItem(hasCI ? name + " [CI]" : name);

            if (path.equals(previousPath)) restoreIdx = i;
        }

        combo.setSelectedIndex(restoreIdx);

        // Sync panel to the selected repo
        if (restoreIdx < repos.size()) {
            ciDataPanel.setSelectedRepoPath(repos.get(restoreIdx).getRoot().getPath());
        }
    }
}
