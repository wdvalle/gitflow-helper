package br.com.gitflowhelper.util;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.util.ArrayList;
import java.util.List;

public class TaskProjectFilter {
    private final Project project;

    public TaskProjectFilter(Project project) {
        this.project = project;
    }

    public List<String> getProjectPaths() {
        List<String> paths = new ArrayList<>();
        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        for (GitRepository repository : manager.getRepositories()) {
            for (GitRemote remote : repository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    String path = extractProjectPath(url);
                    if (path != null) {
                        paths.add(path);
                    }
                }
            }
        }
        return paths;
    }

    private String extractProjectPath(String url) {
        if (url == null) return null;
        String path = url;
        // Remove protocol
        if (path.contains("://")) {
            path = path.substring(path.indexOf("://") + 3);
        } else if (path.contains("@")) {
            path = path.substring(path.indexOf("@") + 1);
        }

        // Remove host
        if (path.contains("/")) {
            path = path.substring(path.indexOf("/") + 1);
        } else if (path.contains(":")) {
            path = path.substring(path.indexOf(":") + 1);
        }

        // Remove .git at the end
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }

        return path.toLowerCase();
    }

    public boolean isTaskFromProject(Task task, List<String> projectPaths) {
        String issueUrl = task.getIssueUrl();
        if (issueUrl == null) return true; // Keep local tasks or tasks without URL

        String lowerUrl = issueUrl.toLowerCase();
        for (String path : projectPaths) {
            if (lowerUrl.contains(path.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
