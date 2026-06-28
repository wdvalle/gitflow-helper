package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JiraConnector extends BaseTrackerConnector {

    public JiraConnector(String baseUrl, String token) {
        super(baseUrl, token);
    }

    @Override
    public void markAsStarted(Task task) throws Exception {
        performTransition(task, "In Progress");
    }

    @Override
    public void markAsFinished(Task task) throws Exception {
        performTransition(task, "Done");
    }

    private void performTransition(Task task, String transitionName) throws Exception {
        String issueKey = task.getNumber();
        String url = String.format("%s/rest/api/2/issue/%s/transitions", baseUrl, issueKey);

        // Simplificação: enviando o nome da transição. 
        // Em implementações reais, o ID da transição é necessário.
        String json = String.format("{\"transition\":{\"id\":\"%s\"}}", getTransitionId(transitionName));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = sendRequest(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Falha ao atualizar issue no Jira: " + response.body());
        }
    }

    private String getTransitionId(String name) {
        // Mapeamento hipotético para fins de exemplo
        if ("In Progress".equals(name)) return "21";
        if ("Done".equals(name)) return "31";
        return "1";
    }
}
