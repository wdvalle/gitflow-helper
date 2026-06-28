package br.com.gitflow.tracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class YouTrackConnector extends IssueTrackerConnector {
    public YouTrackConnector(String baseUrl, String permanentToken) {
        super(baseUrl, permanentToken);
    }

    @Override
    public boolean startIssue(String issueId) {
        return postRequest("/api/issues/" + issueId + "/executeCommand", "{\"query\":\"State In Progress\"}");
    }

    @Override
    public boolean assignIssue(String issueId, String userLogin) {
        return postRequest("/api/issues/" + issueId + "/executeCommand", "{\"query\":\"Assignee " + userLogin + "\"}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return postRequest("/api/issues/" + issueId + "/executeCommand", "{\"query\":\"State Fixed\"}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            String fields = "?fields=idReadable,summary,description,created,customFields(name,value(name,login))";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/issues/" + issueId + fields))
                    .header("Authorization", "Bearer " + this.token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = root.get("idReadable").getAsString();
            String url = this.baseUrl + "/issue/" + id;
            String title = root.get("summary").getAsString();
            String desc = root.has("description") && !root.get("description").isJsonNull() ? root.get("description").getAsString() : "";
            String createdAt = root.get("created").getAsString();

            String state = "Unknown";
            List<String> assignees = new ArrayList<>();

            if (root.has("customFields") && !root.get("customFields").isJsonNull()) {
                for (JsonElement el : root.getAsJsonArray("customFields")) {
                    JsonObject cf = el.getAsJsonObject();
                    String name = cf.get("name").getAsString();

                    if ("State".equals(name) && cf.has("value") && !cf.get("value").isJsonNull()) {
                        state = cf.getAsJsonObject("value").get("name").getAsString();
                    } else if ("Assignee".equals(name) && cf.has("value") && !cf.get("value").isJsonNull()) {
                        JsonElement val = cf.get("value");
                        if (val.isJsonArray()) {
                            for (JsonElement a : val.getAsJsonArray()) {
                                assignees.add(a.getAsJsonObject().get("login").getAsString());
                            }
                        } else {
                            assignees.add(val.getAsJsonObject().get("login").getAsString());
                        }
                    }
                }
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
                    .header("Authorization", "Bearer " + this.token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
