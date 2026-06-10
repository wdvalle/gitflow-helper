package br.com.gitflowhelper.actions.branches;

import br.com.gitflowhelper.gittree.GitBranchPopupBuilder;
import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.actions.BaseAction;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import git4idea.GitBranch;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public class CheckoutRemoteBranchAction extends BaseAction {

    public CheckoutRemoteBranchAction(
            String label,
            String remoteBranchName
    ) {
        super(label,
                GitFlowDescriptions.CHECKOUT_REMOTE.getValue() +remoteBranchName,
                AllIcons.Actions.CheckOut);
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        //e.getPresentation().setEnabled(!isCurrent);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        GitRepository repository = ActionParamsService.getRepo(project,this);
        String currentBranchName = repository.getCurrentBranchName();
        String checkoutBranchName = ActionParamsService.getName(project,this);
        boolean isCurrent = currentBranchName.equals(checkoutBranchName);

        if (isCurrent) return;

        GitExecutor executor = new GitExecutor(project);

        String localBranch =
                checkoutBranchName.substring(checkoutBranchName.indexOf('/') + 1);


        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean needsRemote = true;
            setLoading(true, true, project);
            try {
                setProgress(1, project);

                GitBranch branch = repository.getBranches().findLocalBranch(localBranch);
                if (branch != null) {
                    new CheckoutLocalBranchAction("", localBranch)
                            .checkout(repository, project, localBranch);
                    needsRemote = false;

                }
                setProgress(3, project);
                if (needsRemote) {
                    executor.execute(
                            repository.getRoot(),
                            GitCommand.CHECKOUT,
                            "-b",
                            localBranch,
                            "--track",
                            checkoutBranchName
                    );
                }
                setProgress(7, project);

                repository.update();

                setProgress(10, project);

                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Remote branch " + checkoutBranchName + " checked out successfully");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
            }
            setLoading(false, project);
        });

        if (GitBranchPopupBuilder.getJbPopup() != null) {
            GitBranchPopupBuilder.getJbPopup().cancel();
        }
    }
}
