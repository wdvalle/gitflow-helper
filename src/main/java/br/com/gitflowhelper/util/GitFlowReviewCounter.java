package br.com.gitflowhelper.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;

public final class GitFlowReviewCounter {

    public static final String COUNTER_KEY = "br.com.gitflowhelper.review.counter";

    private GitFlowReviewCounter() {
    }

    public static long getCounter() {
        if (ApplicationManager.getApplication() == null) return 0L;
        PropertiesComponent props = PropertiesComponent.getInstance();
        if (props == null) return 0L;
        return props.getLong(COUNTER_KEY, 0L);
    }

    public static void setCounter(long value) {
        if (ApplicationManager.getApplication() == null) return;
        PropertiesComponent props = PropertiesComponent.getInstance();
        if (props != null) {
            props.setValue(COUNTER_KEY, String.valueOf(value));
        }
    }

    public static long incrementCounter() {
        long next = getCounter() + 1;
        setCounter(next);
        return next;
    }
}
