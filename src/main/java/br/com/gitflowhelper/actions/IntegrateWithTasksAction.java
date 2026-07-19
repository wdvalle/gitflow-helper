package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.events.GitFlowTaskListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.NotificationUtil;
import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public class IntegrateWithTasksAction extends BaseAction {

    public IntegrateWithTasksAction() {
        super("Integrate with tasks", "Enable/Disable Task integration", AllIcons.Actions.Checked);
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        Project p = e.getProject();
        if (p == null) return;

        if (PluginUtils.isTasksPluginMissing()) {
            // Show as disabled with a warning icon so the user knows something is off
            e.getPresentation().setIcon(AllIcons.General.Warning);
            e.getPresentation().setText("Tasks integration (plugin not installed)");
            e.getPresentation().setEnabled(true); // still clickable — clicking shows the install dialog
            return;
        }

        boolean integrate = GitFlowSettingsService.getInstance(p).isIntegrateWithTasks();
        e.getPresentation().setIcon(integrate ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox);
        e.getPresentation().setText("Tasks integration");
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project p = e.getProject();
        if (p == null) return;

        // ── Guard: Tasks plugin not installed or disabled ─────────────────────
        if (PluginUtils.isTasksPluginMissing()) {
            showPluginNotInstalledDialog(p);
            return;
        }

        // ── Normal toggle flow ────────────────────────────────────────────────
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(p);
        boolean currentSetting = settings.isIntegrateWithTasks();

        if (!currentSetting) {
            showEnableIntegrationDialog(p, settings);
        } else {
            showDisableIntegrationDialog(p, settings);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Shows a dialog explaining that the "Task Management" plugin is required,
     * with a clickable link that opens the IDE's Plugin Manager directly on
     * the Marketplace search for "Task Management".
     */
    private void showPluginNotInstalledDialog(Project project) {
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("Task Management Plugin Required");

        JPanel panel = new JPanel(new BorderLayout(15, 10));
        panel.add(new JLabel(AllIcons.General.Warning), BorderLayout.WEST);

        JEditorPane message = new JEditorPane("text/html", "");
        message.setEditable(false);
        message.setOpaque(false);
        message.setBackground(new Color(0, 0, 0, 0));
        message.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        message.setFont(UIManager.getFont("Label.font"));
        message.setText(
                "<html><body>" +
                "The <b>Task Management</b> plugin is required to use the Tasks integration feature " +
                "of Git Flow Helper, but it is currently <b>not installed</b> or <b>disabled</b>.<br><br>" +
                "This plugin allows Git Flow Helper to:<br>" +
                "&bull; <b>Link branches to issues</b> from GitHub, GitLab, Redmine, Jira and more.<br>" +
                "&bull; <b>Auto-generate branch names</b> from task titles.<br>" +
                "&bull; <b>Mark tasks</b> as In Progress when starting a feature or hotfix.<br>" +
                "&bull; <b>Close tasks</b> automatically when finishing a branch.<br><br>" +
                "To install it, click <b>Open Plugin Manager</b> below and search for " +
                "<b>&quot;Task Management&quot;</b>.<br><br>" +
                "<font color='gray'><i>After installing and restarting the IDE, click " +
                "&quot;Tasks integration&quot; again to enable it.</i></font>" +
                "</body></html>"
        );

        // Clicking any hyperlink inside the editor pane opens the plugin manager
        message.addHyperlinkListener(ev -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(ev.getEventType())) {
                openPluginManager(project);
            }
        });

        panel.add(message, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(540, 230));

        builder.setCenterPanel(panel);
        builder.addOkAction().setText("Open Plugin Manager");
        builder.addCancelAction().setText("Cancel");

        if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
            openPluginManager(project);
        }
    }

    /**
     * Opens the IDE's Plugins settings dialog, landing directly on the
     * Marketplace tab with "Task Management" pre-filled in the search box.
     */
    private void openPluginManager(Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Plugins");
    }

    private void showEnableIntegrationDialog(Project project, GitFlowSettingsService settings) {
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("Enable Tasks Integration");

        JPanel panel = new JPanel(new BorderLayout(15, 0));
        panel.add(new JLabel(Messages.getQuestionIcon()), BorderLayout.WEST);

        JLabel label = new JLabel("<html><body>" +
                "Task integration links Git Flow branches with your issue tracker " +
                "<span style='color:orange'>(at this time <b>GitHub, GitLab and Redmine</b>, more trackers coming soon)</span>.<br><br>" +
                "&bull; <b>Starting a feature or hotfix</b>: Select a task to auto-generate the branch name and optionally mark it as 'In Progress'.<br>" +
                "&bull; <b>Finishing a feature or hotfix</b>: Option to merge the branch in develop or main, close the associated task and switch back to the default context.<br><br>" +
                "Note: You must configure your Task Servers at: <b>Settings &#x2192; Tools &#x2192; Tasks &#x2192; Servers</b>.<br><br>" +
                "Enable task integration now?</body></html>");
        panel.add(label, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(500, 200));

        builder.setCenterPanel(panel);
        builder.addOkAction().setText("Enable");
        builder.addCancelAction().setText("Cancel");

        if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
            settings.setIntegrateWithTasks(true);
            project.getMessageBus().syncPublisher(GitFlowTaskListener.TOPIC).tasksChanged();
            NotificationUtil.showGitFlowSuccessNotification(project, "Git Flow Helper", "Task integration enabled successfully.");
        }
    }

    private void showDisableIntegrationDialog(Project project, GitFlowSettingsService settings) {
        if (Messages.showYesNoDialog(project,
                "Are you sure you want to disable Tasks integration? You can re-enable it at any time.",
                "Disable Tasks Integration",
                "Disable",
                "Cancel",
                Messages.getQuestionIcon()) == Messages.YES) {
            settings.setIntegrateWithTasks(false);
            project.getMessageBus().syncPublisher(GitFlowTaskListener.TOPIC).tasksChanged();
            NotificationUtil.showGitFlowSuccessNotification(project, "Git Flow Helper", "Task integration disabled successfully.");
        }
    }
}
