package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Method;
import java.util.List;

public class GitFlowToolWindowFactory implements ToolWindowFactory {

    public static final String GOT_IT_FLOW_REDESIGN_ID = "gitflow.flow.graph.redesign.v2.9.0";

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
                        if (displayName != null && displayName.endsWith(" •")) {
                            selected.setDisplayName(displayName.substring(0, displayName.length() - 2));
                        }
                        if (selected == logsContent) {
                            PluginUtils.clearLiveIndicator(toolWindow);
                        }
                        checkAndShowFlowGotIt(toolWindow, flowContent);
                    }
                }
            }
        });

        // Listen for tool window show / state change to clear live indicator and show Flow got it
        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(
                ToolWindowManagerListener.TOPIC,
                new ToolWindowManagerListener() {
                    @Override
                    public void toolWindowShown(@NotNull ToolWindow tw) {
                        if ("GitFlow".equals(tw.getId())) {
                            checkAndClearLiveIcon(tw, logsContent);
                            checkAndShowFlowGotIt(tw, flowContent);
                        }
                    }

                    @Override
                    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
                        ToolWindow tw = toolWindowManager.getToolWindow("GitFlow");
                        if (tw != null && tw.isVisible()) {
                            checkAndClearLiveIcon(tw, logsContent);
                            checkAndShowFlowGotIt(tw, flowContent);
                        }
                    }
                }
        );

        // Detect when logsPanel becomes visible on screen
        logsPanel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && logsPanel.isShowing()) {
                checkAndClearLiveIcon(toolWindow, logsContent);
                checkAndShowFlowGotIt(toolWindow, flowContent);
            }
        });

        // Detect when the tool window component itself becomes visible on screen
        toolWindow.getComponent().addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && toolWindow.getComponent().isShowing()) {
                checkAndShowFlowGotIt(toolWindow, flowContent);
            }
        });

        if (toolWindow.isVisible()) {
            checkAndClearLiveIcon(toolWindow, logsContent);
            checkAndShowFlowGotIt(toolWindow, flowContent);
        }
    }

    private void checkAndShowFlowGotIt(ToolWindow toolWindow, Content flowContent) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) return;

            GotItTooltip tooltip = new GotItTooltip(
                    GOT_IT_FLOW_REDESIGN_ID,
                    "The Flow graph has been completely redesigned! It is now much more practical, intuitive, and useful for visualizing and managing your branch workflow.",
                    toolWindow.getDisposable()
            )
            .withHeader("Flow Graph Redesigned!")
            .withPosition(Balloon.Position.above)
            .withButtonLabel("Got It")
            .withLink("Switch to Flow", () -> {
                if (!toolWindow.isDisposed()) {
                    toolWindow.getContentManager().setSelectedContent(flowContent);
                }
            });

            if (!tooltip.canShow()) {
                return;
            }

            JComponent targetTab = findFlowTabComponent(toolWindow, flowContent);
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
                // The tool window header may not have finished layout yet; retry on the next EDT tick if still visible
                if (toolWindow.isVisible()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (toolWindow.isDisposed() || !tooltip.canShow()) return;
                        JComponent retryTab = findFlowTabComponent(toolWindow, flowContent);
                        if (retryTab != null && retryTab.isShowing() && retryTab.getWidth() > 0) {
                            tooltip.show(retryTab, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
                        }
                    });
                }
            }
        });
    }

    /**
     * Traverses the tool window Swing hierarchy (including the header in InternalDecorator)
     * to find the exact tab label representing the Flow tab.
     */
    private JComponent findFlowTabComponent(ToolWindow toolWindow, Content flowContent) {
        Container root = getToolWindowRoot(toolWindow);
        if (root == null) {
            return null;
        }
        return findTabComponent(root, flowContent, "Flow");
    }

    private Container getToolWindowRoot(ToolWindow toolWindow) {
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

    private JComponent findTabComponent(Container container, Content targetContent, String title) {
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

            // 3. Any JLabel / ContentLabel whose text starts with "Flow" (e.g. "Flow" or "Flow •")
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
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) return;
            PluginUtils.clearLiveIndicator(toolWindow);
            Content selected = toolWindow.getContentManager().getSelectedContent();
            if (selected == logsContent) {
                String displayName = logsContent.getDisplayName();
                if (displayName != null && displayName.endsWith(" •")) {
                    logsContent.setDisplayName(displayName.substring(0, displayName.length() - 2));
                }
            }
        });
    }

    private void notifyNewContent(ToolWindow toolWindow, Content content, String baseTitle) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if (toolWindow.isDisposed()) return;
            Content selected = toolWindow.getContentManager().getSelectedContent();
            if (selected != content) {
                String currentName = content.getDisplayName();
                if (currentName != null && !currentName.endsWith(" •")) {
                    content.setDisplayName(baseTitle + " •");
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
