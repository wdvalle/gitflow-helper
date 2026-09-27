package br.com.gitflowhelper.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CiServerConfigTest {

    @Test
    public void testDefaultAction() {
        CiServerConfig config = new CiServerConfig();
        assertEquals(CiServerConfig.ACTION_WAIT_BUILD, config.getAction());
        assertTrue(config.isWaitBuild());
        assertFalse(config.isTriggerBuild());
        assertFalse(config.isDoNothing());
    }

    @Test
    public void testSetActionTriggerBuild() {
        CiServerConfig config = new CiServerConfig();
        config.setAction(CiServerConfig.ACTION_TRIGGER_BUILD);
        assertEquals(CiServerConfig.ACTION_TRIGGER_BUILD, config.getAction());
        assertTrue(config.isTriggerBuild());
        assertFalse(config.isWaitBuild());
        assertFalse(config.isDoNothing());
    }

    @Test
    public void testSetActionDoNothing() {
        CiServerConfig config = new CiServerConfig();
        config.setAction(CiServerConfig.ACTION_DO_NOTHING);
        assertEquals(CiServerConfig.ACTION_DO_NOTHING, config.getAction());
        assertTrue(config.isDoNothing());
        assertFalse(config.isTriggerBuild());
        assertFalse(config.isWaitBuild());
    }

    @Test
    public void testSetActionNullOrBlankDefaultsToWaitBuild() {
        CiServerConfig config = new CiServerConfig();
        config.setAction(null);
        assertEquals(CiServerConfig.ACTION_WAIT_BUILD, config.getAction());

        config.setAction("   ");
        assertEquals(CiServerConfig.ACTION_WAIT_BUILD, config.getAction());
    }

    @Test
    public void testCopyIncludesAction() {
        CiServerConfig config = new CiServerConfig();
        config.setCiType("Jenkins");
        config.setCiUrl("https://jenkins.example.com");
        config.setCiLogin("user");
        config.setAction(CiServerConfig.ACTION_TRIGGER_BUILD);

        CiServerConfig copy = config.copy();
        assertEquals(config, copy);
        assertEquals(CiServerConfig.ACTION_TRIGGER_BUILD, copy.getAction());

        config.setAction(CiServerConfig.ACTION_DO_NOTHING);
        copy = config.copy();
        assertEquals(config, copy);
        assertEquals(CiServerConfig.ACTION_DO_NOTHING, copy.getAction());
    }

    @Test
    public void testEqualsAndHashCode() {
        CiServerConfig config1 = new CiServerConfig();
        CiServerConfig config2 = new CiServerConfig();
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());

        config1.setAction(CiServerConfig.ACTION_TRIGGER_BUILD);
        assertNotEquals(config1, config2);

        config2.setAction(CiServerConfig.ACTION_TRIGGER_BUILD);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());

        config1.setAction(CiServerConfig.ACTION_DO_NOTHING);
        assertNotEquals(config1, config2);

        config2.setAction(CiServerConfig.ACTION_DO_NOTHING);
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }
}
