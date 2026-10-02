package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import br.com.gitflowhelper.toolwindow.ci.RepoCiDashboardPanel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class JenkinsConnectorTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testUrlNormalizationAndCrumbIssuerUrl() {
        JenkinsConnector c1 = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        assertEquals("http://localhost:" + port + "/job/test/build", c1.getBuildTriggerUrl());
        assertEquals("http://localhost:" + port + "/crumbIssuer/api/json", c1.getCrumbIssuerUrl());

        JenkinsConnector c2 = new JenkinsConnector("http://localhost:" + port + "/job/test/", "user", "token");
        assertEquals("http://localhost:" + port + "/job/test/build", c2.getBuildTriggerUrl());
        assertEquals("http://localhost:" + port + "/crumbIssuer/api/json", c2.getCrumbIssuerUrl());

        JenkinsConnector c3 = new JenkinsConnector("http://localhost:" + port + "/view/All/job/test/build", "user", "token");
        assertEquals("http://localhost:" + port + "/view/All/job/test/build", c3.getBuildTriggerUrl());
        assertEquals("http://localhost:" + port + "/crumbIssuer/api/json", c3.getCrumbIssuerUrl());

        JenkinsConnector c4 = new JenkinsConnector("http://localhost:" + port + "/jenkins/job/folder/job/test/lastBuild", "user", "token");
        assertEquals("http://localhost:" + port + "/jenkins/job/folder/job/test/build", c4.getBuildTriggerUrl());
        assertEquals("http://localhost:" + port + "/jenkins/crumbIssuer/api/json", c4.getCrumbIssuerUrl());
    }

    @Test
    public void testTriggerBuildWithCrumbAndSession() throws Exception {
        AtomicReference<String> crumbHeaderReceived = new AtomicReference<>();
        AtomicReference<String> cookieHeaderReceived = new AtomicReference<>();

        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=dummy-session-12345; Path=/");
            String responseBody = "{\"crumb\":\"test-crumb-value-999\",\"crumbRequestField\":\"Jenkins-Crumb\"}";
            exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(responseBody.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        server.createContext("/job/test/build", exchange -> {
            crumbHeaderReceived.set(exchange.getRequestHeaders().getFirst("Jenkins-Crumb"));
            cookieHeaderReceived.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "myUser", "myToken");
        HttpResponse<String> response = connector.triggerBuild();

        assertNotNull(response);
        assertEquals(201, response.statusCode());
        assertEquals("test-crumb-value-999", crumbHeaderReceived.get());
        assertNotNull(cookieHeaderReceived.get());
        assertTrue(cookieHeaderReceived.get().contains("JSESSIONID=dummy-session-12345"));
        assertTrue(connector.isBuildTriggered());
    }

    @Test
    public void testTriggerBuildSuccessWhenCrumbIssuer404() throws Exception {
        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        server.createContext("/job/test/build", exchange -> {
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "myUser", "myToken123");
        HttpResponse<String> response = connector.triggerBuild();

        assertNotNull(response);
        assertEquals(201, response.statusCode());
        assertTrue(connector.isBuildTriggered());
    }

    @Test
    public void testTriggerBuildFallbackToParameterized() throws Exception {
        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        server.createContext("/job/test/build", exchange -> {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().write("Method Not Allowed".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        server.createContext("/job/test/buildWithParameters", exchange -> {
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "admin", "secret");
        HttpResponse<String> response = connector.triggerBuild();

        assertNotNull(response);
        assertEquals(201, response.statusCode());
        assertTrue(connector.isBuildTriggered());
    }

    @Test
    public void testTriggerBuildErrorThrowsException() {
        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        server.createContext("/job/test/build", exchange -> {
            exchange.sendResponseHeaders(401, 0);
            exchange.getResponseBody().write("Unauthorized".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "wrong", "cred");
        Exception ex = assertThrows(IllegalStateException.class, connector::triggerBuild);
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    public void testFetchPipelineRunReturnsEmptyPendingRunWhenBuildTriggered() {
        // Mock /lastBuild/wfapi/describe returning completed stages of a PREVIOUS build
        String oldWfApiResponse = "{\n" +
                "  \"id\": \"40\",\n" +
                "  \"name\": \"#40\",\n" +
                "  \"status\": \"SUCCESS\",\n" +
                "  \"stages\": [{\"id\":\"1\",\"name\":\"Old Stage\",\"status\":\"SUCCESS\",\"stageFlowNodes\":[]}]\n" +
                "}";

        server.createContext("/job/test/lastBuild/wfapi/describe", exchange -> {
            byte[] bytes = oldWfApiResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");

        // 1. Without buildTriggered (idle mode), returns old build
        PipelineRun idleRun = connector.fetchPipelineRun();
        assertNotNull(idleRun);
        assertEquals("40", idleRun.getId());
        assertEquals(1, idleRun.getStages().size());

        // 2. With buildTriggered = true, MUST return pending run with EMPTY stages so UI spinner is displayed!
        connector.setBuildTriggered(true);
        PipelineRun pendingRun = connector.fetchPipelineRun();
        assertNotNull(pendingRun);
        assertTrue(pendingRun.getStages().isEmpty(), "Pending run must have empty stages so the loading spinner stays active!");
        assertEquals(PipelineStatus.IN_PROGRESS, pendingRun.getStatus());
    }

    @Test
    public void testFetchNextChunkProgressiveTextDoesNotRepeatWhenLogPauses() {
        AtomicInteger buildNumber = new AtomicInteger(10);
        AtomicInteger logPollCount = new AtomicInteger(0);

        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] body = String.valueOf(buildNumber.get()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.createContext("/job/test/11/logText/progressiveText", exchange -> {
            int poll = logPollCount.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            int startParam = 0;
            if (query != null && query.contains("start=")) {
                startParam = Integer.parseInt(query.split("start=")[1].split("&")[0]);
            }

            if (poll == 1) {
                // First poll: initial output
                String chunk = "Starting build...\n";
                byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("X-Text-Size", String.valueOf(bytes.length));
                exchange.getResponseHeaders().add("X-More-Data", "true");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else if (poll == 2 || poll == 3) {
                // Polls 2 and 3: log generation paused! No new logs produced.
                // startParam should match the X-Text-Size from poll 1.
                assertEquals(18, startParam);
                exchange.getResponseHeaders().add("X-Text-Size", "18");
                exchange.getResponseHeaders().add("X-More-Data", "true");
                exchange.sendResponseHeaders(200, 0);
            } else if (poll == 4) {
                // Poll 4: new logs resume
                assertEquals(18, startParam);
                String chunk = "Build step finished.\n";
                byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("X-Text-Size", String.valueOf(18 + bytes.length));
                exchange.getResponseHeaders().add("X-More-Data", "false");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });

        server.createContext("/job/test/11/api/json", exchange -> {
            boolean isBuilding = logPollCount.get() < 4;
            String json = isBuilding
                    ? "{\"building\":true,\"result\":null}"
                    : "{\"building\":false,\"result\":\"SUCCESS\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");

        // Step 1: Initial baseline recording
        String chunk0 = connector.fetchNextChunk();
        assertTrue(chunk0.contains("Initial build number recorded: #10"));

        // Step 2: New build starts (#11)
        buildNumber.set(11);
        String chunk1 = connector.fetchNextChunk();
        assertTrue(chunk1.contains("New build detected: #11"));
        assertTrue(chunk1.contains("Starting build..."));

        // Step 3 & 4: Log generation paused -> MUST return empty string, NOT repeat!
        String chunk2 = connector.fetchNextChunk();
        assertEquals("", chunk2);

        String chunk3 = connector.fetchNextChunk();
        assertEquals("", chunk3);

        // Step 5: New logs arrive and build completes
        String chunk4 = connector.fetchNextChunk();
        assertTrue(chunk4.contains("Build step finished."));
        assertTrue(chunk4.contains("Build finished: SUCCESS"));
        assertFalse(connector.hasMoreData());
    }

    @Test
    public void testFetchNextChunkConsoleTextFallbackDoesNotRepeatWhenLogPauses() {
        AtomicInteger buildNumber = new AtomicInteger(5);
        AtomicInteger pollCount = new AtomicInteger(0);

        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] body = String.valueOf(buildNumber.get()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // progressiveText returns 404 to trigger fallback to consoleText
        server.createContext("/job/test/6/logText/progressiveText", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        // consoleText returns the full log from byte 0 on every call without X-Text-Size
        server.createContext("/job/test/6/consoleText", exchange -> {
            int p = pollCount.incrementAndGet();
            String fullLog;
            if (p == 1) {
                fullLog = "Line 1\nLine 2\n";
            } else if (p == 2 || p == 3) {
                // Log paused: full log unchanged
                fullLog = "Line 1\nLine 2\n";
            } else {
                fullLog = "Line 1\nLine 2\nLine 3\n";
            }
            byte[] bytes = fullLog.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/job/test/6/api/json", exchange -> {
            boolean isBuilding = pollCount.get() < 4;
            String json = isBuilding
                    ? "{\"building\":true,\"result\":null}"
                    : "{\"building\":false,\"result\":\"SUCCESS\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");

        connector.fetchNextChunk(); // baseline recorded: #5

        buildNumber.set(6);
        String chunk1 = connector.fetchNextChunk();
        assertTrue(chunk1.contains("New build detected: #6"));
        assertTrue(chunk1.contains("Line 1"));
        assertTrue(chunk1.contains("Line 2"));

        // Log paused: must return empty string, NOT repeat Line 1 and Line 2!
        String chunk2 = connector.fetchNextChunk();
        assertEquals("", chunk2);

        String chunk3 = connector.fetchNextChunk();
        assertEquals("", chunk3);

        // More log added
        String chunk4 = connector.fetchNextChunk();
        assertFalse(chunk4.contains("Line 1")); // must NOT repeat previously seen lines
        assertTrue(chunk4.contains("Line 3"));
        assertTrue(chunk4.contains("Build finished: SUCCESS"));
        assertFalse(connector.hasMoreData());
    }

    @Test
    public void testFetchPipelineRunWithWfApi() {
        String wfApiResponse = "{\n" +
                "  \"id\": \"42\",\n" +
                "  \"name\": \"#42\",\n" +
                "  \"status\": \"SUCCESS\",\n" +
                "  \"durationMillis\": 45000,\n" +
                "  \"stages\": [\n" +
                "    {\n" +
                "      \"id\": \"6\",\n" +
                "      \"name\": \"Checkout\",\n" +
                "      \"status\": \"SUCCESS\",\n" +
                "      \"durationMillis\": 5000,\n" +
                "      \"stageFlowNodes\": [\n" +
                "        {\"id\": \"7\", \"name\": \"Git Checkout\", \"status\": \"SUCCESS\", \"durationMillis\": 5000}\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"10\",\n" +
                "      \"name\": \"Build\",\n" +
                "      \"status\": \"SUCCESS\",\n" +
                "      \"durationMillis\": 25000,\n" +
                "      \"stageFlowNodes\": [\n" +
                "        {\"id\": \"11\", \"name\": \"mvn clean compile\", \"status\": \"SUCCESS\", \"durationMillis\": 20000},\n" +
                "        {\"id\": \"12\", \"name\": \"mvn package\", \"status\": \"SUCCESS\", \"durationMillis\": 5000}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        server.createContext("/job/test/lastBuild/wfapi/describe", exchange -> {
            byte[] bytes = wfApiResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        assertEquals("Jenkins", connector.getPlatformName());
        assertTrue(connector.getBuildUrl().endsWith("/job/test/lastBuild"));

        PipelineRun run = connector.fetchPipelineRun();
        assertNotNull(run);
        assertEquals("42", run.getId());
        assertEquals("#42", run.getName());
        assertEquals(PipelineStatus.SUCCESS, run.getStatus());
        assertEquals(45000, run.getDurationMillis());
        assertEquals("45s", run.getFormattedDuration());

        assertEquals(2, run.getStages().size());

        PipelineStage stage0 = run.getStages().get(0);
        assertEquals("Checkout", stage0.getName());
        assertEquals(PipelineStatus.SUCCESS, stage0.getStatus());
        assertEquals(1, stage0.getSteps().size());
        assertEquals("Git Checkout", stage0.getSteps().get(0).getName());
        assertEquals(PipelineStatus.SUCCESS, stage0.getSteps().get(0).getStatus());

        PipelineStage stage1 = run.getStages().get(1);
        assertEquals("Build", stage1.getName());
        assertEquals(2, stage1.getSteps().size());
        assertEquals("mvn clean compile", stage1.getSteps().get(0).getName());
        assertEquals("mvn package", stage1.getSteps().get(1).getName());
    }

    @Test
    public void testFetchPipelineRunWithLogParsingFallback() {
        AtomicInteger buildNumber = new AtomicInteger(98);

        // wfapi returns 404
        server.createContext("/job/test/lastBuild/wfapi/describe", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        // api/json returns building info
        server.createContext("/job/test/lastBuild/api/json", exchange -> {
            String json = "{\"number\":99,\"displayName\":\"Build #99\",\"building\":true,\"duration\":15000}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/job/test/99/api/json", exchange -> {
            String json = "{\"number\":99,\"displayName\":\"Build #99\",\"building\":true,\"duration\":15000}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] bytes = String.valueOf(buildNumber.get()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.createContext("/job/test/99/logText/progressiveText", exchange -> {
            String chunk = "[Pipeline] { (Build)\n[Pipeline] sh mvn compile\n";
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Text-Size", String.valueOf(bytes.length));
            exchange.getResponseHeaders().add("X-More-Data", "true");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");

        // Step 1: initial baseline recording (#98)
        connector.fetchNextChunk();

        // Step 2: Before build #99 starts parsing logs or stages, stages must be empty (triggering UI spinner)
        PipelineRun initialRun = connector.fetchPipelineRun();
        assertNotNull(initialRun);
        assertEquals("99", initialRun.getId());
        assertEquals(PipelineStatus.IN_PROGRESS, initialRun.getStatus());
        assertTrue(initialRun.getStages().isEmpty(), "Initially empty before first stage begins, allowing UI spinner to show");

        // Step 3: Build #99 starts and logs arrive
        buildNumber.set(99);
        connector.fetchNextChunk();

        // Step 4: Now stages are parsed in real time
        PipelineRun runningRun = connector.fetchPipelineRun();
        assertNotNull(runningRun);
        assertFalse(runningRun.getStages().isEmpty(), "Stages must be populated once first stage is parsed from log");
        assertEquals("Build", runningRun.getStages().get(0).getName());
        assertEquals(1, runningRun.getStages().get(0).getSteps().size());
        assertEquals("sh", runningRun.getStages().get(0).getSteps().get(0).getName());
    }

    @Test
    public void testStopRemoteBuildSendsStopAndCancelsQueue() throws Exception {
        AtomicReference<String> stopPathCalled = new AtomicReference<>();

        server.createContext("/crumbIssuer/api/json", exchange -> {
            String responseBody = "{\"crumb\":\"stop-crumb\",\"crumbRequestField\":\"Jenkins-Crumb\"}";
            exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(responseBody.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        server.createContext("/job/test/55/stop", exchange -> {
            stopPathCalled.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        connector.stopRemoteBuild("55");

        assertEquals("/job/test/55/stop", stopPathCalled.get());
    }

    @Test
    public void testStopRemoteBuildFallsBackToTermWhenStopFails() throws Exception {
        AtomicReference<String> termPathCalled = new AtomicReference<>();

        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        server.createContext("/job/test/55/stop", exchange -> {
            exchange.sendResponseHeaders(405, 0);
            exchange.close();
        });

        server.createContext("/job/test/55/term", exchange -> {
            termPathCalled.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        connector.stopRemoteBuild("55");

        assertEquals("/job/test/55/term", termPathCalled.get());
    }

    @Test
    public void testStripHtmlSafelyHandlesLargeHtmlWithoutStackOverflow() {
        StringBuilder largeHtml = new StringBuilder();
        largeHtml.append("<!DOCTYPE html><html><head><title>Error 404 - Not Found</title></head><body>");
        for (int i = 0; i < 2000; i++) {
            largeHtml.append("<div class='container' id='div_").append(i).append("'><span>Line ").append(i).append("</span><br/></div>\n");
        }
        largeHtml.append("</body></html>");

        String stripped = JenkinsConnector.stripHtmlSafely(largeHtml.toString());
        assertNotNull(stripped);
        assertTrue(stripped.length() <= 500);
        assertFalse(stripped.contains("<div>"));
        assertFalse(stripped.contains("<br/>"));
    }

    @Test
    public void testExtractErrorSnippetExtractsCleanSnippet() {
        String html404 = "<html><head><title>Apache Tomcat/9.0 - Error report</title></head>"
                + "<body><h1>HTTP Status 404 – Not Found</h1><p>The origin server did not find a current representation for the target resource.</p></body></html>";
        String snippet = JenkinsConnector.extractErrorSnippet(html404);
        assertNotNull(snippet);
        assertFalse(snippet.contains("<html>"));
        assertTrue(snippet.contains("HTTP Status 404") || snippet.contains("Apache Tomcat"));
    }

    @Test
    public void testRepoCiDashboardPanelToPlainTextHandlesLargeHtmlWithoutStackOverflow() {
        StringBuilder largeHtml = new StringBuilder();
        largeHtml.append("<html><body>");
        for (int i = 0; i < 2000; i++) {
            largeHtml.append("<span>Line ").append(i).append("&lt;test&gt;</span><br>");
        }
        largeHtml.append("</body></html>");

        String plainText = RepoCiDashboardPanel.toPlainText(largeHtml.toString());
        assertNotNull(plainText);
        assertFalse(plainText.contains("<span>"));
        assertFalse(plainText.contains("<br>"));
        assertTrue(plainText.contains("<test>"));
        assertTrue(plainText.contains("Line 0"));
    }

    @Test
    public void testInvalidUrlReturningHtmlErrorDoesNotCrashAndStopsMonitoring() {
        // Simulates invalid Jenkins URL returning Tomcat/Jenkins 404 HTML
        String htmlError = "<!DOCTYPE html><html><head><title>404 Not Found</title></head><body><h1>Not Found</h1><p>No such job</p></body></html>";
        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] bytes = htmlError.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
            exchange.sendResponseHeaders(404, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        String chunk = connector.fetchNextChunk();

        assertNotNull(chunk);
        assertTrue(chunk.contains("Failed to inspect build status") || chunk.contains("Error"));
        assertFalse(chunk.contains("<!DOCTYPE html>"), "Should not dump raw HTML into the log chunk");
        assertFalse(connector.hasMoreData(), "Connector must halt monitoring when non-numeric HTML is received");
    }

    @Test
    public void testTriggerBuildOnInvalidUrlReturningHtmlThrowsSanitizedError() {
        String html500 = "<html><head><title>500 Internal Error</title></head><body><h1>Server Error</h1><p>Jenkins error</p></body></html>";
        server.createContext("/crumbIssuer/api/json", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });
        server.createContext("/job/test/build", exchange -> {
            byte[] bytes = html500.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        IllegalStateException ex = assertThrows(IllegalStateException.class, connector::triggerBuild);
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("500"));
        assertFalse(ex.getMessage().contains("<!DOCTYPE") && !ex.getMessage().contains("<html>"));
    }

    @Test
    public void testTestConnectionSuccess() throws Exception {
        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] bytes = "42\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        assertTrue(connector.testConnection());
    }

    @Test
    public void testTestConnectionFailure() {
        server.createContext("/job/test/lastBuild/buildNumber", exchange -> {
            byte[] bytes = "<html><head><title>403 Forbidden</title></head></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        assertThrows(IllegalStateException.class, connector::testConnection);
    }

    @Test
    public void testAbortPipelineViaCiConnectorInterface() {
        JenkinsConnector connector = new JenkinsConnector("http://localhost:" + port + "/job/test", "user", "token");
        assertTrue(connector.hasMoreData());

        CiConnector ci = connector;
        ci.abortPipeline();
        assertFalse(ci.hasMoreData());

        ci.setBuildTriggered(true);
        assertTrue(ci.isBuildTriggered());
        assertEquals("http://localhost:" + port + "/job/test", ci.getBaseUrl());
    }
}
