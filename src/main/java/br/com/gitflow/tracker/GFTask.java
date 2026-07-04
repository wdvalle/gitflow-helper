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

    public GFTask(Task task) {
        System.out.println("GFTask: " + task.getClass());
        this.task = task;

        try {
            // Substitua 'seuObjeto' pela variável que você está inspecionando
            Object obj = task;

            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();

            System.out.println("--- Atributos Privados de: " + obj.getClass().getSimpleName() + " ---");

            for (java.lang.reflect.Field field : fields) {
                // Filtra apenas o que for privado
//                if (java.lang.reflect.Modifier.isPrivate(field.getModifiers())) {
                    String nome = field.getName();
                    String tipo = field.getType().getCanonicalName();

                    System.out.println("Nome: " + nome + " | Tipo: " + tipo);
//                }
            }
            System.out.println("------------------------------------------------");
        } catch (Exception e) {
            System.out.println("Erro ao inspecionar o objeto: " + e.getMessage());
        }
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
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
        return task.getIcon();
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
        Object value = myIssue != null ? getField(myIssue, fieldName) : null;
        return formatValue(value);
    }

    private Object getField(Object obj, String fieldName) {
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
            Object name = getField(value, "name");
            if (name == null) name = getField(value, "username");
            if (name == null) name = getField(value, "login");
            if (name == null) name = getField(value, "title");
            if (name != null) return name.toString();
        }
        return str;
    }

}
