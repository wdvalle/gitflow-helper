package br.com.gitflow.cicd.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipelineStage {
    private final String id;
    private final String name;
    private PipelineStatus status;
    private long durationMillis;
    private long startTimeMillis;
    private final List<PipelineStep> steps;

    public PipelineStage(@NotNull String id, @NotNull String name, @NotNull PipelineStatus status, long durationMillis) {
        this(id, name, status, durationMillis, new ArrayList<>());
    }

    public PipelineStage(@NotNull String id, @NotNull String name, @NotNull PipelineStatus status, long durationMillis, List<PipelineStep> steps) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.durationMillis = durationMillis;
        this.steps = steps != null ? steps : new ArrayList<>();
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

    public @NotNull List<PipelineStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public void addStep(@NotNull PipelineStep step) {
        this.steps.add(step);
    }

    public void setSteps(List<PipelineStep> steps) {
        this.steps.clear();
        if (steps != null) {
            this.steps.addAll(steps);
        }
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
