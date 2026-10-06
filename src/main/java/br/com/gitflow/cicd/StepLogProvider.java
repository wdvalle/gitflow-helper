package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Functional callback to provide or fetch pipeline logs on demand for a given step.
 */
@FunctionalInterface
public interface StepLogProvider {

    /**
     * Retrieves or fetches the log content for the specified step.
     *
     * @param step the pipeline step to retrieve logs for
     * @return the log text, or null if no log is available
     */
    @Nullable
    String getStepLog(@NotNull PipelineStep step);
}
