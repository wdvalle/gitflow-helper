package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SyncAction extends BaseAction {

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String currentBranch = getBranchName(project);
        String baseBranch = getBaseBranch(project, currentBranch);

        if (baseBranch == null) {
            NotificationUtil.showGitFlowErrorNotification(project, "Error", "Could not identify base branch for " + currentBranch);
            return;
        }

        // Check for uncommitted changes first
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        for (GitRepository repository : repoManager.getRepositories()) {
            if (!ChangeListManager.getInstance(project).getChangesIn(repository.getRoot()).isEmpty()) {
                NotificationUtil.showGitFlowErrorNotification(project, "Sync Cancelled",
                        "Repository '" + repository.getRoot().getName() + "' has uncommitted changes. Please commit or stash them first.");
                return;
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, true, project);
            try {
                sync(project, currentBranch, baseBranch);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Branch '" + currentBranch + "' synced with '" + baseBranch + "'");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Sync Error", ex.getGitResult().getProcessMessage());
            } catch (Exception ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getMessage());
            }
            setLoading(false, project);
        });
    }

    private void sync(Project project, String currentBranch, String baseBranch) {
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        GitExecutor executor = new GitExecutor(project);

        List<GitRepository> repositories = repoManager.getRepositories();
        int totalSteps = repositories.size() * 5;
        int currentStep = 0;

        for (GitRepository repository : repositories) {
            // 1. Fetch
            setProgress(++currentStep * 10 / totalSteps, project);
            executor.execute(repository.getRoot(), GitCommand.FETCH, REMOTE);

            // 2. Checkout base branch
            setProgress(++currentStep * 10 / totalSteps, project);
            executor.execute(repository.getRoot(), GitCommand.CHECKOUT, baseBranch);

            // 3. Pull base branch
            setProgress(++currentStep * 10 / totalSteps, project);
            executor.execute(repository.getRoot(), GitCommand.PULL, REMOTE, baseBranch);

            // 4. Checkout current branch back
            setProgress(++currentStep * 10 / totalSteps, project);
            executor.execute(repository.getRoot(), GitCommand.CHECKOUT, currentBranch);

            // 5. Merge base into current
            setProgress(++currentStep * 10 / totalSteps, project);
            executor.execute(repository.getRoot(), GitCommand.MERGE, baseBranch);

            repository.update();
        }
        setProgress(10, project);
    }

    protected String getBaseBranch(Project project, String currentBranch) {
        if (currentBranch == null) return null;
        if (currentBranch.startsWith(getFeaturePrefix(project))) return getDevelopBranch(project);
        if (currentBranch.startsWith(getReleasePrefix(project))) return getDevelopBranch(project);
        if (currentBranch.startsWith(getHotfixPrefix(project))) return getMainBranch(project);
        return null;
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }

        String currentBranch = getBranchName(project);
        boolean isFlowBranch = currentBranch != null &&
                (currentBranch.startsWith(getFeaturePrefix(project)) ||
                        currentBranch.startsWith(getReleasePrefix(project)) ||
                        currentBranch.startsWith(getHotfixPrefix(project)));

        presentation.setEnabled(StringUtil.isNotEmpty(getMainBranch(project)) &&
                StringUtil.isNotEmpty(getDevelopBranch(project)) &&
                isFlowBranch);
    }
}
