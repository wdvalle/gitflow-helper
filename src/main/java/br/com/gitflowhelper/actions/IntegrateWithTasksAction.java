package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.events.GitFlowTaskListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IntegrateWithTasksAction extends BaseAction {

    public IntegrateWithTasksAction() {
        super("Integrate with tasks", "Enable/Disable Task integration", AllIcons.Actions.Checked);
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        Project p = e.getProject();
        if (p == null) return;
        boolean integrate = GitFlowSettingsService.getInstance(p).isIntegrateWithTasks();
        e.getPresentation().setIcon(integrate ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox);
        e.getPresentation().setText("Tasks integration");
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project p = e.getProject();
        if (p == null) return;
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(p);
        boolean currentSetting = settings.isIntegrateWithTasks();

        if (!currentSetting) {
            DialogBuilder builder = new DialogBuilder(p);
            builder.setTitle("Enable Tasks Integration");

            JPanel panel = new JPanel(new BorderLayout(15, 0));
            panel.add(new JLabel(Messages.getQuestionIcon()), BorderLayout.WEST);

            JLabel label = new JLabel("<html><body>" +
                    "Task integration links Git Flow branches with your issue tracker <span style='color:orange'>(at this time <b>GitHub and GitLab</b>, more trackers comming soon)</span>.<br><br>" +
                    "&bull; <b>Starting a feature or hotfix</b>: Select a task to auto-generate the branch name and optionally mark it as 'In Progress'.<br>" +
                    "&bull; <b>Finishing a feature or hotfix</b>: Option to merge the branch in develop or main, close the associated task and switch back to the default context.<br><br>" +
                    "Note: You must configure your Task Servers at:  <b>Settings &#x2192; Tools &#x2192; Tasks &#x2192; Servers</b>.<br><br>" +
                    "Enable task integration now?</body></html>");
            panel.add(label, BorderLayout.CENTER);
            panel.setPreferredSize(new Dimension(500, 200));

            builder.setCenterPanel(panel);
            builder.addOkAction().setText("Enable");
            builder.addCancelAction().setText("Cancel");

            if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
                settings.setIntegrateWithTasks(true);
                p.getMessageBus().syncPublisher(GitFlowTaskListener.TOPIC).tasksChanged();
                NotificationUtil.showGitFlowSuccessNotification(p, "Git Flow Helper", "Task integration enabled successfully.");
            }
        } else {
            if (Messages.showYesNoDialog(p,
                    "Are you sure you want to disable Tasks integration? You can re-enable it at any time.",
                    "Disable Tasks Integration",
                    "Disable",
                    "Cancel",
                    Messages.getQuestionIcon()) == Messages.YES) {
                settings.setIntegrateWithTasks(false);
                p.getMessageBus().syncPublisher(GitFlowTaskListener.TOPIC).tasksChanged();
                NotificationUtil.showGitFlowSuccessNotification(p, "Git Flow Helper", "Task integration disabled successfully.");
            }
        }
    }
}
