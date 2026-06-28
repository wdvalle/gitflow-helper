package br.com.gitflow.tracker.old;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import java.util.Optional;

public class TrackerFactory {

    public static Optional<IssueTrackerConnector> getConnector(Project project, Task task) {
        TaskManager taskManager = TaskManager.getManager(project);
        TaskRepository[] repositories = taskManager.getAllRepositories();
        
        for (TaskRepository repository : repositories) {
            if (isTaskFromRepository(task, repository)) {
                String url = repository.getUrl();
                String token = "";
                try {
                    // Tenta obter a senha via reflexão, pois nem todos os repositórios expõem publicamente na interface base
                    java.lang.reflect.Field passwordField = findField(repository.getClass(), "myPassword");
                    if (passwordField == null) passwordField = findField(repository.getClass(), "password");
                    
                    if (passwordField != null) {
                        passwordField.setAccessible(true);
                        Object value = passwordField.get(repository);
                        if (value != null) token = value.toString();
                    }
                } catch (Exception ignored) {}

                String typeName = repository.getRepositoryType().getName();
                if (typeName.equalsIgnoreCase("GitLab")) {
                    return Optional.of(new GitLabConnector(url, token));
                } else if (typeName.equalsIgnoreCase("GitHub")) {
                    return Optional.of(new GitHubConnector("https://api.github.com", token));
                } else if (typeName.equalsIgnoreCase("Redmine")) {
                    return Optional.of(new RedmineConnector(url, token));
                } else if (typeName.equalsIgnoreCase("Jira")) {
                    return Optional.of(new JiraConnector(url, token));
                } else if (typeName.equalsIgnoreCase("YouTrack")) {
                    return Optional.of(new YouTrackConnector(url, token));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isTaskFromRepository(Task task, TaskRepository repository) {
        String issueUrl = task.getIssueUrl();
        if (issueUrl == null) return false;
        String repoUrl = repository.getUrl();
        if (repoUrl == null) return false;
        
        // Remove trailing slashes for comparison
        String normalizedIssueUrl = issueUrl.endsWith("/") ? issueUrl.substring(0, issueUrl.length() - 1) : issueUrl;
        String normalizedRepoUrl = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        
        return normalizedIssueUrl.startsWith(normalizedRepoUrl);
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
