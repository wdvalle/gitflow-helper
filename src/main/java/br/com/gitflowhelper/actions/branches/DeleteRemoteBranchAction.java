package br.com.gitflowhelper.actions.branches;

import br.com.gitflowhelper.actions.BaseAction;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.gittree.GitBranchPopupBuilder;
import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;

public class DeleteRemoteBranchAction extends BaseAction {

    public DeleteRemoteBranchAction(
            String label,
            String remoteBranchName) {
        super(label,
                GitFlowDescriptions.DELETE_REMOTE.getValue() + remoteBranchName,
                AllIcons.Vcs.Remove);
    }

    @Override
    public void updateImpl(AnActionEvent e) {
        GitRepository repo = ActionParamsService.getRepo(this);
        String remoteBranchName = ActionParamsService.getName(this);

        if (repo.getCurrentBranch() != null) {
            String current = repo.getCurrentBranch().getName();
            if (remoteBranchName.equals(current)) {
                e.getPresentation().setEnabled(false);
            }
        }
    }

    @Override
    public void actionPerformedImpl(AnActionEvent e) {
        Project project = getProject();
        GitRepository repository = ActionParamsService.getRepo(this);
        String currentBranchName = repository.getCurrentBranchName();
        String remoteBranchName = ActionParamsService.getName(this);
        boolean isCurrent = currentBranchName.equals(remoteBranchName);

        if (isCurrent) return;

        int confirm = Messages.showYesNoDialog(
                project,
                "Delete remote branch '" + remoteBranchName + "'?",
                "Delete Remote Branch",
                Messages.getWarningIcon()
        );

        if (confirm != Messages.YES) return;


        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true);
            try {
                delete(repository, project, remoteBranchName);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Remote branch "+remoteBranchName+" deleted successfully");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
            }
            setLoading(false);
        });

        if (GitBranchPopupBuilder.getJbPopup() != null) {
            GitBranchPopupBuilder.getJbPopup().cancel();
        }
    }

    private void delete(GitRepository repository, Project project, String remoteBranchName) {
        setProgress(1);
        GitExecutor executor = new GitExecutor(project);
        String[] parts = remoteBranchName.split("/", 2);
        if (parts.length < 2) return;

        String remote = parts[0];
        String branch = parts[1];
        setProgress(4);

        executor.execute(
                repository.getRoot(),
                GitCommand.PUSH,
                remote,
                "--delete",
                branch
        );
        setProgress(6);

        repository.update();
        setProgress(10);

    }
}
