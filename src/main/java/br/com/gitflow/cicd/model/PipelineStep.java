package br.com.gitflow.cicd.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PipelineStep {
    private final String id;
    private final String name;
    private PipelineStatus status;
    private long durationMillis;
    private long startTimeMillis;

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
