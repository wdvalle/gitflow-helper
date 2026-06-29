package br.com.gitflow.tracker;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.LocalTaskImpl;

import java.net.URI;
import java.util.Optional;

public class TrackerFactory {

    public static Optional<IssueTrackerConnector> getConnector(Project project, Task task) {
        TaskManager taskManager = TaskManager.getManager(project);
        TaskRepository[] repositories = taskManager.getAllRepositories();

        for (TaskRepository repository : repositories) {
            if (isTaskFromRepository(task, repository)) {
                String url = repository.getUrl();
                String token = extractField(repository, "myPassword", "password");
                String username = extractField(repository, "myUsername", "username");

                String typeName = repository.getRepositoryType().getName();

                try {
                    if (typeName.equalsIgnoreCase("GitLab")) {
                        // No GitLab do IntelliJ, a URL costuma apontar para a raiz.
                        // O ID do projeto pode estar embutido ou ser necessário extraí-lo da URL da task.
                        String projectId = extractGitLabProjectId(task);
                        return Optional.of(new GitLabConnector(url, projectId, token));
                        
                    } else if (typeName.equalsIgnoreCase("GitHub")) {
                        // Precisamos converter a URL do repositório (ex: https://github.com/dono/repo) 
                        // para o formato "dono/repo"
                        String repoName = extractGitHubRepo(url);
                        return Optional.of(new GitHubConnector(repoName, token));
                        
                    } else if (typeName.equalsIgnoreCase("Redmine")) {
                        return Optional.of(new RedmineConnector(url, token));
                        
                    } else if (typeName.equalsIgnoreCase("Jira")) {
                        // Jira exige e-mail (username) + token
                        return Optional.of(new JiraConnector(url, username, token));
                        
                    } else if (typeName.equalsIgnoreCase("YouTrack")) {
                        return Optional.of(new YouTrackConnector(url, token));
                    }
                } catch (Exception e) {
                    // Log silencioso ou tratamento específico do plugin caso a extração falhe
                    return Optional.empty();
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

        String normalizedIssueUrl = issueUrl.endsWith("/") ? issueUrl.substring(0, issueUrl.length() - 1) : issueUrl;
        String normalizedRepoUrl = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;

        return normalizedIssueUrl.startsWith(normalizedRepoUrl);
    }

    /**
     * Metodo genérico para extrair dados protegidos dos Repositórios do IntelliJ via Reflexão.
     * Serve tanto para 'password' quanto para 'username'.
     */
    private static String extractField(TaskRepository repository, String primaryField, String fallbackField) {
        try {
            java.lang.reflect.Field field = findField(repository.getClass(), primaryField);
            if (field == null) field = findField(repository.getClass(), fallbackField);

            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(repository);
                if (value != null) return value.toString();
            }
        } catch (Exception ignored) {}
        return "";
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

    /**
     * Auxiliar para transformar "https://github.com/dono/meu-repo" em "dono/meu-repo"
     */
    private static String extractGitHubRepo(String repoUrl) {
        try {
            URI uri = new URI(repoUrl);
            String path = uri.getPath(); // Retorna "/dono/meu-repo"
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } catch (Exception e) {
            return repoUrl; // Fallback inseguro, mas evita crash imediato
        }
    }

    /**
     * O GitLab usa IID e ProjectID. O ProjectID nem sempre está explícito na base URL.
     * Muitas vezes é necessário fazer "URL encode" do path com namespace (ex: "grupo%2Fprojeto").
     */
    private static String extractGitLabProjectId(Task task) {
        try {
            // 1. Tenta obter o ID do projeto via reflexão (campo myIssue do GitlabTask)
            if (task instanceof LocalTaskImpl) {
                Task theTask = task.getRepository().getIssues(task.getId(), 0, 1, false)[0];
                Object myIssue = getFieldValue(theTask, "myIssue");
                Object projectId = getFieldValue(myIssue, "projectId");
                if (projectId != null) return projectId.toString();
            }
            Object myIssue = getFieldValue(task, "myIssue");
            if (myIssue != null) {
                Object projectId = getFieldValue(myIssue, "projectId");
                if (projectId == null) projectId = getFieldValue(myIssue, "project_id");
                if (projectId != null) return projectId.toString();
            }

            String taskUrl = task.getIssueUrl();
            if (taskUrl == null) return "";

            // 2. Fallback: Extração via URL da issue
            // Exemplo url: https://gitlab.com/meu-grupo/meu-projeto/-/issues/123
            URI uri = new URI(taskUrl);
            String path = uri.getPath();
            
            // Removemos a parte final referentes as issues para pegar só o namespace
            if (path.contains("/-/issues/")) {
                path = path.substring(0, path.indexOf("/-/issues/"));
            } else if (path.contains("/issues/")) {
                path = path.substring(0, path.indexOf("/issues/"));
            }
            
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // O GitLab aceita o "Namespace/Projeto" encodado no lugar do ID numérico!
            return path;
        } catch (Exception e) {
            return "";
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception ignored) {}
        return null;
    }
}