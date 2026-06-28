package br.com.gitflow.tracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GitLabConnector extends IssueTrackerConnector {
    private final String projectId;

    public GitLabConnector(String baseUrl, String projectId, String token) {
        super(baseUrl, token);
        this.projectId = projectId;
    }

    @Override
    public boolean startIssue(String issueId) {
        return putRequest("/api/v4/projects/" + projectId + "/issues/" + issueId, "{\"add_labels\":\"Doing\"}");
    }

    @Override
    public boolean assignIssue(String issueId, String assigneeId) {
        return putRequest("/api/v4/projects/" + projectId + "/issues/" + issueId, "{\"assignee_ids\":[" + assigneeId + "]}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return putRequest("/api/v4/projects/" + projectId + "/issues/" + issueId, "{\"state_event\":\"close\"}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/v4/projects/" + projectId + "/issues/" + issueId))
                    .header("PRIVATE-TOKEN", this.token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = root.get("iid").getAsString();
            String title = root.get("title").getAsString();
            String desc = root.has("description") && !root.get("description").isJsonNull() ? root.get("description").getAsString() : "";
            String url = root.get("web_url").getAsString();
            String createdAt = root.get("created_at").getAsString();
            String state = root.get("state").getAsString();

            List<String> assignees = new ArrayList<>();
            if (root.has("assignees") && !root.get("assignees").isJsonNull()) {
                for (JsonElement el : root.getAsJsonArray("assignees")) {
                    assignees.add(el.getAsJsonObject().get("username").getAsString());
                }
            }
            return new IssueResponse(id, title, desc, assignees, url, createdAt, state);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean putRequest(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + path))
                    .header("PRIVATE-TOKEN", this.token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
