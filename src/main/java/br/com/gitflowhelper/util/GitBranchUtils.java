package br.com.gitflowhelper.util;

import com.intellij.openapi.project.Project;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.util.List;

public class GitBranchUtils {

    public static String getCurrentBranchName(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        List<GitRepository> repositories = manager.getRepositories();
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
