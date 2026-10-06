package br.com.gitflow.cicd.model;

import com.intellij.ui.JBColor;

import java.awt.Color;

public enum PipelineStatus {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    SUCCESS("Success"),
    FAILED("Failed"),
    ABORTED("Aborted"),
    SKIPPED("Skipped"),
    PAUSED("Paused"),
    UNKNOWN("Unknown");

    private final String displayName;

    PipelineStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRunning() {
        return this == IN_PROGRESS;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == ABORTED || this == SKIPPED;
    }

    public JBColor getColor() {
        switch (this) {
            case SUCCESS:
                return new JBColor(new Color(46, 139, 87), new Color(98, 181, 67));
            case FAILED:
                return new JBColor(new Color(210, 60, 60), new Color(230, 80, 80));
            case IN_PROGRESS:
                return new JBColor(new Color(0, 120, 215), new Color(88, 157, 246));
            case ABORTED:
            case SKIPPED:
            case PAUSED:
            case NOT_STARTED:
            case UNKNOWN:
            default:
                return new JBColor(new Color(128, 128, 128), new Color(150, 150, 150));
        }
    }

    public static PipelineStatus fromJenkinsStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return UNKNOWN;
        }
        String s = status.trim().toUpperCase();
        switch (s) {
            case "SUCCESS":
                return SUCCESS;
            case "IN_PROGRESS":
            case "BUILDING":
                return IN_PROGRESS;
            case "FAILED":
            case "FAILURE":
                return FAILED;
            case "ABORTED":
                return ABORTED;
            case "SKIPPED":
            case "NOT_BUILT":
                return SKIPPED;
            case "PAUSED_PENDING_INPUT":
                return PAUSED;
            default:
                return UNKNOWN;
        }
    }

    public static PipelineStatus fromGitLabStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return UNKNOWN;
        }
        String s = status.trim().toLowerCase();
        switch (s) {
            case "success":
                return SUCCESS;
            case "running":
            case "pending":
            case "created":
                return IN_PROGRESS;
            case "failed":
                return FAILED;
            case "canceled":
            case "cancelled":
                return ABORTED;
            case "skipped":
                return SKIPPED;
            case "manual":
                return PAUSED;
            default:
                return UNKNOWN;
        }
    }

    public static PipelineStatus fromGitHubStatus(String status, String conclusion) {
        if (conclusion != null && !conclusion.trim().isEmpty()) {
            String c = conclusion.trim().toLowerCase();
            switch (c) {
                case "success":
                    return SUCCESS;
                case "failure":
                case "timed_out":
                    return FAILED;
                case "cancelled":
                    return ABORTED;
                case "skipped":
                case "neutral":
                    return SKIPPED;
            }
        }
        if (status != null) {
            String s = status.trim().toLowerCase();
            if ("in_progress".equals(s) || "queued".equals(s) || "waiting".equals(s)) {
                return IN_PROGRESS;
            } else if ("completed".equals(s)) {
                return SUCCESS;
            }
        }
        return UNKNOWN;
    }
}
