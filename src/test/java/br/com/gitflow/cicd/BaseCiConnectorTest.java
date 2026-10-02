package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BaseCiConnectorTest {

    private static class DummyCiConnector extends BaseCiConnector {
        public DummyCiConnector(String url, String login, String token) {
            super(url, login, token);
        }

        @Override
        public @NotNull String getPlatformName() {
            return "Dummy";
        }

        @Override
        public @Nullable String getBuildUrl() {
            return normalizedBase + "/1";
        }

        @Override
        public HttpResponse<String> triggerBuild() {
            return null;
        }

        @Override
        public @Nullable PipelineRun fetchPipelineRun() {
            return null;
        }

        @Override
        public @NotNull String fetchNextChunk() {
            return "";
        }
    }

    @Test
    public void testIsValidHttpUrl() {
        assertTrue(BaseCiConnector.isValidHttpUrl("http://localhost:8080"));
        assertTrue(BaseCiConnector.isValidHttpUrl("https://jenkins.example.com/job/test"));
        assertTrue(BaseCiConnector.isValidHttpUrl("  https://gitlab.com/repo  "));

        assertFalse(BaseCiConnector.isValidHttpUrl(null));
        assertFalse(BaseCiConnector.isValidHttpUrl(""));
        assertFalse(BaseCiConnector.isValidHttpUrl("   "));
        assertFalse(BaseCiConnector.isValidHttpUrl("ftp://server"));
        assertFalse(BaseCiConnector.isValidHttpUrl("git@github.com:repo/repo.git"));
        assertFalse(BaseCiConnector.isValidHttpUrl("just-a-name"));
    }

    @Test
    public void testNormalizeBaseUrl() {
        assertEquals("http://jenkins.example.com/job/my-job",
                BaseCiConnector.normalizeBaseUrl("http://jenkins.example.com/job/my-job/"));
        assertEquals("http://jenkins.example.com/job/my-job",
                BaseCiConnector.normalizeBaseUrl("http://jenkins.example.com/job/my-job///"));
        assertEquals("http://jenkins.example.com",
                BaseCiConnector.normalizeBaseUrl("http://jenkins.example.com"));
    }

    @Test
    public void testIsNumericBuildNumber() {
        assertTrue(BaseCiConnector.isNumericBuildNumber("1"));
        assertTrue(BaseCiConnector.isNumericBuildNumber("42"));
        assertTrue(BaseCiConnector.isNumericBuildNumber("  999 \n"));

        assertFalse(BaseCiConnector.isNumericBuildNumber(null));
        assertFalse(BaseCiConnector.isNumericBuildNumber(""));
        assertFalse(BaseCiConnector.isNumericBuildNumber("lastBuild"));
        assertFalse(BaseCiConnector.isNumericBuildNumber("<html>404</html>"));
        assertFalse(BaseCiConnector.isNumericBuildNumber("12a"));
    }

    @Test
    public void testIsHtmlResponse() {
        assertTrue(BaseCiConnector.isHtmlResponse("text/html; charset=utf-8", ""));
        assertTrue(BaseCiConnector.isHtmlResponse(null, "<!DOCTYPE html><html><body>Error</body></html>"));
        assertTrue(BaseCiConnector.isHtmlResponse("application/xhtml+xml", "<html><head></head></html>"));

        assertFalse(BaseCiConnector.isHtmlResponse("application/json", "{\"number\": 1}"));
        assertFalse(BaseCiConnector.isHtmlResponse("text/plain", "Line 1\nLine 2"));
        assertFalse(BaseCiConnector.isHtmlResponse(null, "42"));
    }

    @Test
    public void testSafeParseJsonObject() {
        // Valid JSON object
        JsonObject obj = BaseCiConnector.safeParseJsonObject("{\"name\": \"build\", \"id\": 10}");
        assertNotNull(obj);
        assertEquals("build", obj.get("name").getAsString());
        assertEquals(10, obj.get("id").getAsInt());

        // HTML response should return null, not throw exception
        assertNull(BaseCiConnector.safeParseJsonObject("<!DOCTYPE html><html>404</html>"));
        assertNull(BaseCiConnector.safeParseJsonObject("<html><body>Not Found</body></html>"));

        // JSON array or primitive should return null
        assertNull(BaseCiConnector.safeParseJsonObject("[1, 2, 3]"));
        assertNull(BaseCiConnector.safeParseJsonObject("\"string\""));

        // Malformed JSON should return null
        assertNull(BaseCiConnector.safeParseJsonObject("{broken json"));
        assertNull(BaseCiConnector.safeParseJsonObject(null));
        assertNull(BaseCiConnector.safeParseJsonObject(""));
    }

    @Test
    public void testExtractErrorSnippetAndStripHtmlSafely() {
        String longHtml = "<html><head><title>500 Internal Error</title></head><body><h1>Server Fault</h1>"
                + "<p>An unexpected database error occurred.</p></body></html>";

        String snippet = BaseCiConnector.extractErrorSnippet(longHtml);
        assertNotNull(snippet);
        assertFalse(snippet.contains("<html>"));
        assertTrue(snippet.contains("500 Internal Error") || snippet.contains("Server Fault"));
        assertTrue(snippet.length() <= 153); // max 150 + "..."

        String plainSnippet = BaseCiConnector.extractErrorSnippet("Just plain error message");
        assertEquals("Just plain error message", plainSnippet);

        assertEquals("", BaseCiConnector.extractErrorSnippet(null));
        assertEquals("", BaseCiConnector.extractErrorSnippet("   "));
    }

    @Test
    public void testFormatHtmlLog() {
        String log = "[Pipeline] Start pipeline\n> Running git checkout\nNormal text\n[Error] Something failed\n";
        String html = BaseCiConnector.formatHtmlLog(log);

        assertNotNull(html);
        assertTrue(html.contains("<font color='#888888'>".replace("#888888", "#81C784"))); // Pipeline
        assertTrue(html.contains("#4FC3F7")); // Blue for >
        assertTrue(html.contains("#FFB74D")); // Orange for [Error]
        assertTrue(html.contains("<br>"));
        assertFalse(html.contains("[Pipeline] Start pipeline\n"));
    }

    @Test
    public void testBlueprintCache() {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com", "user", "pass");

        assertTrue(connector.fetchBlueprintStages().isEmpty());

        List<PipelineStage> stages = List.of(
                new PipelineStage("1", "Checkout", PipelineStatus.SUCCESS, 2000),
                new PipelineStage("2", "Build", PipelineStatus.IN_PROGRESS, 5000)
        );

        connector.updateBlueprintCache(stages);
        List<PipelineStage> blueprint = connector.fetchBlueprintStages();
        assertEquals(2, blueprint.size());
        assertEquals("Checkout", blueprint.get(0).getName());
        assertEquals(PipelineStatus.NOT_STARTED, blueprint.get(0).getStatus(), "Blueprint stages must always be NOT_STARTED");
        assertEquals("Build", blueprint.get(1).getName());
        assertEquals(PipelineStatus.NOT_STARTED, blueprint.get(1).getStatus(), "Blueprint stages must always be NOT_STARTED");
    }

    @Test
    public void testInactivityTimeout() {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com", "user", "pass");
        assertTrue(connector.hasMoreData());

        assertNull(connector.checkInactivityTimeout());

        // Fast forward inactivity
        connector.lastDataReceivedTime = System.currentTimeMillis() - (6 * 60 * 1000);
        String timeoutMsg = connector.checkInactivityTimeout();
        assertNotNull(timeoutMsg);
        assertTrue(timeoutMsg.contains("CI/CD monitoring stopped automatically"));
        assertFalse(connector.hasMoreData());

        connector.markDataReceived();
        assertTrue(connector.lastDataReceivedTime <= System.currentTimeMillis());
    }

    @Test
    public void testBaseUrlAndBuildId() {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com/job/test/", "user", "pass");
        assertEquals("https://ci.example.com/job/test/", connector.getBaseUrl());
        assertEquals("https://ci.example.com/job/test", connector.getNormalizedBase());
        assertNull(connector.getCurrentBuildId());
    }

    @Test
    public void testBuildTriggeredAndWaitingLifecycle() {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com", "user", "pass");
        assertFalse(connector.isBuildTriggered());
        assertTrue(connector.isWaitingForNewBuild());

        connector.setBuildTriggered(true);
        assertTrue(connector.isBuildTriggered());
        assertTrue(connector.isWaitingForNewBuild());

        connector.setBuildTriggered(false);
        assertFalse(connector.isBuildTriggered());
    }

    @Test
    public void testAbortPipelineStopsData() {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com", "user", "pass");
        assertTrue(connector.hasMoreData());

        connector.abortPipeline();
        assertFalse(connector.hasMoreData());

        connector.hasMoreData = true;
        connector.abortPipeline("123");
        assertFalse(connector.hasMoreData());
    }

    @Test
    public void testDefaultTestConnection() throws Exception {
        DummyCiConnector connector = new DummyCiConnector("https://ci.example.com", "user", "pass");
        assertTrue(connector.testConnection());
    }
}
