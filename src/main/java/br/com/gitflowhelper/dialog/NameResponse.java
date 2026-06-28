package br.com.gitflowhelper.dialog;

import br.com.gitflow.tracker.GFTask;
import com.intellij.tasks.Task;

public class NameResponse {

    private String name;
    private Boolean pushOnFinish;
    private GFTask selectedTask;
    private boolean activateTask;
    private String username;

    public NameResponse(String name, Boolean pushOnFinish) {
        this(name, pushOnFinish, null, false, null);
    }

    public NameResponse(String name, Boolean pushOnFinish, GFTask selectedTask) {
        this(name, pushOnFinish, selectedTask, true, null);
    }

    public NameResponse(String name, Boolean pushOnFinish, GFTask selectedTask, boolean activateTask, String username) {
        this.name = name;
        this.pushOnFinish = pushOnFinish;
        this.selectedTask = selectedTask;
        this.activateTask = activateTask;
        this.username = username;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Boolean getPushOnFinish() {
        return pushOnFinish;
    }
    public void setPushOnFinish(Boolean pushOnFinish) {
        this.pushOnFinish = pushOnFinish;
    }

    public GFTask getSelectedTask() {
        return selectedTask;
    }

    public void setSelectedTask(GFTask selectedTask) {
        this.selectedTask = selectedTask;
    }

    public boolean isActivateTask() {
        return activateTask;
    }

    public void setActivateTask(boolean activateTask) {
        this.activateTask = activateTask;
    }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }
}
