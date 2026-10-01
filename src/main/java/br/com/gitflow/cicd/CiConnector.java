package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;

/**
 * Common interface for CI/CD server connectors (Jenkins, GitLab CI, GitHub Actions).
 */
public interface CiConnector {

    /**
     * Name of the CI/CD platform (e.g. "Jenkins", "GitLab", "GitHub").
     */
    @NotNull
    String getPlatformName();

    /**
     * Web URL for the current build or pipeline.
     */
    @Nullable
    String getBuildUrl();

    /**
     * Triggers a new build or pipeline on the remote server.
     */
    HttpResponse<String> triggerBuild() throws Exception;

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
     * Stops monitoring or cancels the remote execution if supported.
     */
    void stop();

    /**
     * Sends an abort signal to the remote CI server to cancel the running job.
     */
    default void abortPipeline() {
        stop();
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
