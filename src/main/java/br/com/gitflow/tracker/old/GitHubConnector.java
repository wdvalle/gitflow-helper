package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GitHubConnector extends BaseTrackerConnector {

    public GitHubConnector(String baseUrl, String token) {
        super(baseUrl, token);
    }

    @Override
    public void markAsStarted(Task task) throws Exception {
        updateIssueState(task, "open");
    }

    @Override
    public void markAsFinished(Task task) throws Exception {
        updateIssueState(task, "closed");
    }

    private void updateIssueState(Task task, String state) throws Exception {
        String repoPath = extractRepoPath(task);
        String issueNumber = task.getNumber();

        // URL: https://api.github.com/repos/:owner/:repo/issues/:number
        String url = String.format("%s/repos/%s/issues/%s", baseUrl, repoPath, issueNumber);

        String json = String.format("{\"state\":\"%s\"}", state);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Falha ao atualizar issue no GitHub: " + response.body());
        }
    }

    private String extractRepoPath(Task task) {
        String issueUrl = task.getIssueUrl();
        if (issueUrl != null) {
            // Ex: https://github.com/owner/repo/issues/1
            String path = issueUrl.replace("https://github.com/", "");
            if (path.contains("/issues/")) {
                return path.split("/issues/")[0];
            }
        }
        return "";
    }
}
