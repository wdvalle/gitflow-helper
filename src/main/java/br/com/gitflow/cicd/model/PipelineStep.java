package br.com.gitflow.cicd.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PipelineStep {
    private final String id;
    private final String name;
    private PipelineStatus status;
    private long durationMillis;
    private long startTimeMillis;
    private final StringBuilder logBuilder = new StringBuilder();

    public PipelineStep(@NotNull String id, @NotNull String name, @NotNull PipelineStatus status, long durationMillis) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.durationMillis = durationMillis;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull PipelineStatus status) {
        this.status = status;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public @NotNull String getLog() {
        return logBuilder.toString();
    }

    public void setLog(@Nullable String log) {
        logBuilder.setLength(0);
        if (log != null) {
            logBuilder.append(log);
        }
    }

    public void appendLog(@NotNull String text) {
        if (logBuilder.length() > 0) {
            logBuilder.append("\n");
        }
        logBuilder.append(text);
    }

    public String getFormattedDuration() {
        if (durationMillis <= 0) {
            return "";
        }
        long seconds = durationMillis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSec = seconds % 60;
        return String.format("%dm %02ds", minutes, remainingSec);
    }
}
