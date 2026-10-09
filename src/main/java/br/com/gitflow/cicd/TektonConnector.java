package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CI/CD connector for OpenShift Pipelines and Tekton Pipelines (tekton.dev API).
 *
 * <p>Supports:
 * <ul>
 *   <li>Automatic URL parsing from Kubernetes API endpoints or OpenShift Console URLs</li>
 *   <li>Bearer token authentication (ServiceAccount / OpenShift user token)</li>
 *   <li>Triggering new {@code PipelineRun} executions via REST</li>
 *   <li>Auto-tracking {@code PipelineRun} executions started by external triggers (Webhooks / Git push)</li>
 *   <li>Full DAG mapping: PipelineRun &rarr; TaskRuns (Stages) &rarr; Containers (Steps)</li>
 *   <li>Blueprint extraction directly from Tekton {@code Pipeline} definitions</li>
 *   <li>Real-time incremental log streaming per container/step from Kubernetes Pods</li>
 *   <li>Individual step log retrieval for the stage details dialog</li>
 *   <li>Graceful cancellation via Tekton {@code CancelledRunFinally} status patch</li>
 * </ul>
 */
public class TektonConnector extends BaseCiConnector {

    private static final String DEFAULT_API_VERSION = "tekton.dev/v1";
    private static final String FALLBACK_API_VERSION = "tekton.dev/v1beta1";

    // Regex to extract namespace and pipeline name from common Tekton / OpenShift URLs
    private static final Pattern K8S_PIPELINE_URL_PATTERN = Pattern.compile(
            "^(https?://[^/]+)/apis/(tekton\\.dev/(?:v1|v1beta1))/namespaces/([^/]+)/pipelines/([^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern K8S_PIPELINERUN_URL_PATTERN = Pattern.compile(
            "^(https?://[^/]+)/apis/(tekton\\.dev/(?:v1|v1beta1))/namespaces/([^/]+)/pipelineruns(?:/([^/?#]+))?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OPENSHIFT_CONSOLE_PIPELINE_PATTERN = Pattern.compile(
            "^(https?://[^/]+)(?:/.*)?/k8s/ns/([^/]+)/tekton\\.dev~[^~]+~Pipeline/([^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OPENSHIFT_CONSOLE_RUN_PATTERN = Pattern.compile(
            "^(https?://[^/]+)(?:/.*)?/k8s/ns/([^/]+)/tekton\\.dev~[^~]+~PipelineRun/([^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final String clusterBaseUrl;
    private final String namespace;
    private final String pipelineName;
    private volatile String apiVersion = DEFAULT_API_VERSION;

    private volatile String currentPipelineRunName = null;

    // Tracks read character offsets for each container log: key = "podName:containerName" -> characters read
    private final Map<String, Integer> containerLogOffsets = new ConcurrentHashMap<>();

    // Cache of step id -> container log info for on-demand fetchStepLog
    private final Map<String, ContainerLogRef> stepLogReferences = new ConcurrentHashMap<>();

    private static class ContainerLogRef {
        final String podName;
        final String containerName;

        ContainerLogRef(String podName, String containerName) {
            this.podName = podName;
            this.containerName = containerName;
        }
    }

    public TektonConnector(@NotNull String rawUrl, @Nullable String login, @Nullable String token) {
        super(rawUrl, login, token);
        ParsedTektonUrl parsed = parseTektonUrl(rawUrl, login);
        this.clusterBaseUrl = parsed.clusterBaseUrl;
        this.namespace = parsed.namespace;
        this.pipelineName = parsed.pipelineName;
        if (parsed.apiVersion != null) {
            this.apiVersion = parsed.apiVersion;
        }
    }

    public TektonConnector(@NotNull String rawUrl,
                           @Nullable String login,
                           @Nullable String token,
                           @Nullable HttpClient customClient) {
        super(rawUrl, login, token, customClient);
        ParsedTektonUrl parsed = parseTektonUrl(rawUrl, login);
        this.clusterBaseUrl = parsed.clusterBaseUrl;
        this.namespace = parsed.namespace;
        this.pipelineName = parsed.pipelineName;
        if (parsed.apiVersion != null) {
            this.apiVersion = parsed.apiVersion;
        }
    }

    public TektonConnector(@NotNull String clusterBaseUrl,
                           @NotNull String namespace,
                           @NotNull String pipelineName,
                           @Nullable String token) {
        super(clusterBaseUrl, "", token);
        this.clusterBaseUrl = normalizeBaseUrl(clusterBaseUrl);
        this.namespace = namespace.trim();
        this.pipelineName = pipelineName.trim();
    }

    @Override
    public @NotNull String getPlatformName() {
        return "OpenShift (Tekton)";
    }

    public @NotNull String getClusterBaseUrl() {
        return clusterBaseUrl;
    }

    public @NotNull String getNamespace() {
        return namespace;
    }

    public @NotNull String getPipelineName() {
        return pipelineName;
    }

    public @NotNull String getApiVersion() {
        return apiVersion;
    }

    public @Nullable String getCurrentPipelineRunName() {
        return currentPipelineRunName;
    }

    public void setCurrentPipelineRunName(@Nullable String name) {
        this.currentPipelineRunName = name;
    }

    @Override
    public @Nullable String getCurrentBuildId() {
        return currentPipelineRunName;
    }

    @Override
    public @Nullable String getBuildUrl() {
        if (currentPipelineRunName != null) {
            return String.format("%s/apis/%s/namespaces/%s/pipelineruns/%s",
                    clusterBaseUrl, apiVersion, namespace, currentPipelineRunName);
        }
        return String.format("%s/apis/%s/namespaces/%s/pipelines/%s",
                clusterBaseUrl, apiVersion, namespace, pipelineName);
    }

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    @Override
    protected void applyAuthentication(@NotNull HttpRequest.Builder builder) {
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
    }

    // -----------------------------------------------------------------------
    // Connectivity & Diagnostics
    // -----------------------------------------------------------------------

    @Override
    public boolean testConnection() throws Exception {
        if (clusterBaseUrl.isEmpty() || namespace.isEmpty()) {
            throw new IllegalStateException("Tekton cluster URL or namespace is not specified.");
        }

        // 1. Try querying pipeline definition
        String pipelineUrl = String.format("%s/apis/%s/namespaces/%s/pipelines/%s",
                clusterBaseUrl, apiVersion, namespace, pipelineName);

        HttpRequest req = createRequestBuilder(pipelineUrl).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        int code = res.statusCode();
        String contentType = res.headers().firstValue("Content-Type").orElse("");

        // If 404 with v1, try v1beta1 fallback
        if (code == 404 && DEFAULT_API_VERSION.equals(apiVersion)) {
            String fallbackUrl = String.format("%s/apis/%s/namespaces/%s/pipelines/%s",
                    clusterBaseUrl, FALLBACK_API_VERSION, namespace, pipelineName);
            HttpRequest fbReq = createRequestBuilder(fallbackUrl).GET().build();
            HttpResponse<String> fbRes = httpClient.send(fbReq, HttpResponse.BodyHandlers.ofString());
            if (fbRes.statusCode() >= 200 && fbRes.statusCode() < 400) {
                this.apiVersion = FALLBACK_API_VERSION;
                return true;
            }
        }

        if (code >= 200 && code < 400) {
            if (isHtmlResponse(contentType, res.body())) {
                throw new IllegalStateException("Server returned an HTML page instead of Kubernetes API response. Check URL and Bearer token.");
            }
            return true;
        }

        // If specific pipeline not found, check namespace accessibility via pipelineruns list
        if (code == 404) {
            String listUrl = String.format("%s/apis/%s/namespaces/%s/pipelineruns?limit=1",
                    clusterBaseUrl, apiVersion, namespace);
            HttpRequest listReq = createRequestBuilder(listUrl).GET().build();
            HttpResponse<String> listRes = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            if (listRes.statusCode() >= 200 && listRes.statusCode() < 400) {
                return true;
            }
        }

        throw new IllegalStateException(buildFriendlyHttpErrorMessage("Tekton connection test failed", code, contentType, res.body()));
    }

    // -----------------------------------------------------------------------
    // Blueprint Discovery
    // -----------------------------------------------------------------------

    @Override
    public @NotNull List<PipelineStage> fetchBlueprintStages() {
        if (!blueprintStagesCache.isEmpty()) {
            return new ArrayList<>(blueprintStagesCache);
        }

        try {
            String pipelineUrl = String.format("%s/apis/%s/namespaces/%s/pipelines/%s",
                    clusterBaseUrl, apiVersion, namespace, pipelineName);
            HttpRequest req = createRequestBuilder(pipelineUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("spec")) {
                    JsonObject spec = json.getAsJsonObject("spec");
                    List<PipelineStage> stages = new ArrayList<>();

                    // Parse tasks in DAG order
                    if (spec.has("tasks")) {
                        JsonArray tasks = spec.getAsJsonArray("tasks");
                        for (JsonElement el : tasks) {
                            if (!el.isJsonObject()) continue;
                            JsonObject task = el.getAsJsonObject();
                            String name = task.has("name") ? task.get("name").getAsString() : "Task";
                            PipelineStage stage = new PipelineStage(name, name, PipelineStatus.NOT_STARTED, 0);

                            // Add steps if taskSpec is embedded
                            if (task.has("taskSpec") && task.getAsJsonObject("taskSpec").has("steps")) {
                                JsonArray steps = task.getAsJsonObject("taskSpec").getAsJsonArray("steps");
                                for (JsonElement stepEl : steps) {
                                    if (stepEl.isJsonObject() && stepEl.getAsJsonObject().has("name")) {
                                        String stepName = stepEl.getAsJsonObject().get("name").getAsString();
                                        stage.addStep(new PipelineStep(stepName, stepName, PipelineStatus.NOT_STARTED, 0));
                                    }
                                }
                            }
                            stages.add(stage);
                        }
                    }

                    // Parse finally tasks if any
                    if (spec.has("finally")) {
                        JsonArray finTasks = spec.getAsJsonArray("finally");
                        for (JsonElement el : finTasks) {
                            if (!el.isJsonObject()) continue;
                            JsonObject task = el.getAsJsonObject();
                            String name = task.has("name") ? task.get("name").getAsString() : "Finally";
                            stages.add(new PipelineStage(name, name, PipelineStatus.NOT_STARTED, 0));
                        }
                    }

                    if (!stages.isEmpty()) {
                        updateBlueprintCache(stages);
                        return stages;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return super.fetchBlueprintStages();
    }

    // -----------------------------------------------------------------------
    // Build Trigger & Execution
    // -----------------------------------------------------------------------

    @Override
    public HttpResponse<String> triggerBuild() throws Exception {
        setBuildTriggered(true);

        String url = String.format("%s/apis/%s/namespaces/%s/pipelineruns",
                clusterBaseUrl, apiVersion, namespace);

        String jsonPayload = buildPipelineRunPayload();

        HttpRequest req = createRequestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        int code = res.statusCode();
        String contentType = res.headers().firstValue("Content-Type").orElse("");

        if (code == 201 || code == 200) {
            JsonObject json = safeParseJsonObject(res.body());
            if (json != null && json.has("metadata") && json.getAsJsonObject("metadata").has("name")) {
                this.currentPipelineRunName = json.getAsJsonObject("metadata").get("name").getAsString();
                this.waitingForNewBuild = false;
                this.hasMoreData = true;
            }
            return res;
        }

        throw new IllegalStateException(buildFriendlyHttpErrorMessage(
                "Failed to trigger Tekton PipelineRun", code, contentType, res.body()
        ));
    }

    private @NotNull String buildPipelineRunPayload() {
        JsonObject pr = new JsonObject();
        pr.addProperty("apiVersion", apiVersion);
        pr.addProperty("kind", "PipelineRun");

        JsonObject metadata = new JsonObject();
        metadata.addProperty("generateName", pipelineName + "-run-");

        JsonObject labels = new JsonObject();
        labels.addProperty("tekton.dev/pipeline", pipelineName);
        metadata.add("labels", labels);
        pr.add("metadata", metadata);

        JsonObject spec = new JsonObject();
        JsonObject pipelineRef = new JsonObject();
        pipelineRef.addProperty("name", pipelineName);
        spec.add("pipelineRef", pipelineRef);
        pr.add("spec", spec);

        return pr.toString();
    }

    // -----------------------------------------------------------------------
    // Pipeline State Polling (DAG & Real-time Stages)
    // -----------------------------------------------------------------------

    @Override
    public @Nullable PipelineRun fetchPipelineRun() {
        try {
            // If we don't have an active PipelineRun name, look for latest run (supports external triggers)
            if (currentPipelineRunName == null) {
                currentPipelineRunName = findLatestPipelineRunName();
                if (currentPipelineRunName == null) {
                    return latestPipelineRun;
                }
            }

            String prUrl = String.format("%s/apis/%s/namespaces/%s/pipelineruns/%s",
                    clusterBaseUrl, apiVersion, namespace, currentPipelineRunName);

            HttpRequest req = createRequestBuilder(prUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                return latestPipelineRun;
            }

            JsonObject json = safeParseJsonObject(res.body());
            if (json == null) {
                return latestPipelineRun;
            }

            JsonObject statusObj = json.has("status") ? json.getAsJsonObject("status") : null;
            PipelineStatus overallStatus = determineTektonStatus(statusObj);

            PipelineRun run = new PipelineRun(
                    currentPipelineRunName,
                    "#" + currentPipelineRunName,
                    overallStatus
            );

            if (statusObj != null) {
                long duration = calculateDurationMs(statusObj);
                run.setDurationMillis(duration);
            }

            // Populate stages and steps from TaskRuns
            List<PipelineStage> stages = fetchTaskRunsForPipelineRun(currentPipelineRunName);
            if (!stages.isEmpty()) {
                for (PipelineStage stage : stages) {
                    run.addStage(stage);
                }
                updateBlueprintCache(stages);
            } else if (!blueprintStagesCache.isEmpty()) {
                // Initial fallback before TaskRuns are scheduled
                for (PipelineStage bp : blueprintStagesCache) {
                    run.addStage(new PipelineStage(bp.getId(), bp.getName(), PipelineStatus.NOT_STARTED, 0));
                }
            }

            this.latestPipelineRun = run;

            if (!overallStatus.isRunning()) {
                this.waitingForNewBuild = false;
            }

            return run;
        } catch (Exception e) {
            return latestPipelineRun;
        }
    }

    private @NotNull List<PipelineStage> fetchTaskRunsForPipelineRun(@NotNull String prName) {
        List<PipelineStage> stages = new ArrayList<>();
        try {
            String trUrl = String.format("%s/apis/%s/namespaces/%s/taskruns?labelSelector=tekton.dev/pipelineRun=%s",
                    clusterBaseUrl, apiVersion, namespace, prName);

            HttpRequest req = createRequestBuilder(trUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("items")) {
                    JsonArray items = json.getAsJsonArray("items");

                    // Sort TaskRuns by creation timestamp
                    List<JsonObject> taskRunObjects = new ArrayList<>();
                    for (JsonElement el : items) {
                        if (el.isJsonObject()) taskRunObjects.add(el.getAsJsonObject());
                    }
                    taskRunObjects.sort(Comparator.comparing(this::extractCreationTimestamp));

                    for (JsonObject tr : taskRunObjects) {
                        JsonObject meta = tr.getAsJsonObject("metadata");
                        JsonObject trStatus = tr.has("status") ? tr.getAsJsonObject("status") : null;

                        String taskRunName = meta.get("name").getAsString();
                        String stageName = taskRunName;

                        // Prefer tekton.dev/pipelineTask label or status.pipelineTaskName
                        if (meta.has("labels") && meta.getAsJsonObject("labels").has("tekton.dev/pipelineTask")) {
                            stageName = meta.getAsJsonObject("labels").get("tekton.dev/pipelineTask").getAsString();
                        } else if (trStatus != null && trStatus.has("pipelineTaskName")) {
                            stageName = trStatus.get("pipelineTaskName").getAsString();
                        }

                        PipelineStatus stageStatus = determineTektonStatus(trStatus);
                        long stageDuration = calculateDurationMs(trStatus);

                        PipelineStage stage = new PipelineStage(taskRunName, stageName, stageStatus, stageDuration);

                        // Extract Steps within this TaskRun
                        if (trStatus != null && trStatus.has("steps")) {
                            JsonArray stepsArray = trStatus.getAsJsonArray("steps");
                            for (JsonElement sEl : stepsArray) {
                                if (!sEl.isJsonObject()) continue;
                                JsonObject sObj = sEl.getAsJsonObject();
                                String stepName = sObj.has("name") ? sObj.get("name").getAsString() : "step";
                                String containerName = sObj.has("container") ? sObj.get("container").getAsString() : "step-" + stepName;

                                PipelineStatus stepStatus = determineStepStatus(sObj);
                                long stepDuration = calculateStepDurationMs(sObj);

                                String stepId = taskRunName + "/" + stepName;
                                PipelineStep step = new PipelineStep(stepId, stepName, stepStatus, stepDuration);
                                stage.addStep(step);

                                // Map stepId for on-demand details log retrieval
                                if (trStatus.has("podName")) {
                                    String podName = trStatus.get("podName").getAsString();
                                    stepLogReferences.put(stepId, new ContainerLogRef(podName, containerName));
                                }
                            }
                        }

                        stages.add(stage);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return stages;
    }

    private @NotNull String extractCreationTimestamp(@NotNull JsonObject k8sObj) {
        if (k8sObj.has("metadata") && k8sObj.getAsJsonObject("metadata").has("creationTimestamp")) {
            return k8sObj.getAsJsonObject("metadata").get("creationTimestamp").getAsString();
        }
        return "";
    }

    // -----------------------------------------------------------------------
    // Log Streaming (Pod Containers -> Console)
    // -----------------------------------------------------------------------

    @Override
    public @NotNull String fetchNextChunk() {
        if (currentPipelineRunName == null) {
            return "";
        }

        StringBuilder chunkBuilder = new StringBuilder();

        try {
            // Find all pods created for this PipelineRun
            String podsUrl = String.format("%s/api/v1/namespaces/%s/pods?labelSelector=tekton.dev/pipelineRun=%s",
                    clusterBaseUrl, namespace, currentPipelineRunName);

            HttpRequest req = createRequestBuilder(podsUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("items")) {
                    JsonArray pods = json.getAsJsonArray("items");

                    for (JsonElement pEl : pods) {
                        if (!pEl.isJsonObject()) continue;
                        JsonObject pod = pEl.getAsJsonObject();
                        String podName = pod.getAsJsonObject("metadata").get("name").getAsString();

                        JsonObject podSpec = pod.getAsJsonObject("spec");
                        if (!podSpec.has("containers")) continue;

                        JsonArray containers = podSpec.getAsJsonArray("containers");
                        for (JsonElement cEl : containers) {
                            if (!cEl.isJsonObject()) continue;
                            String cName = cEl.getAsJsonObject().get("name").getAsString();
                            if (!cName.startsWith("step-") && !cName.equals("place-tools")) {
                                continue;
                            }

                            String key = podName + ":" + cName;
                            int lastOffset = containerLogOffsets.getOrDefault(key, 0);

                            String containerLogUrl = String.format(
                                    "%s/api/v1/namespaces/%s/pods/%s/log?container=%s&follow=false",
                                    clusterBaseUrl, namespace, podName, cName
                            );

                            HttpRequest logReq = createRequestBuilder(containerLogUrl).GET().build();
                            HttpResponse<String> logRes = httpClient.send(logReq, HttpResponse.BodyHandlers.ofString());

                            if (logRes.statusCode() == 200 && logRes.body() != null) {
                                String fullLog = logRes.body();
                                if (fullLog.length() > lastOffset) {
                                    String newDelta = fullLog.substring(lastOffset);
                                    containerLogOffsets.put(key, fullLog.length());

                                    String taskName = pod.has("metadata") && pod.getAsJsonObject("metadata").has("labels")
                                            && pod.getAsJsonObject("metadata").getAsJsonObject("labels").has("tekton.dev/pipelineTask")
                                            ? pod.getAsJsonObject("metadata").getAsJsonObject("labels").get("tekton.dev/pipelineTask").getAsString()
                                            : podName;

                                    chunkBuilder.append("[Pipeline] { (").append(taskName).append(" - ").append(cName).append(") }\n");
                                    chunkBuilder.append(newDelta);
                                    if (!newDelta.endsWith("\n")) {
                                        chunkBuilder.append("\n");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (latestPipelineRun != null && !latestPipelineRun.getStatus().isRunning()) {
            this.hasMoreData = false;
        }

        String rawOutput = chunkBuilder.toString();
        return rawOutput.isEmpty() ? "" : formatHtmlLog(rawOutput);
    }

    /**
     * Implements {@link StepLogProvider} on-demand log reading for individual step inspection.
     */
    public @Nullable String fetchStepLog(@NotNull String stepId) {
        ContainerLogRef ref = stepLogReferences.get(stepId);
        if (ref == null) return null;

        try {
            String logUrl = String.format("%s/api/v1/namespaces/%s/pods/%s/log?container=%s&follow=false",
                    clusterBaseUrl, namespace, ref.podName, ref.containerName);
            HttpRequest req = createRequestBuilder(logUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return res.body();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Pipeline Cancellation
    // -----------------------------------------------------------------------

    @Override
    public void abortPipeline() {
        if (currentPipelineRunName == null) {
            stop();
            return;
        }

        try {
            String prUrl = String.format("%s/apis/%s/namespaces/%s/pipelineruns/%s",
                    clusterBaseUrl, apiVersion, namespace, currentPipelineRunName);

            // Tekton standard graceful cancellation status
            String patchJson = "{\"spec\":{\"status\":\"CancelledRunFinally\"}}";

            HttpRequest req = createRequestBuilder(prUrl)
                    .header("Content-Type", "application/merge-patch+json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(patchJson))
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());

            // If CancelledRunFinally is not accepted (e.g. older Tekton version), fallback to PipelineRunCancelled
            if (res.statusCode() >= 400) {
                String fallbackPatch = "{\"spec\":{\"status\":\"PipelineRunCancelled\"}}";
                HttpRequest fbReq = createRequestBuilder(prUrl)
                        .header("Content-Type", "application/merge-patch+json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(fallbackPatch))
                        .build();
                httpClient.send(fbReq, HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception ignored) {
        }

        stop();
    }

    // -----------------------------------------------------------------------
    // Helper Parsers & Mappers
    // -----------------------------------------------------------------------

    private @Nullable String findLatestPipelineRunName() {
        try {
            String listUrl = String.format("%s/apis/%s/namespaces/%s/pipelineruns?labelSelector=tekton.dev/pipeline=%s",
                    clusterBaseUrl, apiVersion, namespace, pipelineName);

            HttpRequest req = createRequestBuilder(listUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("items")) {
                    JsonArray items = json.getAsJsonArray("items");
                    String latestName = null;
                    String latestTs = "";

                    for (JsonElement el : items) {
                        if (!el.isJsonObject()) continue;
                        JsonObject pr = el.getAsJsonObject();
                        String name = pr.getAsJsonObject("metadata").get("name").getAsString();
                        String ts = extractCreationTimestamp(pr);

                        if (ts.compareTo(latestTs) >= 0) {
                            latestTs = ts;
                            latestName = name;
                        }
                    }
                    return latestName;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static @NotNull PipelineStatus determineTektonStatus(@Nullable JsonObject statusObj) {
        if (statusObj == null || !statusObj.has("conditions")) {
            return PipelineStatus.NOT_STARTED;
        }

        JsonArray conditions = statusObj.getAsJsonArray("conditions");
        for (JsonElement el : conditions) {
            if (!el.isJsonObject()) continue;
            JsonObject cond = el.getAsJsonObject();
            String type = cond.has("type") ? cond.get("type").getAsString() : "";
            if ("Succeeded".equalsIgnoreCase(type)) {
                String status = cond.has("status") ? cond.get("status").getAsString() : "Unknown";
                String reason = cond.has("reason") ? cond.get("reason").getAsString() : "";

                if ("True".equalsIgnoreCase(status)) {
                    return PipelineStatus.SUCCESS;
                } else if ("False".equalsIgnoreCase(status)) {
                    if (reason.toLowerCase().contains("cancel")) {
                        return PipelineStatus.ABORTED;
                    }
                    return PipelineStatus.FAILED;
                } else {
                    if ("Running".equalsIgnoreCase(reason)) {
                        return PipelineStatus.IN_PROGRESS;
                    }
                    return PipelineStatus.NOT_STARTED;
                }
            }
        }

        return PipelineStatus.NOT_STARTED;
    }

    private static @NotNull PipelineStatus determineStepStatus(@NotNull JsonObject stepObj) {
        if (stepObj.has("terminated")) {
            JsonObject term = stepObj.getAsJsonObject("terminated");
            int exitCode = term.has("exitCode") ? term.get("exitCode").getAsInt() : 0;
            return exitCode == 0 ? PipelineStatus.SUCCESS : PipelineStatus.FAILED;
        }
        if (stepObj.has("running")) {
            return PipelineStatus.IN_PROGRESS;
        }
        if (stepObj.has("waiting")) {
            return PipelineStatus.NOT_STARTED;
        }
        return PipelineStatus.NOT_STARTED;
    }

    private static long calculateDurationMs(@Nullable JsonObject statusObj) {
        if (statusObj == null) return 0L;
        String startStr = statusObj.has("startTime") ? statusObj.get("startTime").getAsString() : null;
        String endStr = statusObj.has("completionTime") ? statusObj.get("completionTime").getAsString() : null;

        if (startStr == null) return 0L;
        try {
            Instant start = Instant.parse(startStr);
            Instant end = endStr != null ? Instant.parse(endStr) : Instant.now();
            return Math.max(0L, Duration.between(start, end).toMillis());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long calculateStepDurationMs(@NotNull JsonObject stepObj) {
        if (stepObj.has("terminated")) {
            JsonObject term = stepObj.getAsJsonObject("terminated");
            String startStr = term.has("startedAt") ? term.get("startedAt").getAsString() : null;
            String endStr = term.has("finishedAt") ? term.get("finishedAt").getAsString() : null;
            if (startStr != null && endStr != null) {
                try {
                    Instant start = Instant.parse(startStr);
                    Instant end = Instant.parse(endStr);
                    return Math.max(0L, Duration.between(start, end).toMillis());
                } catch (Exception ignored) {
                }
            }
        }
        return 0L;
    }

    // -----------------------------------------------------------------------
    // URL Parsing Helper
    // -----------------------------------------------------------------------

    public static class ParsedTektonUrl {
        public final String clusterBaseUrl;
        public final String namespace;
        public final String pipelineName;
        public final String apiVersion;

        public ParsedTektonUrl(String clusterBaseUrl, String namespace, String pipelineName, String apiVersion) {
            this.clusterBaseUrl = clusterBaseUrl;
            this.namespace = namespace;
            this.pipelineName = pipelineName;
            this.apiVersion = apiVersion;
        }
    }

    public static @NotNull ParsedTektonUrl parseTektonUrl(@NotNull String rawUrl, @Nullable String login) {
        String trimmed = rawUrl.trim();

        // 1. Direct Kubernetes Pipeline URL
        Matcher m1 = K8S_PIPELINE_URL_PATTERN.matcher(trimmed);
        if (m1.find()) {
            return new ParsedTektonUrl(m1.group(1), m1.group(3), m1.group(4), m1.group(2));
        }

        // 2. Direct Kubernetes PipelineRun URL
        Matcher m2 = K8S_PIPELINERUN_URL_PATTERN.matcher(trimmed);
        if (m2.find()) {
            String runName = m2.group(4);
            String pipeName = runName != null ? runName : "pipeline";
            return new ParsedTektonUrl(m2.group(1), m2.group(3), pipeName, m2.group(2));
        }

        // 3. OpenShift Web Console Pipeline URL
        Matcher m3 = OPENSHIFT_CONSOLE_PIPELINE_PATTERN.matcher(trimmed);
        if (m3.find()) {
            return new ParsedTektonUrl(m3.group(1), m3.group(2), m3.group(3), DEFAULT_API_VERSION);
        }

        // 4. OpenShift Web Console PipelineRun URL
        Matcher m4 = OPENSHIFT_CONSOLE_RUN_PATTERN.matcher(trimmed);
        if (m4.find()) {
            return new ParsedTektonUrl(m4.group(1), m4.group(2), m4.group(3), DEFAULT_API_VERSION);
        }

        // 5. Fallback: Base URL with namespace/pipeline in login field
        String base = normalizeBaseUrl(trimmed);
        String ns = "default";
        String pipe = "pipeline";

        if (login != null && !login.isBlank()) {
            String[] parts = login.trim().split("/");
            if (parts.length >= 2) {
                ns = parts[0].trim();
                pipe = parts[1].trim();
            } else if (parts.length == 1) {
                ns = parts[0].trim();
            }
        }

        return new ParsedTektonUrl(base, ns, pipe, DEFAULT_API_VERSION);
    }
}
