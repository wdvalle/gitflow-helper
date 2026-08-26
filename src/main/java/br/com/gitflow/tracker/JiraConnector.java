package br.com.gitflow.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getUserId(String username) {
        if (username == null || username.isBlank()) return username;
        try {
            String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/rest/api/3/user/search?query=" + encodedUser))
                    .header("Authorization", getAuthHeader())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonElement jsonElement = JsonParser.parseString(response.body());
                if (jsonElement.isJsonArray() && !jsonElement.getAsJsonArray().isEmpty()) {
                    JsonObject firstUser = jsonElement.getAsJsonArray().get(0).getAsJsonObject();
                    if (firstUser.has("accountId") && !firstUser.get("accountId").isJsonNull()) {
                        return firstUser.get("accountId").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return username;
    }

    @Override
    public boolean startIssue(String issueId) {
        String transitionId = findTransitionId(issueId, new String[]{
                "in progress", "in dev", "in development", "start", "doing",
                "em andamento", "em desenvolvimento", "desenvolvimento", "start progress"
        }, "21");
        return postRequest("/rest/api/3/issue/" + issueId + "/transitions", "{\"transition\":{\"id\":\"" + transitionId + "\"}}");
    }

    @Override
    public boolean assignIssue(String issueId, String user) {
        if (user == null || user.isBlank()) {
            return putRequest("/rest/api/3/issue/" + issueId + "/assignee", "{\"accountId\":null}");
        }
        String accountId = getUserId(user);
        JsonObject body = new JsonObject();
        body.addProperty("accountId", accountId);
        return putRequest("/rest/api/3/issue/" + issueId + "/assignee", gson.toJson(body));
    }

    @Override
    public boolean closeIssue(String issueNumber, String issueId) {
        String transitionId = findTransitionId(issueId, new String[]{
                "done", "closed", "close", "resolve", "resolved", "complete",
                "completed", "fechado", "concluído", "concluido", "resolvido", "finalizado"
        }, "31");
        return postRequest("/rest/api/3/issue/" + issueId + "/transitions", "{\"transition\":{\"id\":\"" + transitionId + "\"}}");
    }

    @Override
    public IssueResponse getIssue(String issueId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/rest/api/3/issue/" + issueId))
                    .header("Authorization", getAuthHeader())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return parseIssueJson(root);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Executes a JQL search using the updated Jira REST API v3 /search/jql endpoint (CHANGE-2046).
     */
    public List<IssueResponse> searchIssues(String jql, int maxResults) {
        List<IssueResponse> result = new ArrayList<>();
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("jql", jql != null ? jql : "");
            payload.addProperty("maxResults", maxResults > 0 ? maxResults : 50);

            JsonArray fields = new JsonArray();
            fields.add("summary");
            fields.add("description");
            fields.add("status");
            fields.add("assignee");
            fields.add("created");
            fields.add("key");
            payload.add("fields", fields);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/rest/api/3/search/jql"))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (root.has("issues") && root.get("issues").isJsonArray()) {
                    for (JsonElement item : root.getAsJsonArray("issues")) {
                        IssueResponse issue = parseIssueJson(item.getAsJsonObject());
                        if (issue != null) {
                            result.add(issue);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static String extractJiraIssueKey(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) return "";
        try {
            if (urlOrKey.startsWith("http://") || urlOrKey.startsWith("https://")) {
                URI uri = new URI(urlOrKey);
                String path = uri.getPath();
                if (path != null && path.contains("/browse/")) {
                    String key = path.substring(path.indexOf("/browse/") + "/browse/".length());
                    if (key.contains("/")) {
                        key = key.substring(0, key.indexOf("/"));
                    }
                    return key;
                } else if (path != null && path.contains("/issues/")) {
                    String key = path.substring(path.indexOf("/issues/") + "/issues/".length());
                    if (key.contains("/")) {
                        key = key.substring(0, key.indexOf("/"));
                    }
                    return key;
                }
            }
        } catch (Exception ignored) {}
        return urlOrKey;
    }

    public static String parseDescription(JsonElement descElement) {
        if (descElement == null || descElement.isJsonNull()) {
            return "";
        }
        if (descElement.isJsonPrimitive()) {
            return descElement.getAsString();
        }
        if (descElement.isJsonObject()) {
            StringBuilder sb = new StringBuilder();
            extractTextFromAdfNode(descElement.getAsJsonObject(), sb);
            return sb.toString().trim();
        }
        return descElement.toString();
    }

    private static void extractTextFromAdfNode(JsonObject node, StringBuilder sb) {
        if (node.has("text") && !node.get("text").isJsonNull()) {
            sb.append(node.get("text").getAsString());
        }
        if (node.has("content") && node.get("content").isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray("content")) {
                if (child.isJsonObject()) {
                    extractTextFromAdfNode(child.getAsJsonObject(), sb);
                    String type = child.getAsJsonObject().has("type") && !child.getAsJsonObject().get("type").isJsonNull()
                            ? child.getAsJsonObject().get("type").getAsString() : "";
                    if ("paragraph".equals(type) || "heading".equals(type) || "listItem".equals(type)) {
                        sb.append("\n");
                    }
                }
            }
        }
    }

    private IssueResponse parseIssueJson(JsonObject root) {
        try {
            JsonObject fields = root.getAsJsonObject("fields");
            String id = root.has("key") ? root.get("key").getAsString() : "";
            String url = this.baseUrl + "/browse/" + id;
            String title = fields.has("summary") && !fields.get("summary").isJsonNull() ? fields.get("summary").getAsString() : "";

            String desc = "";
            if (fields.has("description")) {
                desc = parseDescription(fields.get("description"));
            }

            String createdAt = fields.has("created") && !fields.get("created").isJsonNull() ? fields.get("created").getAsString() : "";
            String state = fields.has("status") && !fields.get("status").isJsonNull()
                    && fields.getAsJsonObject("status").has("name")
                    ? fields.getAsJsonObject("status").get("name").getAsString() : "Unknown";

            List<String> assignees = new ArrayList<>();
            if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
                JsonObject assigneeObj = fields.getAsJsonObject("assignee");
                if (assigneeObj.has("displayName") && !assigneeObj.get("displayName").isJsonNull()) {
                    assignees.add(assigneeObj.get("displayName").getAsString());
                } else if (assigneeObj.has("name") && !assigneeObj.get("name").isJsonNull()) {
                    assignees.add(assigneeObj.get("name").getAsString());
                }
            }
            return new IssueResponse(id, title, desc, assignees, url, createdAt, state);
        } catch (Exception e) {
            return null;
        }
    }

    private String findTransitionId(String issueId, String[] targetKeywords, String fallbackId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/rest/api/3/issue/" + issueId + "/transitions"))
                    .header("Authorization", getAuthHeader())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (root.has("transitions") && root.get("transitions").isJsonArray()) {
                    JsonArray transitions = root.getAsJsonArray("transitions");
                    for (JsonElement el : transitions) {
                        JsonObject trans = el.getAsJsonObject();
                        String transId = trans.get("id").getAsString();
                        String name = trans.has("name") && !trans.get("name").isJsonNull() ? trans.get("name").getAsString() : "";
                        String toName = trans.has("to") && trans.getAsJsonObject("to").has("name") && !trans.getAsJsonObject("to").get("name").isJsonNull()
                                ? trans.getAsJsonObject("to").get("name").getAsString() : "";

                        for (String kw : targetKeywords) {
                            if (name.equalsIgnoreCase(kw) || toName.equalsIgnoreCase(kw) ||
                                    name.toLowerCase().contains(kw.toLowerCase()) || toName.toLowerCase().contains(kw.toLowerCase())) {
                                return transId;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return fallbackId;
    }

    private boolean postRequest(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + path))
                    .header("Authorization", getAuthHeader())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            int code = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            return code == 200 || code == 204;
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
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            int code = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            return code == 200 || code == 204;
        } catch (Exception e) {
            return false;
        }
    }
}
