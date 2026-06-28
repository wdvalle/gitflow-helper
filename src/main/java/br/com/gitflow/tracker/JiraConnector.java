package br.com.gitflow.tracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class JiraConnector extends IssueTrackerConnector {
    private final String email;

    public JiraConnector(String baseUrl, String email, String apiToken) {
        super(baseUrl, apiToken);
        this.email = email;
    }

    private String getAuthHeader() {
        String auth = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());
    }

    @Override
    public boolean startIssue(String issueId) {
        return postRequest("/rest/api/3/issue/" + issueId + "/transitions", "{\"transition\":{\"id\":\"21\"}}");
    }

    @Override
    public boolean assignIssue(String issueId, String accountId) {
        return putRequest("/rest/api/3/issue/" + issueId + "/assignee", "{\"accountId\":\"" + accountId + "\"\"}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return postRequest("/rest/api/3/issue/" + issueId + "/transitions", "{\"transition\":{\"id\":\"31\"}}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/rest/api/3/issue/" + issueId))
                    .header("Authorization", getAuthHeader())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject fields = root.getAsJsonObject("fields");

            String id = root.get("key").getAsString();
            String url = this.baseUrl + "/browse/" + id;
            String title = fields.get("summary").getAsString();
            String desc = fields.has("description") && !fields.get("description").isJsonNull() ? fields.get("description").toString() : "";
            String createdAt = fields.get("created").getAsString();
            String state = fields.getAsJsonObject("status").get("name").getAsString();

            List<String> assignees = new ArrayList<>();
            if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
                assignees.add(fields.getAsJsonObject("assignee").get("displayName").getAsString());
            }
            return new IssueResponse(id, title, desc, assignees, url, createdAt, state);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean postRequest(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + path))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 204;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean putRequest(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + path))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 204;
        } catch (Exception e) {
            return false;
        }
    }
}
