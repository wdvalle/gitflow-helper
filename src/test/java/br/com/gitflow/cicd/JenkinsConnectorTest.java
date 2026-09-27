package br.com.gitflow.cicd;

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
}
