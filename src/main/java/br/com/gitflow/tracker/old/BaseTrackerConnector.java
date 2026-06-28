package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class BaseTrackerConnector implements IssueTrackerConnector {
    protected final String baseUrl;
    protected final String token;
    protected final HttpClient httpClient;

    public BaseTrackerConnector(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    protected HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected String getIssueId(Task task) {
        return task.getPresentableId();
    }

    protected Object getFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    protected java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
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
