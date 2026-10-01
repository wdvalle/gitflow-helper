package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JenkinsConnector implements CiConnector {
    private static final Pattern STAGE_START_PATTERN = Pattern.compile("\\[Pipeline\\]\\s*\\{\\s*\\(([^\\)]+)\\)");
    private static final Pattern STEP_PATTERN = Pattern.compile("\\[Pipeline\\]\\s*([a-zA-Z0-9_-]+)");

    private final String login;
    private final String token;
    private final String normalizedBase;
    private final String buildTriggerUrl;
    private final String crumbIssuerUrl;
    private final String buildNumberUrl;
    private final HttpClient httpClient;

    private String crumb = null;
    private String crumbRequestField = null;

    private String baselineBuildNumber = null;
    private String targetBuildNumber = null;
    private boolean waitingForNewBuild = true;
    private boolean useConsoleTextFallback = false;

    // Tracks the byte offset for progressive log text requests
    private int start = 0;

    // True while monitoring is active and Jenkins reports data being produced
    private boolean hasMoreData = true;

    private long lastDataReceivedTime = System.currentTimeMillis();
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    // Real-time stages and steps extracted from log stream or wfapi
    private final List<PipelineStage> fallbackStages = new CopyOnWriteArrayList<>();
    private PipelineStage currentActiveStage = null;
    private PipelineStep currentActiveStep = null;
    private PipelineRun latestPipelineRun = null;

    public JenkinsConnector(String baseUrl, String login, String token) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (base.endsWith("/build")) {
            base = base.substring(0, base.length() - "/build".length());
        } else if (base.endsWith("/buildWithParameters")) {
            base = base.substring(0, base.length() - "/buildWithParameters".length());
        } else if (base.endsWith("/lastBuild")) {
            base = base.substring(0, base.length() - "/lastBuild".length());
        }
        this.normalizedBase = base;
        this.buildTriggerUrl = base + "/build";
        this.buildNumberUrl = base + "/lastBuild/buildNumber";

        int rootEnd = base.length();
        int jobIdx = base.indexOf("/job/");
        int viewIdx = base.indexOf("/view/");
        if (jobIdx != -1) {
            rootEnd = Math.min(rootEnd, jobIdx);
        }
        if (viewIdx != -1) {
            rootEnd = Math.min(rootEnd, viewIdx);
        }
        String jenkinsRoot = base.substring(0, rootEnd);
        this.crumbIssuerUrl = jenkinsRoot + "/crumbIssuer/api/json";

        this.login = login;
        this.token = token;

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
    public void stop() {
        this.hasMoreData = false;
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
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("crumb") && json.has("crumbRequestField")) {
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
        // Record the current build number as baseline before triggering so the newly started build is immediately detected
        if (baselineBuildNumber == null) {
            try {
                HttpRequest bReq = createRequestBuilder(buildNumberUrl).GET().build();
                HttpResponse<String> bRes = httpClient.send(bReq, HttpResponse.BodyHandlers.ofString());
                if (bRes.statusCode() == 200 && bRes.body() != null) {
                    baselineBuildNumber = bRes.body().trim();
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
                return paramResponse;
            }
        }

        String body = response.body() != null ? response.body().trim() : "";
        throw new IllegalStateException("Jenkins returned HTTP " + statusCode + (body.isEmpty() ? "" : ": " + body));
    }

    public static HttpResponse<String> triggerBuild(String baseUrl, String login, String token) throws Exception {
        return new JenkinsConnector(baseUrl, login, token).triggerBuild();
    }

    /**
     * Returns true while Jenkins signals monitoring should continue.
     * Becomes false after the build status API reports building = false, or on error/timeout.
     */
    @Override
    public boolean hasMoreData() {
        return hasMoreData;
    }

    /**
     * Fetches the current pipeline execution state including stages and steps.
     * Tries Jenkins Pipeline Stage View API (/wfapi/describe) first, and falls back to
     * standard API + parsed log stages if wfapi is unavailable.
     */
    @Override
    public @Nullable PipelineRun fetchPipelineRun() {
        // Try wfapi/describe first
        PipelineRun wfRun = fetchWfApiPipelineRun();
        if (wfRun != null && !wfRun.getStages().isEmpty()) {
            latestPipelineRun = wfRun;
            return wfRun;
        }

        // Fallback to /api/json + log-parsed stages
        PipelineRun apiRun = fetchBasicApiPipelineRun();
        if (apiRun != null) {
            if (!fallbackStages.isEmpty()) {
                apiRun.setStages(new ArrayList<>(fallbackStages));
            } else if (apiRun.getStatus().isRunning()) {
                // Synthesize an initial stage if running and no stages parsed yet
                PipelineStage initStage = new PipelineStage("stage-init", "Execution", PipelineStatus.IN_PROGRESS, 0);
                initStage.addStep(new PipelineStep("step-init", "Running tasks", PipelineStatus.IN_PROGRESS, 0));
                apiRun.addStage(initStage);
            }
            latestPipelineRun = apiRun;
            return apiRun;
        }

        return latestPipelineRun;
    }

    private @Nullable PipelineRun fetchWfApiPipelineRun() {
        try {
            HttpRequest request = createRequestBuilder(getWfApiUrl()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String id = json.has("id") ? json.get("id").getAsString() : (targetBuildNumber != null ? targetBuildNumber : "last");
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
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
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
     * Fetches the next available chunk of output from Jenkins.
     */
    @Override
    public @NotNull String fetchNextChunk() {
        if (!hasMoreData) {
            return "";
        }

        if (System.currentTimeMillis() - lastDataReceivedTime > INACTIVITY_TIMEOUT_MS) {
            hasMoreData = false;
            return "<font color='#FFFFFF'>CI/CD monitoring stopped automatically: No data received for 5 minutes.</font><br>";
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

        if (response.statusCode() != 200) {
            hasMoreData = false;
            return "Error fetching build number: HTTP " + response.statusCode();
        }

        String currentBuildNumber = response.body() != null ? response.body().trim() : "";

        if (baselineBuildNumber == null) {
            // First execution: record baseline build number
            baselineBuildNumber = currentBuildNumber;
            lastDataReceivedTime = System.currentTimeMillis();
            return "<font color='#FFFFFF'>Initial build number recorded: #" + baselineBuildNumber + ". Waiting for new build to start...</font><br>";
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
            lastDataReceivedTime = System.currentTimeMillis();
            String headerLog = "<font color='#FFFFFF'>New build detected: #" + currentBuildNumber + ". Fetching logs...</font><br>";
            String firstChunk = fetchProgressiveConsoleText();
            return headerLog + firstChunk;
        }

        // Build number has not changed yet
        return "";
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

        if (response.statusCode() != 200) {
            hasMoreData = false;
            return "Error: HTTP " + response.statusCode();
        }

        String rawBody = response.body() != null ? response.body() : "";
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
            lastDataReceivedTime = System.currentTimeMillis();
            // Parse stages and steps from progressive logs in real time
            parseStagesFromLogChunk(chunk);
        }
        String formattedChunk = (chunk != null && !chunk.isEmpty()) ? formatHtml(chunk) : "";

        // Check build status via Jenkins API
        checkBuildFinishedViaApi();

        if (!hasMoreData) {
            // Append final build status message if build finished
            String status = fetchBuildResultStatus();
            finalizeStagesOnBuildFinished("SUCCESS".equalsIgnoreCase(status));
            formattedChunk = formattedChunk + "<br><font color='#FFFFFF'>Build finished: " + status + "</font><br>";
        }

        return formattedChunk;
    }

    private void parseStagesFromLogChunk(@NotNull String chunk) {
        String[] lines = chunk.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Check for stage start: [Pipeline] { (StageName)
            Matcher stageMatcher = STAGE_START_PATTERN.matcher(line);
            if (stageMatcher.find()) {
                String stageName = stageMatcher.group(1).trim();
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

            // Check for step: [Pipeline] stepName
            if (line.startsWith("[Pipeline]")) {
                if (line.contains("// stage") || line.equals("[Pipeline] }")) {
                    if (currentActiveStep != null && currentActiveStep.getStatus().isRunning()) {
                        currentActiveStep.setStatus(PipelineStatus.SUCCESS);
                    }
                    continue;
                }

                Matcher stepMatcher = STEP_PATTERN.matcher(line);
                if (stepMatcher.find()) {
                    String stepCmd = stepMatcher.group(1).trim();
                    if (!stepCmd.equalsIgnoreCase("stage") && !stepCmd.equalsIgnoreCase("node")) {
                        if (currentActiveStage == null) {
                            currentActiveStage = new PipelineStage("stage-1", "Build", PipelineStatus.IN_PROGRESS, 0);
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
                        currentActiveStage.addStep(currentActiveStep);
                    }
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
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("building")) {
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
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("result") && !json.get("result").isJsonNull()) {
                    return json.get("result").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "COMPLETED";
    }

    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (token != null && !token.isEmpty()) {
            String credentials = (login != null && !login.isEmpty() ? login : "") + ":" + token;
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private String formatHtml(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        String[] lines = rawText.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty()) {
                break;
            }

            String escapedLine = line
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");

            String trimmed = line.trim();
            String color = null;

            if (trimmed.startsWith(">")) {
                color = "#4FC3F7"; // Blue
            } else if (trimmed.startsWith("[Pipeline]")) {
                color = "#81C784"; // Green
            } else if (trimmed.startsWith("[")) {
                color = "#FFB74D"; // Orange
            }

            if (color != null) {
                sb.append("<font color='").append(color).append("'>")
                  .append(escapedLine)
                  .append("</font>");
            } else {
                sb.append(escapedLine);
            }

            sb.append("<br>");
        }

        return sb.toString();
    }
}
