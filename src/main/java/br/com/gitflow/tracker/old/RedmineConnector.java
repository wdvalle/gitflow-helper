package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RedmineConnector extends BaseTrackerConnector {

    public RedmineConnector(String baseUrl, String token) {
        super(baseUrl, token);
    }

    @Override
    public void markAsStarted(Task task) throws Exception {
        updateIssueStatus(task, 2); // 2 = In Progress (padrão)
    }

    @Override
    public void markAsFinished(Task task) throws Exception {
        updateIssueStatus(task, 5); // 5 = Closed (padrão)
    }

    private void updateIssueStatus(Task task, int statusId) throws Exception {
        String issueId = task.getNumber();
        // URL: base/issues/:id.json
        String url = String.format("%s/issues/%s.json", baseUrl, issueId);

        String json = String.format("{\"issue\":{\"status_id\":%d}}", statusId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Redmine-API-Key", token)
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Falha ao atualizar issue no Redmine: " + response.body());
        }
    }
}
