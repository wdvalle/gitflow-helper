package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GitLabConnector extends BaseTrackerConnector {

    public GitLabConnector(String baseUrl, String token) {
        super(baseUrl, token);
    }

    @Override
    public void markAsStarted(Task task) throws Exception {
        // No GitLab, reabrir a issue se estiver fechada ou adicionar label 'doing'
        updateIssueState(task, "reopen");
    }

    @Override
    public void markAsFinished(Task task) throws Exception {
        updateIssueState(task, "close");
    }

    private void updateIssueState(Task task, String event) throws Exception {
        String projectPath = extractProjectPath(task);
        String issueIid = task.getNumber();
        
        if (projectPath == null || projectPath.isEmpty()) {
            throw new RuntimeException("Não foi possível identificar o ID do projeto para a task: " + task.getPresentableId());
        }

        String apiBase = baseUrl;
        if (!apiBase.contains("/api/v4")) {
            apiBase = apiBase + (apiBase.endsWith("/") ? "" : "/") + "api/v4";
        }

        // URL: /projects/:id/issues/:issue_iid?state_event=:event
        String url = String.format("%s/projects/%s/issues/%s?state_event=%s",
                apiBase, URLEncoder.encode(projectPath, StandardCharsets.UTF_8), issueIid, event);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Private-Token", token)
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Falha ao atualizar issue no GitLab (" + response.statusCode() + "): " + response.body());
        }
    }

    private String extractProjectPath(Task task) {
        // 1. Tenta obter o ID do projeto via reflexão (campo myIssue do GitlabTask)
        Object myIssue = getFieldValue(task, "myIssue");
        if (myIssue != null) {
            Object projectId = getFieldValue(myIssue, "projectId");
            if (projectId == null) projectId = getFieldValue(myIssue, "project_id");
            if (projectId != null) return projectId.toString();
        }

        // 2. Extração via URL da issue
        String issueUrl = task.getIssueUrl();
        if (issueUrl != null) {
            String path = issueUrl;
            // Normaliza removendo o protocolo e host (baseUrl)
            String normalizedBase = baseUrl.replaceFirst("https?://", "");
            String normalizedUrl = issueUrl.replaceFirst("https?://", "");
            
            if (normalizedUrl.startsWith(normalizedBase)) {
                path = normalizedUrl.substring(normalizedBase.length());
            }
            
            if (path.startsWith("/")) path = path.substring(1);

            // Caso 2a: URL de API já contém o ID do projeto
            // Ex: /api/v4/projects/313/issues/25
            if (path.contains("projects/") && path.contains("/issues/")) {
                String sub = path.substring(path.indexOf("projects/") + 9);
                return sub.split("/issues/")[0];
            }

            // Caso 2b: Formato Web padrão (novo GitLab)
            // Ex: group/project/-/issues/1
            if (path.contains("/-/issues/")) {
                return path.split("/-/issues/")[0];
            }

            // Caso 2c: Formato Web antigo
            // Ex: group/project/issues/1
            if (path.contains("/issues/")) {
                return path.split("/issues/")[0];
            }
        }
        return ""; // Fallback ou erro
    }
}
