package br.com.gitflowhelper;

import br.com.gitflowhelper.util.GitFlowReviewCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitFlowReviewCounterTest {

    @Test
    public void testCounterSafeWhenNoAppRunning() {
        assertDoesNotThrow(GitFlowReviewCounter::getCounter);
        assertDoesNotThrow(() -> GitFlowReviewCounter.setCounter(10L));
        assertDoesNotThrow(GitFlowReviewCounter::incrementCounter);
        assertEquals(0L, GitFlowReviewCounter.getCounter());
    }
}
