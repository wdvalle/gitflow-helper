package br.com.gitflow.tracker.old;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import java.util.Optional;

/**
 * Exemplo de como utilizar o sistema de conectores para o GitLab.
 */
public class GitLabExample {

    public void runExample(Project project, Task selectedTask) {
        // 1. Obter o conector apropriado através da Factory
        Optional<IssueTrackerConnector> connectorOpt = TrackerFactory.getConnector(project, selectedTask);

        connectorOpt.ifPresent(connector -> {
            try {
                // 2. Marcar a issue como iniciada (ex: quando começar uma feature)
                System.out.println("Marcando issue como iniciada no GitLab...");
                connector.markAsStarted(selectedTask);

                // ... realizar trabalho ...

                // 3. Marcar a issue como finalizada (ex: ao finalizar a feature)
                System.out.println("Marcando issue como finalizada no GitLab...");
                connector.markAsFinished(selectedTask);

            } catch (Exception e) {
                System.err.println("Erro ao interagir com o GitLab: " + e.getMessage());
                e.printStackTrace();
            }
        });

        if (connectorOpt.isEmpty()) {
            System.out.println("Nenhum conector GitLab encontrado para a task selecionada.");
        }
    }

    /**
     * Exemplo de uso manual caso não queira usar a Factory (precisando da URL e Token).
     */
    public void runManualExample(Task task) {
        String gitlabUrl = "https://gitlab.com";
        String privateToken = "seu_token_aqui";

        IssueTrackerConnector connector = new GitLabConnector(gitlabUrl, privateToken);
        try {
            connector.markAsStarted(task);
            // ...
            connector.markAsFinished(task);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
