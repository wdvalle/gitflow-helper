package br.com.gitflowhelper.util;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.project.Project;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;

import java.util.List;

public class GitBranchUtils {

    public static String getCurrentBranchName(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        List<GitRepository> repositories = GitFlowSettingsService.getInstance(project).getSelectedRepositories();
        if (repositories.isEmpty()) {
            return null;
        }

        GitRepository repository = repositories.get(0);
        GitBranch branch = repository.getCurrentBranch();

        if (branch != null) {
            return branch.getName();
        }

        // Detached HEAD (e.g. checkout on commit)
        return repository.getCurrentRevision();
    }
}
