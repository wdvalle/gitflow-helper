package br.com.gitflow.cicd.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipelineRun {
    private String id;
    private String name;
    private String branch;
    private String commit;
    private PipelineStatus status = PipelineStatus.UNKNOWN;
    private long durationMillis;
    private long startTimeMillis;
    private String webUrl;
    private final List<PipelineStage> stages = new ArrayList<>();

    public PipelineRun() {
    }

    public PipelineRun(String id, String name, PipelineStatus status) {
        this.id = id;
        this.name = name;
        this.status = status != null ? status : PipelineStatus.UNKNOWN;
    }

    public @Nullable String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public @Nullable String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
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

    public @Nullable String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public @NotNull List<PipelineStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    public void addStage(@NotNull PipelineStage stage) {
        this.stages.add(stage);
    }

    public void setStages(@Nullable List<PipelineStage> stages) {
        this.stages.clear();
        if (stages != null) {
            this.stages.addAll(stages);
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
