package br.com.gitflow.tracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RedmineConnector extends IssueTrackerConnector {
    private String username;
    private String password;

    public RedmineConnector(String baseUrl, String username, String password) {
        super(baseUrl, null); // Pass null for token, as we're not using it for basic auth
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean startIssue(String issueId) {
        return putRequest("/issues/" + issueId + ".json", "{\"issue\":{\"status_id\":2, \"notes\":\"Issue started via GitFlow Helper\"}}");
    }

    @Override
    public String getUserId(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/users.json?name=" + username))
                    .header("Authorization", getBasicAuthHeader())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return username;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (root.has("users") && root.getAsJsonArray("users").size() > 0) {
                return root.getAsJsonArray("users").get(0).getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception e) {
            // Fallback to username
        }
        return username;
    }

    @Override
    public boolean assignIssue(String issueId, String user) {
        // Redmine allows assigned_to_id to be "me" or a numeric ID. 
        // If it's not "me" or numeric, it might be a username, but Redmine API primarily expects ID.
        String body;
        if (user == null || user.isBlank()) {
            body = "{\"issue\":{\"assigned_to_id\":\"\"}}";
        } else if ("me".equalsIgnoreCase(user)) {
            body = "{\"issue\":{\"assigned_to_id\":\"me\"}}";
        } else if (user.matches("\\d+")) {
            body = "{\"issue\":{\"assigned_to_id\":" + user + "}}";
        } else {
            String userId = getUserId(user);
            if (userId.matches("\\d+")) {
                body = "{\"issue\":{\"assigned_to_id\":" + userId + "}}";
            } else {
                body = "{\"issue\":{\"assigned_to_id\":\"" + user + "\"}}";
            }
        }
        return putRequest("/issues/" + issueId + ".json", body);
    }

    @Override
    public boolean closeIssue(String issueId) {
        return putRequest("/issues/" + issueId + ".json", "{\"issue\":{\"status_id\":3, \"notes\":\"Issue closed via GitFlow Helper\"}}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/issues/" + issueId + ".json"))
                    .header("Authorization", getBasicAuthHeader())
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
            String state = root.has("status") && !root.get("status").isJsonNull() 
                    ? root.getAsJsonObject("status").get("name").getAsString() 
                    : "Unknown";

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
                    .header("Authorization", getBasicAuthHeader())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            return statusCode == 200 || statusCode == 204;
        } catch (Exception e) {
            return false;
        }
    }

    private String getBasicAuthHeader() {
        String credentials = this.username + ":" + this.password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
