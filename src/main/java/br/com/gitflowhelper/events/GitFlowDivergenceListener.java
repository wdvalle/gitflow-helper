package br.com.gitflowhelper.events;

import br.com.gitflowhelper.service.DivergenceInfo;
import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface GitFlowDivergenceListener {
    Topic<GitFlowDivergenceListener> TOPIC = Topic.create("GitFlowHelper.DivergenceChanged", GitFlowDivergenceListener.class);

    void divergenceUpdated(DivergenceInfo info);
}
