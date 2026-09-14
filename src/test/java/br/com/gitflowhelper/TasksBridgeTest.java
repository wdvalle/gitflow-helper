package br.com.gitflowhelper;

import br.com.gitflowhelper.tasks.TasksBridge;
import br.com.gitflowhelper.tasks.TasksBridgeImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TasksBridgeTest {

    @Test
    public void testHasConfiguredServersWithNullProjectReturnsFalse() {
        TasksBridge bridge = new TasksBridgeImpl();
        assertNotNull(bridge);
        assertFalse(bridge.hasConfiguredServers(null));
    }
}
