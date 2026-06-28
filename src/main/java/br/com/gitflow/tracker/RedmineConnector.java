package br.com.gitflow.tracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RedmineConnector extends IssueTrackerConnector {
    public RedmineConnector(String baseUrl, String apiKey) {
        super(baseUrl, apiKey);
    }

    @Override
    public boolean startIssue(String issueId) {
        return putRequest("/issues/" + issueId + ".json", "{\"issue\":{\"status_id\":2}}");
    }

    @Override
    public boolean assignIssue(String issueId, String userId) {
        return putRequest("/issues/" + issueId + ".json", "{\"issue\":{\"assigned_to_id\":" + userId + "}}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return putRequest("/issues/" + issueId + ".json", "{\"issue\":{\"status_id\":3}}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/issues/" + issueId + ".json"))
                    .header("X-Redmine-API-Key", this.token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("issue");
            String id = root.get("id").getAsString();
            String url = this.baseUrl + "/issues/" + id;
            String title = root.get("subject").getAsString();
            String desc = root.has("description") && !root.get("description").isJsonNull() ? root.get("description").getAsString() : "";
            String createdAt = root.get("created_on").getAsString();
            String state = root.getAsJsonObject("status").get("name").getAsString();

            List<String> assignees = new ArrayList<>();
            if (root.has("assigned_to") && !root.get("assigned_to").isJsonNull()) {
                assignees.add(root.getAsJsonObject("assigned_to").get("name").getAsString());
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
                    .header("X-Redmine-API-Key", this.token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
