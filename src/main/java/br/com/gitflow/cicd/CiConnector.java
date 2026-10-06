package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;
import java.util.List;

/**
 * Common interface for CI/CD server connectors (Jenkins, GitLab CI, GitHub Actions, etc.).
 */
public interface CiConnector {

    /**
     * Name of the CI/CD platform (e.g. "Jenkins", "GitLab", "GitHub").
     */
    @NotNull
    String getPlatformName();

    /**
     * Configured base URL for the CI/CD server or job.
     */
    @NotNull
    String getBaseUrl();

    /**
     * Web URL for the current build or pipeline.
     */
    @Nullable
    String getBuildUrl();

    /**
     * Returns the identifier of the current execution (e.g. build number for Jenkins,
     * pipeline ID for GitLab, workflow run ID for GitHub Actions), or null if not yet determined.
     */
    @Nullable
    default String getCurrentBuildId() {
        return null;
    }

    /**
     * Flags whether a new build was triggered by the plugin and we are waiting for the CI server
     * to allocate and report a new pipeline/build execution.
     */
    void setBuildTriggered(boolean triggered);

    /**
     * Returns true if a new build execution was triggered and is being actively waited on.
     */
    default boolean isBuildTriggered() {
        return false;
    }

    /**
     * Returns true if the connector is waiting for the remote CI server to queue and start
     * the new build before log streaming or stage tracking begins.
     */
    default boolean isWaitingForNewBuild() {
        return false;
    }

    /**
     * Triggers a new build or pipeline on the remote server.
     */
    HttpResponse<String> triggerBuild() throws Exception;

    /**
     * Tests connectivity and authentication against the CI/CD server.
     *
     * @return true if the connection and credentials are valid
     * @throws Exception if network fails, host is unreachable, or credentials are invalid
     */
    default boolean testConnection() throws Exception {
        return true;
    }

    /**
     * Fetches the current pipeline execution state including stages and steps.
     *
     * @return current {@link PipelineRun} representation or null if not yet available.
     */
    @Nullable
    PipelineRun fetchPipelineRun();

    /**
     * Fetches the next available chunk of log output from the server.
     *
     * @return HTML or formatted text chunk of logs.
     */
    @NotNull
    String fetchNextChunk();

    /**
     * Returns true while the pipeline is running or new log output is available.
     */
    boolean hasMoreData();

    /**
     * Stops local monitoring of the remote execution.
     */
    void stop();

    /**
     * Sends an abort/cancellation signal to the remote CI server to stop the running job/pipeline.
     */
    default void abortPipeline() {
        stop();
    }

    /**
     * Sends an abort/cancellation signal for a specific target build/pipeline ID.
     *
     * @param buildId the build number, pipeline ID, or run ID to cancel
     */
    default void abortPipeline(@NotNull String buildId) {
        abortPipeline();
    }

    /**
     * Returns the complete blueprint stages for the pipeline (e.g. from last successful build or known pipeline definition),
     * ensuring a complete diagram even when the latest build failed or was interrupted early.
     */
    default @NotNull List<PipelineStage> fetchBlueprintStages() {
        PipelineRun run = fetchPipelineRun();
        return run != null ? run.getStages() : List.of();
    }

    /**
     * Fetches the console or pipeline execution log specifically for a given step/node ID.
     *
     * @param stepId the unique identifier of the step or flow node
     * @return the raw or formatted log text of the step, or null if not available
     */
    @Nullable
    default String fetchStepLog(@NotNull String stepId) {
        return null;
    }
}
