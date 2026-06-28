package br.com.gitflow.tracker;

import java.util.List;

public class IssueResponse {
    private final String id;
    private final String title;
    private final String description;
    private final List<String> assignees;
    private final String url;
    private final String createdAt;
    private final String state;

    public IssueResponse(String id, String title, String description, List<String> assignees, String url, String createdAt, String state) {
        this.id = id;
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.assignees = assignees;
        this.url = url != null ? url : "";
        this.createdAt = createdAt != null ? createdAt : "";
        this.state = state != null ? state : "";
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getAssignees() { return assignees; }
    public String getUrl() { return url; }
    public String getCreatedAt() { return createdAt; }
    public String getState() { return state; }

    public String getAssigneesAsString() {
        return assignees != null ? String.join(", ", assignees) : "";
    }
}
