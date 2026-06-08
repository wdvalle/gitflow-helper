package br.com.gitflowhelper.dialog;

import com.intellij.tasks.Task;

public class NameResponse {

    private String name;
    private Boolean pushOnFinish;
    private Task selectedTask;
    private boolean activateTask;

    public NameResponse(String name, Boolean pushOnFinish) {
        this(name, pushOnFinish, null, false);
    }

    public NameResponse(String name, Boolean pushOnFinish, Task selectedTask) {
        this(name, pushOnFinish, selectedTask, true);
    }

    public NameResponse(String name, Boolean pushOnFinish, Task selectedTask, boolean activateTask) {
        this.name = name;
        this.pushOnFinish = pushOnFinish;
        this.selectedTask = selectedTask;
        this.activateTask = activateTask;
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

    public Task getSelectedTask() {
        return selectedTask;
    }

    public void setSelectedTask(Task selectedTask) {
        this.selectedTask = selectedTask;
    }

    public boolean isActivateTask() {
        return activateTask;
    }

    public void setActivateTask(boolean activateTask) {
        this.activateTask = activateTask;
    }
}
