package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.settings.RepoCiEntry;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GitFlowToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // Logs tab
        ToolWindowPanel logsPanel = new ToolWindowPanel(project);
        Content logsContent = contentFactory.createContent(logsPanel, "Logs", false);
        toolWindow.getContentManager().addContent(logsContent);

        // Issues tab
        TasksToolWindowPanel tasksPanel = new TasksToolWindowPanel(project);
        Content tasksContent = contentFactory.createContent(tasksPanel, "Issues", false);
        tasksContent.setDisposer(tasksPanel);
        toolWindow.getContentManager().addContent(tasksContent);

        // Flow tab
        GitFlowGraphPanel flowPanel = new GitFlowGraphPanel(project);
        Content flowContent = contentFactory.createContent(flowPanel, "Flow", false);
        toolWindow.getContentManager().addContent(flowContent);

        // CI/CD tab
        CIDataToolWindowPanel ciDataPanel = new CIDataToolWindowPanel(project);
        Content ciDataContent = contentFactory.createContent(
                createCIContent(project, ciDataPanel), "CI/CD", false);
        ciDataContent.setDisposer(ciDataPanel);
        toolWindow.getContentManager().addContent(ciDataContent);
    }

    // -----------------------------------------------------------------------
    // CI/CD tab content
    // -----------------------------------------------------------------------

    private JComponent createCIContent(Project project, CIDataToolWindowPanel ciDataPanel) {
        JPanel panel = new JPanel(new BorderLayout());

        // ---- Action toolbar (play / clear) ----
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new ToggleCIAction(ciDataPanel));
        actionGroup.add(new ClearCIAction(ciDataPanel));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "CIToolWindowToolbar", actionGroup, true);
        toolbar.setTargetComponent(panel);

        // ---- Repository selection combo ----
        ComboBox<String> repoCombo = new ComboBox<>();
        populateRepoCombo(repoCombo, project, ciDataPanel, null);

        // Refresh combo when settings change (user edits config in ConfigDialog)
        project.getMessageBus().connect().subscribe(
                GitFlowSettingsListener.TOPIC,
                () -> {
                    String previousPath = ciDataPanel.getSelectedRepoPath();
                    populateRepoCombo(repoCombo, project, ciDataPanel, previousPath);
                });

        repoCombo.addActionListener(e -> {
            int idx = repoCombo.getSelectedIndex();
            List<GitRepository> repos =
                    GitRepositoryManager.getInstance(project).getRepositories();
            if (idx >= 0 && idx < repos.size()) {
                ciDataPanel.setSelectedRepoPath(repos.get(idx).getRoot().getPath());
            }
        });

        // ---- North panel: toolbar + repo combo ----
        JPanel northPanel = new JPanel(new BorderLayout(4, 0));
        northPanel.add(toolbar.getComponent(), BorderLayout.WEST);

        JPanel comboWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        comboWrapper.add(new JLabel("Repo:"));
        repoCombo.setPreferredSize(new Dimension(180, repoCombo.getPreferredSize().height));
        comboWrapper.add(repoCombo);
        northPanel.add(comboWrapper, BorderLayout.CENTER);

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
