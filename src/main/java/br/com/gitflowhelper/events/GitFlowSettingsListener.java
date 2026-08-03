package br.com.gitflowhelper.events;

import com.intellij.util.messages.Topic;

public interface GitFlowSettingsListener {
    Topic<GitFlowSettingsListener> TOPIC = Topic.create("GitFlowHelper.SettingsChanged", GitFlowSettingsListener.class);

    void settingsChanged();
}
