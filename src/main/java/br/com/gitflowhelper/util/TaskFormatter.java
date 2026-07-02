package br.com.gitflowhelper.util;

import br.com.gitflow.tracker.GFTask;
import br.com.gitflow.tracker.IssueResponse;
import br.com.gitflow.tracker.IssueTrackerConnector;
import br.com.gitflow.tracker.TrackerFactory;
import com.intellij.openapi.project.Project;

import java.util.Optional;

public class TaskFormatter {
    private Optional<IssueTrackerConnector> trackerConnector = Optional.empty();
    private final Project project;

    public TaskFormatter(Project project) {
        this.project = project;
    }

    public String formatTaskDetail(GFTask task) {
        // Adds padding/margin to the body tag so the text does not stick to the panel borders
        StringBuilder sb = new StringBuilder("<html><body style='font-family: sans-serif; font-size: 11pt; margin: 12px;'>");

        // Task header (ID + Summary) highlighted
        sb.append("<h2 style='margin-top: 0; margin-bottom: 8px; font-weight: normal;'>");
        sb.append("<b>").append(task.getPresentableId()).append("</b> &mdash; ");
        sb.append(task.getSummary());
        sb.append("</h2>");

        // Subtle separator line
        sb.append("<hr style='border: 0; border-top: 1px solid #888888; margin-bottom: 12px;'>");

        // Uses a table to perfectly align labels and values
        sb.append("<table border='0' cellpadding='4' cellspacing='0'>");

        // Inserts custom data (Assignee, State, Description, etc.)
        appendMyIssueData(sb, task);

        sb.append("</table>");
        sb.append("</body></html>");

        return sb.toString();
    }

    private void appendMyIssueData(StringBuilder sb, GFTask gfTask) {
        //assumes only one issue tracker per project (why more than one?)
        if (trackerConnector.isEmpty())
            trackerConnector = TrackerFactory.getConnector(project, gfTask.getTask());
        if (gfTask.getAssignees().isEmpty()) {
            trackerConnector.ifPresent(connector -> {
                String issueId = gfTask.getLocalId();
                if (issueId.isEmpty()) issueId = gfTask.getId();
                IssueResponse issue = connector.getIssue(issueId);
                if (issue != null) {
                    gfTask.setAssignees(issue.getAssigneesAsString());
                }
            });
        }
        appendToTable(sb, "Assignees", gfTask.getAssignees());
        appendToTable(sb, "State", gfTask.getState());
        appendToTable(sb, "Description", gfTask.getDescriptionAsHtml());

        if (gfTask.getIssueUrl() != null) {
            appendLinkToTable(sb, "URL", gfTask.getIssueUrl());
        }
    }

    private void appendLinkToTable(StringBuilder sb, String label, String value) {
        String link = "<a href=\""+value+"\">"+value+"</a>";
        appendToTable(sb, label, link);
    }

    private void appendToTable(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append("<tr>");
            sb.append("<td valign='top' style='color: #888888;'><b>").append(label).append(":</b></td>");
            sb.append("<td valign='top'>").append(value).append("</td>");
            sb.append("</tr>");
        }
    }
}
