package br.com.gitflow.tracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GitHubConnector extends IssueTrackerConnector {
    private final String repo;

    public GitHubConnector(String repo, String token) {
        super("https://api.github.com", token);
        this.repo = repo;
    }

    @Override
    public boolean startIssue(String issueId) {
        return patchRequest("/repos/" + repo + "/issues/" + issueId, "{\"labels\":[\"in-progress\"]}");
    }

    @Override
    public boolean assignIssue(String issueId, String assignee) {
        return patchRequest("/repos/" + repo + "/issues/" + issueId, "{\"assignees\":[\"" + assignee + "\"]}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return patchRequest("/repos/" + repo + "/issues/" + issueId, "{\"state\":\"closed\"}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/repos/" + repo + "/issues/" + issueId))
                    .header("Authorization", "Bearer " + this.token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = root.get("number").getAsString();
            String title = root.get("title").getAsString();
            String desc = root.has("body") && !root.get("body").isJsonNull() ? root.get("body").getAsString() : "";
            String url = root.get("html_url").getAsString();
            String createdAt = root.get("created_at").getAsString();
            String state = root.get("state").getAsString();

            List<String> assignees = new ArrayList<>();
            if (root.has("assignees") && !root.get("assignees").isJsonNull()) {
                for (JsonElement el : root.getAsJsonArray("assignees")) {
                    assignees.add(el.getAsJsonObject().get("login").getAsString());
                }
            }
            return new IssueResponse(id, title, desc, assignees, url, createdAt, state);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean patchRequest(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + path))
                    .header("Authorization", "Bearer " + this.token)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
