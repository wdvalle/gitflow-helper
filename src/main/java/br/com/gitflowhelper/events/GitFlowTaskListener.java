package br.com.gitflowhelper.events;

import com.intellij.util.messages.Topic;

public interface GitFlowTaskListener {
    Topic<GitFlowTaskListener> TOPIC = Topic.create("GitFlow Task Changes", GitFlowTaskListener.class);

    void tasksChanged();
}
