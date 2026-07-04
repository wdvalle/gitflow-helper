package br.com.gitflow.tracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.LocalTaskImpl;
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
    public boolean assignIssue(String issueId, String username) {
        String userId = getUserId(username);
        if (userId == null) return false;
        return putRequest("/api/v4/projects/" + projectId + "/issues/" + issueId, "{\"assignee_ids\":[" + userId + "]}");
    }

    @Override
    public boolean closeIssue(String issueId) {
        return putRequest("/api/v4/projects/" + projectId + "/issues/" + issueId, "{\"state_event\":\"close\", \"remove_labels\":\"Doing\"}");
    }

    private String getUserId(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/v4/users?username=" + username))
                    .header("PRIVATE-TOKEN", this.token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonElement jsonElement = JsonParser.parseString(response.body());
            if (jsonElement.isJsonArray() && !jsonElement.getAsJsonArray().isEmpty()) {
                return jsonElement.getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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
            var httpCode = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            return httpCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractGitLabProjectId(Task task) {
        try {
            // 1. Try to get the project ID via reflection (myIssue field of GitlabTask)
            if (task instanceof LocalTaskImpl) {
                Task theTask = task.getRepository().getIssues(task.getId(), 0, 1, false)[0];
                Object myIssue = getFieldValue(theTask, "myIssue");
                Object projectId = getFieldValue(myIssue, "projectId");
                if (projectId != null) return projectId.toString();
            }
            Object myIssue = getFieldValue(task, "myIssue");
            if (myIssue != null) {
                Object projectId = getFieldValue(myIssue, "projectId");
                if (projectId == null) projectId = getFieldValue(myIssue, "project_id");
                if (projectId != null) return projectId.toString();
            }

            String taskUrl = task.getIssueUrl();
            if (taskUrl == null) return "";

            // 2. Fallback: Extraction via issue URL
            // Example url: https://gitlab.com/my-group/my-project/-/issues/123
            URI uri = new URI(taskUrl);
            String path = uri.getPath();

            // We remove the final part related to issues to get only the namespace
            if (path.contains("/-/issues/")) {
                path = path.substring(0, path.indexOf("/-/issues/"));
            } else if (path.contains("/issues/")) {
                path = path.substring(0, path.indexOf("/issues/"));
            }

            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // GitLab accepts the encoded "Namespace/Project" instead of the numeric ID!
            return path;
        } catch (Exception e) {
            return "";
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
