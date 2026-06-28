package br.com.gitflow.tracker.old;

import com.intellij.tasks.Task;

public interface IssueTrackerConnector {
    void markAsStarted(Task task) throws Exception;
    void markAsFinished(Task task) throws Exception;
}
