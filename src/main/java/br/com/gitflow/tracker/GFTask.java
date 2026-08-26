package br.com.gitflow.tracker;

import com.intellij.lang.javascript.boilerplate.GithubProjectGeneratorPeer;
import com.intellij.tasks.Task;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GFTask {

    private Task task;
    private String assignees;
    private Icon vendorIcon;

    public GFTask(Task task) {
        this.task = task;
        if (task != null) {
            Icon rawIcon = task.getIcon();
            Icon unwrapped = unwrapDelegateIcon(rawIcon);
            this.vendorIcon = unwrapped != null ? unwrapped : rawIcon;
        }
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
        if (task != null && this.vendorIcon == null) {
            Icon rawIcon = task.getIcon();
            Icon unwrapped = unwrapDelegateIcon(rawIcon);
            this.vendorIcon = unwrapped != null ? unwrapped : rawIcon;
        }
    }

    public String getState() {
        String state = getValue("state");
        if (state.isEmpty()) {
            state = getValue("status");
        }
        return state;
    }

    public String getDescription() {
        if (task.getDescription() != null) {
            return task.getDescription();
        }
        String description = getValue("description");
        if (description.isEmpty()) {
            description = getValue("body");
        }
        return description;
    }

    public String getPresentableId() {
        return task.getPresentableId();
    }

    public String getSummary() {
        return task.getSummary();
    }

    public Icon getIcon() {
        if (task != null) {
            Icon current = task.getIcon();
            if (current != null) {
                // For Jira tasks, the icon is a DeferredIcon wrapping the real URL-based
                // icon inside scaledDelegateIcon / delegateIcon fields. Extract and cache
                // the innermost concrete icon so it is not lost when the deferred wrapper
                // re-evaluates and produces an invalid result.
                Icon unwrapped = unwrapDelegateIcon(current);
                if (unwrapped != null) {
                    this.vendorIcon = unwrapped;
                    return unwrapped;
                }
                return current;
            }
            if (task.getRepository() != null) {
                Icon repoIcon = task.getRepository().getIcon();
                if (repoIcon != null) {
                    return repoIcon;
                }
            }
        }
        return vendorIcon;
    }

    /**
     * Unwraps an IntelliJ {@code DeferredIcon} (or similar delegate wrapper) by
     * reflectively reading the {@code scaledDelegateIcon} or {@code delegateIcon}
     * fields until a concrete, non-delegate {@link Icon} is reached.
     *
     * @return the innermost delegate icon, or {@code null} if unwrapping is not
     *         possible (e.g. the icon is already a concrete icon).
     */
    private Icon unwrapDelegateIcon(Icon icon) {
        Icon result = null;
        Icon current = icon;
        int maxDepth = 10; // safeguard against infinite loops
        while (current != null && maxDepth-- > 0) {
            Object delegate = getField(current, "scaledDelegateIcon");
            if (delegate == null) {
                delegate = getField(current, "delegateIcon");
            }
            if (delegate instanceof Icon delegateIcon && delegateIcon != current) {
                result = delegateIcon;
                current = delegateIcon;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Returns {@code true} when the underlying task originates from a Jira repository.
     */
    public boolean isJira() {
        if (task == null) return false;
        try {
            if (task.getRepository() != null
                    && task.getRepository().getRepositoryType() != null
                    && "Jira".equalsIgnoreCase(task.getRepository().getRepositoryType().getName())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        // Fallback: check for the myJiraIssue field via reflection
        return getField(task, "myJiraIssue") != null;
    }

    public String getIssueUrl() {
        return task.getIssueUrl();
    }

    public String getId() {
        return task.getId();
    }

    public String getLocalId() {
        String localId = getValue("localId");
        if (localId.isEmpty()) {
            localId = getValue("iid");
        }
        if (localId.isEmpty()) {
            localId = getValue("number");
        }
        if (localId.isEmpty()) {
            localId = getValue("id");
        }
        if (localId.isEmpty()) {
            localId = getValue("myId");
        }
        return localId;
    }

    public String getDescriptionAsHtml() {
        return getDescription().replace("\n", "<br>");
    }

    public void setAssignees(String assignees) {
        this.assignees = assignees;
    }

    public String getAssignees() {
        if (this.assignees != null) {
            return this.assignees;
        }
        String value = getValue("assignee");
        if (value.isEmpty()) {
            value = getValue("assignees");
        }
        if (!value.isEmpty()) {
            this.assignees = value;
        }
        return this.assignees != null ? this.assignees : "";
    }

    private String getValue(String fieldName) {
        Object myIssue = getField(task, "myIssue");
        if (myIssue == null) {
            myIssue = getField(task, "val$issue");
        }
        if (myIssue == null) {
            myIssue = getField(task, "myJiraIssue");
        }
        if (myIssue == null) {
            myIssue = task;
        }
        Object value = myIssue != null ? getField(myIssue, fieldName) : null;
        return formatValue(value);
    }

    private Object getField(Object obj, String fieldName) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            //intellij developer is joking me... boolean? serious?!
            if (field != null && !isBoolean(field)) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isBoolean(Field field) {
        Class<?> tipo = field.getType();
        return tipo.equals(boolean.class) || tipo.equals(Boolean.class);
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
            Object name = getField(value, "name");
            if (name == null) name = getField(value, "username");
            if (name == null) name = getField(value, "login");
            if (name == null) name = getField(value, "title");
            if (name != null) return name.toString();
        }
        return str;
    }

}
