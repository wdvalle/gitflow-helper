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

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CI/CD connector for Jenkins servers.
 *
 * <p>Supports Jenkins Declarative and Scripted Pipelines, Stage View API (/wfapi/describe),
 * CSRF Crumb handling, progressive log streaming (/logText/progressiveText),
 * fallback log stage parsing, and remote build aborting.</p>
 */
public class JenkinsConnector extends BaseCiConnector {

    private static final Pattern STAGE_START_PATTERN = Pattern.compile(
            "\\[Pipeline\\]\\s*(?:\\{\\s*\\([\"']?([^\"'\\)]+)[\"']?\\)|stage:?\\s*\\(?[\"']?([^\"'\\)\\r\\n]+)[\"']?\\)?)|Entering stage\\s+[\"']?([^\"'\\r\\n]+)[\"']?"
    );
    private static final Pattern STEP_PATTERN = Pattern.compile("\\[Pipeline\\]\\s*([a-zA-Z0-9_-]+)");

    private final String buildTriggerUrl;
    private final String crumbIssuerUrl;
    private final String buildNumberUrl;

    private String crumb = null;
    private String crumbRequestField = null;

    private String baselineBuildNumber = null;
    private String targetBuildNumber = null;
    private String lastQueueItemUrl = null;
    private boolean useConsoleTextFallback = false;

    // Tracks the byte offset for progressive log text requests
    private int start = 0;

    // Real-time stages and steps extracted from log stream or wfapi
    private final List<PipelineStage> fallbackStages = new CopyOnWriteArrayList<>();
    private PipelineStage currentActiveStage = null;
    private PipelineStep currentActiveStep = null;

    public JenkinsConnector(@NotNull String baseUrl, @Nullable String login, @Nullable String token) {
        super(stripJenkinsSuffixes(baseUrl), login, token);

        this.buildTriggerUrl = this.normalizedBase + "/build";
        this.buildNumberUrl = this.normalizedBase + "/lastBuild/buildNumber";

        int rootEnd = this.normalizedBase.length();
        int jobIdx = this.normalizedBase.indexOf("/job/");
        int viewIdx = this.normalizedBase.indexOf("/view/");
        if (jobIdx != -1) {
            rootEnd = Math.min(rootEnd, jobIdx);
        }
        if (viewIdx != -1) {
            rootEnd = Math.min(rootEnd, viewIdx);
        }
        String jenkinsRoot = this.normalizedBase.substring(0, rootEnd);
        this.crumbIssuerUrl = jenkinsRoot + "/crumbIssuer/api/json";
    }

    private static @NotNull String stripJenkinsSuffixes(@NotNull String url) {
        String base = normalizeBaseUrl(url);
        if (base.endsWith("/build")) {
            base = base.substring(0, base.length() - "/build".length());
        } else if (base.endsWith("/buildWithParameters")) {
            base = base.substring(0, base.length() - "/buildWithParameters".length());
        } else if (base.endsWith("/lastBuild")) {
            base = base.substring(0, base.length() - "/lastBuild".length());
        }
        return base;
    }

    @Override
    public @NotNull String getPlatformName() {
        return "Jenkins";
    }

    @Override
    public @Nullable String getBuildUrl() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        return normalizedBase + "/" + buildTarget;
    }

    @Override
    public @Nullable String getCurrentBuildId() {
        return (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : null;
    }

    @Override
    public void stop() {
        super.stop();
        CompletableFuture.runAsync(this::stopRemoteBuild);
    }

    @Override
    public void abortPipeline() {
        stop();
    }

    @Override
    public void abortPipeline(@NotNull String buildId) {
        this.hasMoreData = false;
        CompletableFuture.runAsync(() -> stopRemoteBuild(buildId));
    }

    /**
     * Sends abort signal to Jenkins to cancel or stop the running build or queued item.
     */
    public void stopRemoteBuild() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        stopRemoteBuild(buildTarget);
    }

    /**
     * Sends abort signal for a specific build target (e.g. build number or "lastBuild").
     */
    public void stopRemoteBuild(@NotNull String buildTarget) {
        this.hasMoreData = false;
        try {
            fetchCrumbIfNeeded();

            // Cancel any queued item in Jenkins queue
            cancelQueuedItemIfAny();

            // Stop the target or lastBuild
            String stopUrl = normalizedBase + "/" + buildTarget + "/stop";
            HttpRequest.Builder stopBuilder = createRequestBuilder(stopUrl)
                    .POST(HttpRequest.BodyPublishers.noBody());
            addCrumbHeader(stopBuilder);
            HttpResponse<String> stopRes = httpClient.send(stopBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (stopRes.statusCode() == 403) {
                this.crumb = null;
                this.crumbRequestField = null;
                fetchCrumbIfNeeded();
                HttpRequest.Builder retryBuilder = createRequestBuilder(stopUrl)
                        .POST(HttpRequest.BodyPublishers.noBody());
                addCrumbHeader(retryBuilder);
                stopRes = httpClient.send(retryBuilder.build(), HttpResponse.BodyHandlers.ofString());
            }

            if (stopRes.statusCode() == 404 || stopRes.statusCode() == 405) {
                String termUrl = normalizedBase + "/" + buildTarget + "/term";
                HttpRequest.Builder termBuilder = createRequestBuilder(termUrl)
                        .POST(HttpRequest.BodyPublishers.noBody());
                addCrumbHeader(termBuilder);
                httpClient.send(termBuilder.build(), HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception ignored) {
        }
    }

    private void cancelQueuedItemIfAny() {
        // If we captured queue item location URL from triggerBuild
        if (lastQueueItemUrl != null && !lastQueueItemUrl.isEmpty()) {
            try {
                int itemIdx = lastQueueItemUrl.indexOf("/queue/item/");
                if (itemIdx != -1) {
                    String base = lastQueueItemUrl.substring(0, itemIdx);
                    String idStr = lastQueueItemUrl.substring(itemIdx + "/queue/item/".length()).replace("/", "");
                    String qCancelUrl = base + "/queue/cancelItem?id=" + idStr;
                    HttpRequest.Builder cancelBuilder = createRequestBuilder(qCancelUrl)
                            .POST(HttpRequest.BodyPublishers.noBody());
                    addCrumbHeader(cancelBuilder);
                    httpClient.send(cancelBuilder.build(), HttpResponse.BodyHandlers.ofString());
                }
            } catch (Exception ignored) {
            }
        }

        // Query job queue state via API
        try {
            String jobQueueUrl = normalizedBase + "/api/json?tree=inQueue,queueItem[id]";
            HttpRequest req = createRequestBuilder(jobQueueUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("inQueue") && json.get("inQueue").getAsBoolean() && json.has("queueItem")) {
                    JsonElement qElem = json.get("queueItem");
                    if (qElem.isJsonObject()) {
                        JsonObject item = qElem.getAsJsonObject();
                        if (item.has("id")) {
                            long qId = item.get("id").getAsLong();
                            int rootEnd = normalizedBase.length();
                            int jobIdx = normalizedBase.indexOf("/job/");
                            if (jobIdx != -1) rootEnd = jobIdx;
                            String jenkinsRoot = normalizedBase.substring(0, rootEnd);
                            String cancelUrl = jenkinsRoot + "/queue/cancelItem?id=" + qId;
                            HttpRequest.Builder cancelBuilder = createRequestBuilder(cancelUrl)
                                    .POST(HttpRequest.BodyPublishers.noBody());
                            addCrumbHeader(cancelBuilder);
                            httpClient.send(cancelBuilder.build(), HttpResponse.BodyHandlers.ofString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void setBuildTriggered(boolean buildTriggered) {
        super.setBuildTriggered(buildTriggered);
        if (buildTriggered) {
            this.targetBuildNumber = null;
            this.fallbackStages.clear();
            this.currentActiveStage = null;
            this.currentActiveStep = null;
        }
    }

    @Override
    public boolean testConnection() throws Exception {
        HttpRequest req = createRequestBuilder(buildNumberUrl).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        String contentType = res.headers().firstValue("Content-Type").orElse("");
        if (code >= 200 && code < 400) {
            if (isHtmlResponse(contentType, res.body())) {
                throw new IllegalStateException("CI server returned a webpage instead of an API response. Please verify the URL and credentials.");
            }
            return true;
        }
        throw new IllegalStateException(buildFriendlyHttpErrorMessage("Connection test failed", code, contentType, res.body()));
    }

    public String getBuildTriggerUrl() {
        return buildTriggerUrl;
    }

    public String getCrumbIssuerUrl() {
        return crumbIssuerUrl;
    }

    public String getCrumb() {
        return crumb;
    }

    public String getCrumbRequestField() {
        return crumbRequestField;
    }

    public String getTargetBuildNumber() {
        return targetBuildNumber;
    }

    String getLogUrl() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        if (useConsoleTextFallback) {
            return normalizedBase + "/" + buildTarget + "/consoleText";
        } else {
            return normalizedBase + "/" + buildTarget + "/logText/progressiveText";
        }
    }

    String getApiUrl() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        return normalizedBase + "/" + buildTarget + "/api/json?tree=building,result,duration,timestamp,url,displayName,number";
    }

    String getWfApiUrl() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        return normalizedBase + "/" + buildTarget + "/wfapi/describe";
    }

    /**
     * Attempts to fetch the CSRF crumb from Jenkins.
     */
    private void fetchCrumbIfNeeded() {
        if (fetchCrumbFromUrl(crumbIssuerUrl)) {
            return;
        }
        if (!crumbIssuerUrl.equals(normalizedBase + "/crumbIssuer/api/json")) {
            fetchCrumbFromUrl(normalizedBase + "/crumbIssuer/api/json");
        }
    }

    private boolean fetchCrumbFromUrl(String url) {
        try {
            HttpRequest request = createRequestBuilder(url).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject json = safeParseJsonObject(response.body());
                if (json != null && json.has("crumb") && json.has("crumbRequestField")) {
                    this.crumb = json.get("crumb").getAsString();
                    this.crumbRequestField = json.get("crumbRequestField").getAsString();
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void addCrumbHeader(HttpRequest.Builder builder) {
        if (crumb != null && !crumb.isEmpty() && crumbRequestField != null && !crumbRequestField.isEmpty()) {
            builder.header(crumbRequestField, crumb);
        }
    }

    /**
     * Triggers a build on the Jenkins server using POST with Basic Authentication and CSRF crumb.
     *
     * @return the HTTP response received from Jenkins
     * @throws Exception if network fails or Jenkins returns an error status code
     */
    @Override
    public HttpResponse<String> triggerBuild() throws Exception {
        setBuildTriggered(true);

        // Record the current build number as baseline before triggering so the newly started build is immediately detected
        if (baselineBuildNumber == null) {
            try {
                HttpRequest bReq = createRequestBuilder(buildNumberUrl).GET().build();
                HttpResponse<String> bRes = httpClient.send(bReq, HttpResponse.BodyHandlers.ofString());
                if (bRes.statusCode() == 200 && bRes.body() != null) {
                    String trimmed = bRes.body().trim();
                    if (isNumericBuildNumber(trimmed)) {
                        baselineBuildNumber = trimmed;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        fetchCrumbIfNeeded();

        HttpRequest.Builder builder = createRequestBuilder(buildTriggerUrl)
                .POST(HttpRequest.BodyPublishers.noBody());
        addCrumbHeader(builder);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();

        // Capture queue location URL if Jenkins returned it
        Optional<String> location = response.headers().firstValue("Location");
        if (location.isPresent()) {
            this.lastQueueItemUrl = location.get();
        }

        // If 403 received, the crumb might be required, expired or changed: refetch once and retry
        if (statusCode == 403) {
            this.crumb = null;
            this.crumbRequestField = null;
            fetchCrumbIfNeeded();
            if (crumb != null && !crumb.isEmpty()) {
                HttpRequest.Builder retryBuilder = createRequestBuilder(buildTriggerUrl)
                        .POST(HttpRequest.BodyPublishers.noBody());
                addCrumbHeader(retryBuilder);
                response = httpClient.send(retryBuilder.build(), HttpResponse.BodyHandlers.ofString());
                statusCode = response.statusCode();
                Optional<String> retryLocation = response.headers().firstValue("Location");
                if (retryLocation.isPresent()) {
                    this.lastQueueItemUrl = retryLocation.get();
                }
            }
        }

        // Jenkins typically returns 201 Created when a build is scheduled.
        // Some configurations or reverse proxies may return 200 OK or 302/303 redirect.
        if (statusCode >= 200 && statusCode < 400) {
            return response;
        }

        // If the job is parameterized, Jenkins may return 400 or 405 for /build.
        // Attempt fallback to /buildWithParameters.
        if (statusCode == 400 || statusCode == 405) {
            String parameterizedUrl = normalizedBase + "/buildWithParameters";
            HttpRequest.Builder paramBuilder = createRequestBuilder(parameterizedUrl)
                    .POST(HttpRequest.BodyPublishers.noBody());
            addCrumbHeader(paramBuilder);

            HttpRequest paramRequest = paramBuilder.build();
            HttpResponse<String> paramResponse = httpClient.send(paramRequest, HttpResponse.BodyHandlers.ofString());
            if (paramResponse.statusCode() >= 200 && paramResponse.statusCode() < 400) {
                Optional<String> paramLoc = paramResponse.headers().firstValue("Location");
                if (paramLoc.isPresent()) {
                    this.lastQueueItemUrl = paramLoc.get();
                }
                return paramResponse;
            }
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        throw new IllegalStateException(buildFriendlyHttpErrorMessage("Failed to trigger build", statusCode, contentType, response.body()));
    }

    public static HttpResponse<String> triggerBuild(String baseUrl, String login, String token) throws Exception {
        return new JenkinsConnector(baseUrl, login, token).triggerBuild();
    }

    /**
     * Fetches the current pipeline execution state including stages and steps.
     * Tries Jenkins Pipeline Stage View API (/wfapi/describe) first, and falls back to
     * standard API + parsed log stages if wfapi is unavailable.
     */
    @Override
    public @Nullable PipelineRun fetchPipelineRun() {
        // If a new build was triggered or we are waiting for a new build to start,
        // do NOT return the previous build's completed stages from /lastBuild!
        if (buildTriggered && (waitingForNewBuild || targetBuildNumber == null)) {
            PipelineRun pendingRun = new PipelineRun(
                    targetBuildNumber != null ? targetBuildNumber : "queued",
                    "Starting build...",
                    PipelineStatus.IN_PROGRESS
            );
            pendingRun.setWebUrl(getBuildUrl());
            return pendingRun;
        }

        // Try wfapi/describe first
        PipelineRun wfRun = fetchWfApiPipelineRun();
        if (wfRun != null && !wfRun.getStages().isEmpty()) {
            updateBlueprintCache(wfRun.getStages());
            latestPipelineRun = wfRun;
            return wfRun;
        }

        // Fallback to /api/json + log-parsed stages
        PipelineRun apiRun = fetchBasicApiPipelineRun();
        if (apiRun != null) {
            if (!fallbackStages.isEmpty()) {
                apiRun.setStages(new ArrayList<>(fallbackStages));
                updateBlueprintCache(fallbackStages);
            }
            latestPipelineRun = apiRun;
            return apiRun;
        }

        return latestPipelineRun;
    }

    @Override
    public @NotNull List<PipelineStage> fetchBlueprintStages() {
        if (blueprintStagesCache.isEmpty()) {
            // 1. Try lastSuccessfulBuild to get the complete successful stage pipeline
            PipelineRun successfulRun = fetchWfApiPipelineRunFrom("lastSuccessfulBuild");
            if (successfulRun != null && !successfulRun.getStages().isEmpty()) {
                updateBlueprintCache(successfulRun.getStages());
            } else {
                // 2. Try lastCompletedBuild
                PipelineRun completedRun = fetchWfApiPipelineRunFrom("lastCompletedBuild");
                if (completedRun != null && !completedRun.getStages().isEmpty()) {
                    updateBlueprintCache(completedRun.getStages());
                } else if (latestPipelineRun != null && !latestPipelineRun.getStages().isEmpty()) {
                    updateBlueprintCache(latestPipelineRun.getStages());
                }
            }
        }
        return new ArrayList<>(blueprintStagesCache);
    }

    private @Nullable PipelineRun fetchWfApiPipelineRun() {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";
        return fetchWfApiPipelineRunFrom(buildTarget);
    }

    private @Nullable PipelineRun fetchWfApiPipelineRunFrom(@NotNull String buildTarget) {
        try {
            String url = normalizedBase + "/" + buildTarget + "/wfapi/describe";
            HttpRequest request = createRequestBuilder(url).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject json = safeParseJsonObject(response.body());
                if (json == null) {
                    return null;
                }
                String id = json.has("id") ? json.get("id").getAsString() : (targetBuildNumber != null ? targetBuildNumber : buildTarget);
                String name = json.has("name") ? json.get("name").getAsString() : ("Build #" + id);
                String statusStr = json.has("status") ? json.get("status").getAsString() : "";
                PipelineStatus status = PipelineStatus.fromJenkinsStatus(statusStr);

                PipelineRun run = new PipelineRun(id, name, status);
                run.setWebUrl(getBuildUrl());
                if (json.has("durationMillis") && !json.get("durationMillis").isJsonNull()) {
                    run.setDurationMillis(json.get("durationMillis").getAsLong());
                }
                if (json.has("startTimeMillis") && !json.get("startTimeMillis").isJsonNull()) {
                    run.setStartTimeMillis(json.get("startTimeMillis").getAsLong());
                }

                if (json.has("stages") && json.get("stages").isJsonArray()) {
                    JsonArray stagesArr = json.getAsJsonArray("stages");
                    for (JsonElement stageElem : stagesArr) {
                        if (!stageElem.isJsonObject()) continue;
                        JsonObject stageObj = stageElem.getAsJsonObject();

                        String stageId = stageObj.has("id") ? stageObj.get("id").getAsString() : UUID.randomUUID().toString();
                        String stageName = stageObj.has("name") ? stageObj.get("name").getAsString() : "Stage";
                        String stageStatusStr = stageObj.has("status") ? stageObj.get("status").getAsString() : "";
                        PipelineStatus stageStatus = PipelineStatus.fromJenkinsStatus(stageStatusStr);
                        long stageDuration = stageObj.has("durationMillis") && !stageObj.get("durationMillis").isJsonNull() ?
                                stageObj.get("durationMillis").getAsLong() : 0;

                        PipelineStage stage = new PipelineStage(stageId, stageName, stageStatus, stageDuration);
                        if (stageObj.has("startTimeMillis") && !stageObj.get("startTimeMillis").isJsonNull()) {
                            stage.setStartTimeMillis(stageObj.get("startTimeMillis").getAsLong());
                        }

                        // Parse steps (stageFlowNodes)
                        if (stageObj.has("stageFlowNodes") && stageObj.get("stageFlowNodes").isJsonArray()) {
                            JsonArray nodesArr = stageObj.getAsJsonArray("stageFlowNodes");
                            for (JsonElement nodeElem : nodesArr) {
                                if (!nodeElem.isJsonObject()) continue;
                                JsonObject nodeObj = nodeElem.getAsJsonObject();
                                String nodeId = nodeObj.has("id") ? nodeObj.get("id").getAsString() : UUID.randomUUID().toString();
                                String nodeName = nodeObj.has("name") ? nodeObj.get("name").getAsString() : "Step";
                                String nodeStatusStr = nodeObj.has("status") ? nodeObj.get("status").getAsString() : "";
                                PipelineStatus nodeStatus = PipelineStatus.fromJenkinsStatus(nodeStatusStr);
                                long nodeDuration = nodeObj.has("durationMillis") && !nodeObj.get("durationMillis").isJsonNull() ?
                                        nodeObj.get("durationMillis").getAsLong() : 0;

                                PipelineStep step = new PipelineStep(nodeId, nodeName, nodeStatus, nodeDuration);

                                // Check if we already collected logs for this step in fallbackStages
                                for (PipelineStage fbStage : fallbackStages) {
                                    for (PipelineStep fbStep : fbStage.getSteps()) {
                                        if ((fbStep.getId().equals(nodeId) || fbStep.getName().equalsIgnoreCase(nodeName)) && !fbStep.getLog().isEmpty()) {
                                            step.setLog(fbStep.getLog());
                                            break;
                                        }
                                    }
                                    if (!step.getLog().isEmpty()) break;
                                }

                                stage.addStep(step);
                            }
                        }

                        // If no stageFlowNodes in wfapi, check if we have fallback steps from log
                        if (stage.getSteps().isEmpty()) {
                            for (PipelineStage fbStage : fallbackStages) {
                                if (fbStage.getName().equalsIgnoreCase(stageName) && !fbStage.getSteps().isEmpty()) {
                                    stage.setSteps(new ArrayList<>(fbStage.getSteps()));
                                    break;
                                }
                            }
                        }

                        // If still empty, add default step based on stage name
                        if (stage.getSteps().isEmpty()) {
                            stage.addStep(new PipelineStep(stageId + "-step", stageName, stageStatus, stageDuration));
                        }

                        run.addStage(stage);
                    }
                }

                return run;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private @Nullable PipelineRun fetchBasicApiPipelineRun() {
        try {
            HttpRequest request = createRequestBuilder(getApiUrl()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject json = safeParseJsonObject(response.body());
                if (json == null) {
                    return null;
                }
                String id = json.has("number") ? json.get("number").getAsString() : (targetBuildNumber != null ? targetBuildNumber : "last");
                String name = json.has("displayName") ? json.get("displayName").getAsString() : ("Build #" + id);

                boolean isBuilding = json.has("building") && json.get("building").getAsBoolean();
                PipelineStatus status;
                if (isBuilding) {
                    status = PipelineStatus.IN_PROGRESS;
                } else if (json.has("result") && !json.get("result").isJsonNull()) {
                    status = PipelineStatus.fromJenkinsStatus(json.get("result").getAsString());
                } else {
                    status = PipelineStatus.UNKNOWN;
                }

                PipelineRun run = new PipelineRun(id, name, status);
                run.setWebUrl(getBuildUrl());
                if (json.has("duration") && !json.get("duration").isJsonNull()) {
                    run.setDurationMillis(json.get("duration").getAsLong());
                }
                if (json.has("timestamp") && !json.get("timestamp").isJsonNull()) {
                    run.setStartTimeMillis(json.get("timestamp").getAsLong());
                }
                return run;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Fetches the console or pipeline execution log specifically for a given step/node ID.
     */
    @Override
    public @Nullable String fetchStepLog(@NotNull String stepId) {
        String buildTarget = (targetBuildNumber != null && !targetBuildNumber.isEmpty()) ? targetBuildNumber : "lastBuild";

        // 1. Try wfapi/log
        String wfLogUrl = normalizedBase + "/" + buildTarget + "/execution/node/" + stepId + "/wfapi/log";
        try {
            HttpRequest request = createRequestBuilder(wfLogUrl).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                String ct = response.headers().firstValue("Content-Type").orElse("");
                if (!isHtmlResponse(ct, response.body())) {
                    JsonObject json = safeParseJsonObject(response.body());
                    if (json != null && json.has("text") && !json.get("text").isJsonNull()) {
                        return json.get("text").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Try progressiveText on the node
        String progLogUrl = normalizedBase + "/" + buildTarget + "/execution/node/" + stepId + "/logText/progressiveText";
        try {
            HttpRequest req = createRequestBuilder(progLogUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null && !res.body().isEmpty()) {
                String ct = res.headers().firstValue("Content-Type").orElse("");
                if (!isHtmlResponse(ct, res.body())) {
                    return res.body();
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Fallback: check if we already collected logs for this step in fallbackStages
        for (PipelineStage stage : fallbackStages) {
            for (PipelineStep step : stage.getSteps()) {
                if (step.getId().equals(stepId) && !step.getLog().isEmpty()) {
                    return step.getLog();
                }
            }
        }

        return null;
    }

    /**
     * Fetches the next available chunk of output from Jenkins.
     */
    @Override
    public @NotNull String fetchNextChunk() {
        if (!hasMoreData) {
            return "";
        }

        String timeoutMsg = checkInactivityTimeout();
        if (timeoutMsg != null) {
            return timeoutMsg;
        }

        try {
            if (waitingForNewBuild) {
                return checkBuildNumber();
            } else {
                return fetchProgressiveConsoleText();
            }
        } catch (Exception e) {
            hasMoreData = false;
            return "Error: " + e.getMessage();
        }
    }

    private String checkBuildNumber() throws Exception {
        HttpRequest request = createRequestBuilder(buildNumberUrl).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (response.statusCode() != 200) {
            hasMoreData = false;
            return formatErrorHtml(buildFriendlyHttpErrorMessage("Error fetching build status", response.statusCode(), contentType, response.body()));
        }

        String currentBuildNumber = response.body() != null ? response.body().trim() : "";

        // Build numbers from Jenkins /lastBuild/buildNumber must be purely numeric.
        // If an HTML error page or login redirect is returned, disregard HTML and show a friendly message.
        if (currentBuildNumber.isEmpty() || !isNumericBuildNumber(currentBuildNumber) || isHtmlResponse(contentType, currentBuildNumber)) {
            hasMoreData = false;
            return formatErrorHtml("CI server returned an invalid response instead of a build number. Please verify the CI/CD URL and credentials.");
        }

        if (baselineBuildNumber == null) {
            // First execution: record baseline build number
            baselineBuildNumber = currentBuildNumber;
            markDataReceived();

            // Check if current build is already building right now
            if (checkIfCurrentBuildIsBuilding(currentBuildNumber)) {
                targetBuildNumber = currentBuildNumber;
                waitingForNewBuild = false;
                start = 0;
                useConsoleTextFallback = false;
                fallbackStages.clear();
                currentActiveStage = null;
                currentActiveStep = null;
                String headerLog = formatInfoHtml("Build in progress detected: #" + currentBuildNumber + ". Fetching logs...");
                String firstChunk = fetchProgressiveConsoleText();
                return headerLog + firstChunk;
            }

            return formatInfoHtml("Initial build number recorded: #" + baselineBuildNumber + ". Waiting for new build to start...");
        }

        if (!currentBuildNumber.equals(baselineBuildNumber)) {
            // Build number changed! Start progressive log reading
            baselineBuildNumber = currentBuildNumber;
            targetBuildNumber = currentBuildNumber;
            waitingForNewBuild = false;
            start = 0;
            useConsoleTextFallback = false;
            fallbackStages.clear();
            currentActiveStage = null;
            currentActiveStep = null;
            markDataReceived();
            String headerLog = formatInfoHtml("New build detected: #" + currentBuildNumber + ". Fetching logs...");
            String firstChunk = fetchProgressiveConsoleText();
            return headerLog + firstChunk;
        }

        // Also check if current baseline build is actively building
        if (checkIfCurrentBuildIsBuilding(currentBuildNumber)) {
            targetBuildNumber = currentBuildNumber;
            waitingForNewBuild = false;
            start = 0;
            useConsoleTextFallback = false;
            fallbackStages.clear();
            currentActiveStage = null;
            currentActiveStep = null;
            markDataReceived();
            String headerLog = formatInfoHtml("Active build detected: #" + currentBuildNumber + ". Fetching logs...");
            String firstChunk = fetchProgressiveConsoleText();
            return headerLog + firstChunk;
        }

        // Build number has not changed yet
        return "";
    }

    private boolean checkIfCurrentBuildIsBuilding(String buildNum) {
        if (!isNumericBuildNumber(buildNum)) return false;
        try {
            String checkUrl = normalizedBase + "/" + buildNum + "/api/json?tree=building,timestamp";
            HttpRequest req = createRequestBuilder(checkUrl).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject json = safeParseJsonObject(res.body());
                if (json != null && json.has("building") && json.get("building").getAsBoolean()) {
                    if (json.has("timestamp")) {
                        long ts = json.get("timestamp").getAsLong();
                        if (System.currentTimeMillis() - ts < 10 * 60 * 1000) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String fetchProgressiveConsoleText() throws Exception {
        String logUrl = getLogUrl();
        HttpRequest request = createRequestBuilder(logUrl + "?start=" + start).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // If 404 on logText/progressiveText, fallback to consoleText
        if (response.statusCode() == 404 && !useConsoleTextFallback) {
            useConsoleTextFallback = true;
            logUrl = getLogUrl();
            request = createRequestBuilder(logUrl + "?start=" + start).GET().build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (response.statusCode() != 200) {
            hasMoreData = false;
            return formatErrorHtml(buildFriendlyHttpErrorMessage("Error fetching console log", response.statusCode(), contentType, response.body()));
        }

        String rawBody = response.body() != null ? response.body() : "";
        if (isHtmlResponse(contentType, rawBody)) {
            hasMoreData = false;
            return formatErrorHtml("Received HTML webpage instead of pipeline console log. Please verify CI/CD URL.");
        }

        String chunk;
        Optional<String> textSizeHeader = response.headers().firstValue("X-Text-Size");
        if (textSizeHeader.isPresent()) {
            try {
                start = Integer.parseInt(textSizeHeader.get());
            } catch (NumberFormatException ignored) {
                start += rawBody.length();
            }
            chunk = rawBody;
        } else {
            // When X-Text-Size is absent (e.g. consoleText endpoint or proxy stripped the header),
            // the response contains the entire log from byte 0. Extract only new content since offset 'start'.
            if (rawBody.length() > start) {
                chunk = rawBody.substring(start);
                start = rawBody.length();
            } else {
                chunk = "";
            }
        }

        // Update more-data flag from X-More-Data header if present
        Optional<String> moreDataHeader = response.headers().firstValue("X-More-Data");
        if (moreDataHeader.isPresent() && !"true".equalsIgnoreCase(moreDataHeader.get())) {
            hasMoreData = false;
        }

        if (chunk != null && !chunk.isEmpty()) {
            markDataReceived();
            // Parse stages and steps from progressive logs in real time
            parseStagesFromLogChunk(chunk);
        }
        String formattedChunk = (chunk != null && !chunk.isEmpty()) ? formatHtmlLog(chunk) : "";

        // Check build status via Jenkins API
        checkBuildFinishedViaApi();

        if (!hasMoreData) {
            // Append final build status message if build finished
            String status = fetchBuildResultStatus();
            finalizeStagesOnBuildFinished("SUCCESS".equalsIgnoreCase(status));
            formattedChunk = formattedChunk + "<br>" + formatInfoHtml("Build finished: " + status);
        }

        return formattedChunk;
    }

    private void parseStagesFromLogChunk(@NotNull String chunk) {
        String[] lines = chunk.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.length() > 1000) continue;

            boolean isPipeline = line.startsWith("[Pipeline]");
            boolean isEnteringStage = line.startsWith("Entering stage");

            // Check for stage start: [Pipeline] { (StageName) or [Pipeline] stage: StageName
            if (isPipeline || isEnteringStage) {
                Matcher stageMatcher = STAGE_START_PATTERN.matcher(line);
                if (stageMatcher.find()) {
                    String stageName = stageMatcher.group(1);
                    if (stageName == null) stageName = stageMatcher.group(2);
                    if (stageName == null) stageName = stageMatcher.group(3);
                    if (stageName != null) {
                        stageName = stageName.trim();
                        if (currentActiveStep != null && currentActiveStep.getStatus().isRunning()) {
                            currentActiveStep.setStatus(PipelineStatus.SUCCESS);
                        }
                        if (currentActiveStage != null && currentActiveStage.getStatus().isRunning()) {
                            currentActiveStage.setStatus(PipelineStatus.SUCCESS);
                        }
                        currentActiveStage = new PipelineStage("stage-" + (fallbackStages.size() + 1), stageName, PipelineStatus.IN_PROGRESS, 0);
                        fallbackStages.add(currentActiveStage);
                        currentActiveStep = null;
                        continue;
                    }
                }
            }

            // Check for step: [Pipeline] stepName
            if (isPipeline) {
                if (line.contains("// stage") || line.equals("[Pipeline] }")) {
                    if (currentActiveStep != null) {
                        currentActiveStep.appendLog(rawLine);
                        if (currentActiveStep.getStatus().isRunning()) {
                            currentActiveStep.setStatus(PipelineStatus.SUCCESS);
                        }
                    }
                    continue;
                }

                Matcher stepMatcher = STEP_PATTERN.matcher(line);
                if (stepMatcher.find()) {
                    String stepCmd = stepMatcher.group(1).trim();
                    if (!stepCmd.equalsIgnoreCase("stage")) {
                        if (currentActiveStage == null) {
                            String initialStageName = (stepCmd.equalsIgnoreCase("checkout") || stepCmd.equalsIgnoreCase("git"))
                                    ? "Checkout" : (stepCmd.equalsIgnoreCase("node") ? "Prepare" : "Build");
                            currentActiveStage = new PipelineStage("stage-1", initialStageName, PipelineStatus.IN_PROGRESS, 0);
                            fallbackStages.add(currentActiveStage);
                        }

                        if (currentActiveStep != null && currentActiveStep.getStatus().isRunning()) {
                            currentActiveStep.setStatus(PipelineStatus.SUCCESS);
                        }

                        currentActiveStep = new PipelineStep(
                                "step-" + (currentActiveStage.getSteps().size() + 1),
                                stepCmd,
                                PipelineStatus.IN_PROGRESS,
                                0
                        );
                        currentActiveStep.appendLog(rawLine);
                        currentActiveStage.addStep(currentActiveStep);
                    }
                }
            } else {
                if (currentActiveStep != null) {
                    currentActiveStep.appendLog(rawLine);
                }
            }
        }
    }

    private void finalizeStagesOnBuildFinished(boolean isSuccess) {
        for (PipelineStage stage : fallbackStages) {
            if (stage.getStatus().isRunning()) {
                stage.setStatus(isSuccess ? PipelineStatus.SUCCESS : PipelineStatus.FAILED);
            }
            for (PipelineStep step : stage.getSteps()) {
                if (step.getStatus().isRunning()) {
                    step.setStatus(isSuccess ? PipelineStatus.SUCCESS : PipelineStatus.FAILED);
                }
            }
        }
    }

    private void checkBuildFinishedViaApi() {
        try {
            HttpRequest request = createRequestBuilder(getApiUrl()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject json = safeParseJsonObject(response.body());
                if (json != null && json.has("building")) {
                    boolean isBuilding = json.get("building").getAsBoolean();
                    if (!isBuilding) {
                        hasMoreData = false;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback: keep hasMoreData unchanged if API check fails transiently
        }
    }

    private String fetchBuildResultStatus() {
        try {
            HttpRequest request = createRequestBuilder(getApiUrl()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject json = safeParseJsonObject(response.body());
                if (json != null && json.has("result") && !json.get("result").isJsonNull()) {
                    return json.get("result").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "COMPLETED";
    }
}
