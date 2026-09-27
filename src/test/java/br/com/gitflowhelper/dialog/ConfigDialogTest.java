package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.CiServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigDialogTest {

    @Test
    public void testHelpConstants() {
        assertNotNull(ConfigDialog.HELP_TRIGGER_BUILD);
        assertNotNull(ConfigDialog.HELP_WAIT_BUILD);
        assertNotNull(ConfigDialog.HELP_DO_NOTHING);
        assertTrue(ConfigDialog.HELP_TRIGGER_BUILD.contains("trigger the build on the CI/CD server upon feature finish"));
        assertTrue(ConfigDialog.HELP_WAIT_BUILD.contains("wait for Git to trigger the build and then collect the logs"));
        assertTrue(ConfigDialog.HELP_DO_NOTHING.contains("do nothing upon feature finish"));
    }

    @Test
    public void testRadioValues() {
        assertEquals("Trigger build on feature finish", CiServerConfig.ACTION_TRIGGER_BUILD);
        assertEquals("Wait build start and listen logs", CiServerConfig.ACTION_WAIT_BUILD);
        assertEquals("Do nothing", CiServerConfig.ACTION_DO_NOTHING);
    }
}
