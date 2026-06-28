package br.com.gitflow.tracker;

import com.intellij.tasks.Task;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GFTask {

    private Task task;
    private String assignees;

    public GFTask(Task task) {
        this.task = task;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getState() {
        return getField("state") != null ? getField("state") : "";
    }

    public String getDescription() {
        return getField("description") != null ? getField("description") : "";
    }

    public String getPresentableId() {
        return task.getPresentableId();
    }

    public String getSummary() {
        return task.getSummary();
    }

    public Icon getIcon() {
        return task.getIcon();
    }

    public String getIssueUrl() {
        return task.getIssueUrl();
    }

    public String getId() {
        return task.getId();
    }

    public String getLocalId() {
        return getField("localId");
    }

    public String getDescriptionAsHtml() {
        return getDescription().replace("\n", "<br>");
    }

    public void setAssignees(String assignees) {
        this.assignees = assignees;
    }

    public String getAssignees() {
        if (!getField("assignee").isEmpty()) {
            this.assignees = getField("assignee");
        } else if (!getField("assignees").isEmpty()) {
            this.assignees = getField("assignees");
        }
        return formatValue(this.assignees);
    }

    private String getField(String fieldName) {
        Object myIssue = getFieldValue(task, "myIssue");
        Object value = myIssue != null ? getFieldValue(myIssue, fieldName) : null;
        return formatValue(value);
    }

    private Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
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

    private String formatValue(Object value) {
        switch (value) {
            case null -> {
                return "";
            }
            case String s -> {
                return s;
            }
            case Iterable iterable -> {
                List<String> results = new ArrayList<>();
                for (Object item : iterable) {
                    String fmt = formatValue(item);
                    if (fmt != null && !fmt.isEmpty()) results.add(fmt);
                }
                return String.join(", ", results);
            }
            default -> {
            }
        }

        // If it is an object, try to get name or username when toString uses the default format (e.g. com.package.Class@hash)
        String str = value.toString();
        if (str.contains("@") && str.contains(value.getClass().getSimpleName())) {
            Object name = getFieldValue(value, "name");
            if (name == null) name = getFieldValue(value, "username");
            if (name == null) name = getFieldValue(value, "title");
            if (name != null) return name.toString();
        }
        return str;
    }

}
