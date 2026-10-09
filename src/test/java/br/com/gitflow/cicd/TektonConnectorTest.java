package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import br.com.gitflowhelper.settings.CiServerConfig;
import br.com.gitflowhelper.toolwindow.CIDataToolWindowPanel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TektonConnectorTest {

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
    public void testUrlParsingVariousFormats() {
        // 1. Direct K8s Pipeline URL
        String url1 = "https://api.cluster.example.com:6443/apis/tekton.dev/v1/namespaces/dev-team/pipelines/backend-pipeline";
        TektonConnector c1 = new TektonConnector(url1, "", "token123");
        assertEquals("https://api.cluster.example.com:6443", c1.getClusterBaseUrl());
        assertEquals("dev-team", c1.getNamespace());
        assertEquals("backend-pipeline", c1.getPipelineName());
        assertEquals("tekton.dev/v1", c1.getApiVersion());

        // 2. Direct K8s PipelineRun URL with v1beta1
        String url2 = "https://api.cluster.example.com:6443/apis/tekton.dev/v1beta1/namespaces/prod-ns/pipelineruns/release-run-1";
        TektonConnector c2 = new TektonConnector(url2, "", "token123");
        assertEquals("https://api.cluster.example.com:6443", c2.getClusterBaseUrl());
        assertEquals("prod-ns", c2.getNamespace());
        assertEquals("release-run-1", c2.getPipelineName());
        assertEquals("tekton.dev/v1beta1", c2.getApiVersion());

        // 3. OpenShift Web Console URL
        String url3 = "https://console.apps.cluster.com/k8s/ns/billing/tekton.dev~v1~Pipeline/billing-ci";
        TektonConnector c3 = new TektonConnector(url3, "", "token123");
        assertEquals("https://console.apps.cluster.com", c3.getClusterBaseUrl());
        assertEquals("billing", c3.getNamespace());
        assertEquals("billing-ci", c3.getPipelineName());

        // 4. Base URL + login fallback (namespace/pipeline)
        String url4 = "https://api.cluster.com:6443";
        TektonConnector c4 = new TektonConnector(url4, "my-ns/my-pipe", "token123");
        assertEquals("https://api.cluster.com:6443", c4.getClusterBaseUrl());
        assertEquals("my-ns", c4.getNamespace());
        assertEquals("my-pipe", c4.getPipelineName());
    }

    @Test
    public void testTestConnectionSuccessWithBearerToken() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();

        server.createContext("/apis/tekton.dev/v1/namespaces/test-ns/pipelines/test-pipe", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "{\"kind\":\"Pipeline\",\"apiVersion\":\"tekton.dev/v1\",\"metadata\":{\"name\":\"test-pipe\"}}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/test-ns/pipelines/test-pipe";
        TektonConnector connector = new TektonConnector(url, "", "sha256~secret-sa-token");

        assertTrue(connector.testConnection());
        assertEquals("Bearer sha256~secret-sa-token", authHeader.get());
    }

    @Test
    public void testTestConnectionFallbackToV1Beta1() throws Exception {
        server.createContext("/apis/tekton.dev/v1/namespaces/test-ns/pipelines/test-pipe", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });

        server.createContext("/apis/tekton.dev/v1beta1/namespaces/test-ns/pipelines/test-pipe", exchange -> {
            String response = "{\"kind\":\"Pipeline\",\"apiVersion\":\"tekton.dev/v1beta1\"}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/test-ns/pipelines/test-pipe";
        TektonConnector connector = new TektonConnector(url, "", "secret-token");

        assertTrue(connector.testConnection());
        assertEquals("tekton.dev/v1beta1", connector.getApiVersion());
    }

    @Test
    public void testTriggerBuildCreatesPipelineRun() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();

        server.createContext("/apis/tekton.dev/v1/namespaces/demo-ns/pipelineruns", exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                requestBody.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            String createdJson = "{\n" +
                    "  \"apiVersion\": \"tekton.dev/v1\",\n" +
                    "  \"kind\": \"PipelineRun\",\n" +
                    "  \"metadata\": {\n" +
                    "    \"name\": \"demo-pipe-run-abc12\"\n" +
                    "  }\n" +
                    "}";
            exchange.sendResponseHeaders(201, createdJson.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(createdJson.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/demo-ns/pipelines/demo-pipe";
        TektonConnector connector = new TektonConnector(url, "", "tok");

        HttpResponse<String> res = connector.triggerBuild();
        assertEquals(201, res.statusCode());
        assertTrue(connector.isBuildTriggered());
        assertEquals("demo-pipe-run-abc12", connector.getCurrentPipelineRunName());
        assertTrue(requestBody.get().contains("demo-pipe"));
    }

    @Test
    public void testFetchBlueprintStagesFromPipeline() {
        server.createContext("/apis/tekton.dev/v1/namespaces/ci/pipelines/build-pipeline", exchange -> {
            String pipeJson = "{\n" +
                    "  \"apiVersion\": \"tekton.dev/v1\",\n" +
                    "  \"kind\": \"Pipeline\",\n" +
                    "  \"spec\": {\n" +
                    "    \"tasks\": [\n" +
                    "      {\"name\": \"git-clone\"},\n" +
                    "      {\"name\": \"maven-build\"},\n" +
                    "      {\"name\": \"image-push\"}\n" +
                    "    ],\n" +
                    "    \"finally\": [\n" +
                    "      {\"name\": \"slack-notify\"}\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}";
            exchange.sendResponseHeaders(200, pipeJson.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(pipeJson.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/ci/pipelines/build-pipeline";
        TektonConnector connector = new TektonConnector(url, "", "token");

        var stages = connector.fetchBlueprintStages();
        assertEquals(4, stages.size());
        assertEquals("git-clone", stages.get(0).getName());
        assertEquals("maven-build", stages.get(1).getName());
        assertEquals("image-push", stages.get(2).getName());
        assertEquals("slack-notify", stages.get(3).getName());
        assertEquals(PipelineStatus.NOT_STARTED, stages.get(0).getStatus());
    }

    @Test
    public void testFetchPipelineRunAndTaskRunsMapping() {
        // PipelineRun endpoint
        server.createContext("/apis/tekton.dev/v1/namespaces/ci/pipelineruns/run-100", exchange -> {
            String prJson = "{\n" +
                    "  \"metadata\": {\"name\": \"run-100\"},\n" +
                    "  \"status\": {\n" +
                    "    \"conditions\": [\n" +
                    "      {\"type\": \"Succeeded\", \"status\": \"Unknown\", \"reason\": \"Running\"}\n" +
                    "    ],\n" +
                    "    \"startTime\": \"2026-10-06T12:00:00Z\"\n" +
                    "  }\n" +
                    "}";
            exchange.sendResponseHeaders(200, prJson.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(prJson.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        // TaskRuns endpoint
        server.createContext("/apis/tekton.dev/v1/namespaces/ci/taskruns", exchange -> {
            String trJson = "{\n" +
                    "  \"items\": [\n" +
                    "    {\n" +
                    "      \"metadata\": {\n" +
                    "        \"name\": \"run-100-checkout\",\n" +
                    "        \"labels\": {\"tekton.dev/pipelineTask\": \"checkout\"}\n" +
                    "      },\n" +
                    "      \"status\": {\n" +
                    "        \"conditions\": [{\"type\": \"Succeeded\", \"status\": \"True\"}],\n" +
                    "        \"steps\": [\n" +
                    "          {\"name\": \"clone\", \"terminated\": {\"exitCode\": 0}}\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"metadata\": {\n" +
                    "        \"name\": \"run-100-build\",\n" +
                    "        \"labels\": {\"tekton.dev/pipelineTask\": \"build\"}\n" +
                    "      },\n" +
                    "      \"status\": {\n" +
                    "        \"conditions\": [{\"type\": \"Succeeded\", \"status\": \"Unknown\", \"reason\": \"Running\"}],\n" +
                    "        \"steps\": [\n" +
                    "          {\"name\": \"compile\", \"running\": {}}\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            exchange.sendResponseHeaders(200, trJson.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(trJson.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/ci/pipelines/sample";
        TektonConnector connector = new TektonConnector(url, "", "token");
        connector.setCurrentPipelineRunName("run-100");

        PipelineRun run = connector.fetchPipelineRun();
        assertNotNull(run);
        assertEquals(PipelineStatus.IN_PROGRESS, run.getStatus());
        assertEquals(2, run.getStages().size());

        PipelineStage stage1 = run.getStages().get(0);
        assertEquals("checkout", stage1.getName());
        assertEquals(PipelineStatus.SUCCESS, stage1.getStatus());
        assertEquals(1, stage1.getSteps().size());
        assertEquals(PipelineStatus.SUCCESS, stage1.getSteps().get(0).getStatus());

        PipelineStage stage2 = run.getStages().get(1);
        assertEquals("build", stage2.getName());
        assertEquals(PipelineStatus.IN_PROGRESS, stage2.getStatus());
        assertEquals(1, stage2.getSteps().size());
        assertEquals(PipelineStatus.IN_PROGRESS, stage2.getSteps().get(0).getStatus());
    }

    @Test
    public void testFetchNextChunkIncrementalLogStreaming() {
        server.createContext("/api/v1/namespaces/ci/pods", exchange -> {
            String podsJson = "{\n" +
                    "  \"items\": [\n" +
                    "    {\n" +
                    "      \"metadata\": {\n" +
                    "        \"name\": \"run-55-task-pod\",\n" +
                    "        \"labels\": {\"tekton.dev/pipelineTask\": \"build-task\"}\n" +
                    "      },\n" +
                    "      \"spec\": {\n" +
                    "        \"containers\": [{\"name\": \"step-compile\"}]\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            exchange.sendResponseHeaders(200, podsJson.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(podsJson.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        AtomicReference<String> logContent = new AtomicReference<>("Compiling source files...\nDone compiling.\n");
        server.createContext("/api/v1/namespaces/ci/pods/run-55-task-pod/log", exchange -> {
            String body = logContent.get();
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/ci/pipelines/sample";
        TektonConnector connector = new TektonConnector(url, "", "token");
        connector.setCurrentPipelineRunName("run-55");

        String chunk1 = connector.fetchNextChunk();
        assertTrue(chunk1.contains("Compiling source files..."));
        assertTrue(chunk1.contains("Done compiling."));

        // Second fetch without new logs should yield nothing (incremental)
        String chunk2 = connector.fetchNextChunk();
        assertTrue(chunk2.isEmpty());

        // Now append more lines
        logContent.set("Compiling source files...\nDone compiling.\nRunning unit tests...\n");
        String chunk3 = connector.fetchNextChunk();
        assertTrue(chunk3.contains("Running unit tests..."));
        assertFalse(chunk3.contains("Compiling source files..."));
    }

    @Test
    public void testAbortPipelineSendsPatchRequest() {
        AtomicReference<String> patchMethod = new AtomicReference<>();
        AtomicReference<String> patchBody = new AtomicReference<>();

        server.createContext("/apis/tekton.dev/v1/namespaces/ci/pipelineruns/run-99", exchange -> {
            patchMethod.set(exchange.getRequestMethod());
            try (InputStream is = exchange.getRequestBody()) {
                patchBody.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        String url = "http://localhost:" + port + "/apis/tekton.dev/v1/namespaces/ci/pipelines/sample";
        TektonConnector connector = new TektonConnector(url, "", "token");
        connector.setCurrentPipelineRunName("run-99");

        connector.abortPipeline();
        assertEquals("PATCH", patchMethod.get());
        assertTrue(patchBody.get().contains("CancelledRunFinally"));
        assertFalse(connector.hasMoreData());
    }

    @Test
    public void testFactoryCreationInCIDataToolWindowPanel() {
        CiServerConfig cfgTekton = new CiServerConfig();
        cfgTekton.setCiType("OpenShift (Tekton)");
        cfgTekton.setCiUrl("https://api.cluster.com:6443/apis/tekton.dev/v1/namespaces/prod/pipelines/deploy-app");

        CiConnector conn = CIDataToolWindowPanel.createConnector(cfgTekton, "my-token");
        assertNotNull(conn);
        assertInstanceOf(TektonConnector.class, conn);
        assertEquals("OpenShift (Tekton)", conn.getPlatformName());

        CiServerConfig cfgTektonShort = new CiServerConfig();
        cfgTektonShort.setCiType("Tekton");
        cfgTektonShort.setCiUrl("https://api.cluster.com:6443/apis/tekton.dev/v1/namespaces/prod/pipelines/deploy-app");

        CiConnector connShort = CIDataToolWindowPanel.createConnector(cfgTektonShort, "my-token");
        assertNotNull(connShort);
        assertInstanceOf(TektonConnector.class, connShort);
    }
}
