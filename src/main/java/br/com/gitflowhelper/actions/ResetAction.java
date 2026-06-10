package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.gittree.GitBranchPopupBuilder;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ResetAction extends BaseAction {

    public ResetAction(String label) {
        super(label, GitFlowDescriptions.RESET.getValue(), AllIcons.General.Reset);
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(StringUtil.isNotEmpty(getMainBranch(e.getProject())));
    }

    @Override
    public void actionPerformedImpl(AnActionEvent e) {
        Project project = e.getProject();

        int confirm = Messages.showYesNoDialog(
                project,
                "Reset configuration?",
                "Reset Plugin",
                Messages.getQuestionIcon()
        );

        if (confirm != Messages.YES) return;


        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, project);
            try {
                GitFlowSettingsService.getInstance(project).resetAndDeleteStorage();
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Reset successfully.");

            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
            }
            setLoading(false, project);
        });

    }

}
