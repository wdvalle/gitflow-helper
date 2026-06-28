package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class YouTrackConnector extends BaseTrackerConnector {

    public YouTrackConnector(String baseUrl, String token) {
        super(baseUrl, token);
    }

    @Override
    public void markAsStarted(Task task) throws Exception {
        executeCommand(task, "state In Progress");
    }

    @Override
    public void markAsFinished(Task task) throws Exception {
        executeCommand(task, "state Fixed");
    }

    private void executeCommand(Task task, String command) throws Exception {
        String issueId = task.getNumber();
        // URL: base/api/issues/:id/commands
        String url = String.format("%s/api/issues/%s/commands", baseUrl, issueId);

        String json = String.format("{\"query\":\"%s\"}", command);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Falha ao atualizar issue no YouTrack: " + response.body());
        }
    }
}
