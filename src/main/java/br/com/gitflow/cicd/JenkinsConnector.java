package br.com.gitflow.cicd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class JenkinsConnector {
    private final String login;
    private final String token;
    private final String buildNumberUrl;
    private final String consoleTextUrl;
    private final String apiUrl;
    private final HttpClient httpClient;

    private String baselineBuildNumber = null;
    private boolean waitingForNewBuild = true;

    // Tracks the byte offset for progressive log text requests
    private int start = 0;

    // True while monitoring is active and Jenkins reports data being produced
    private boolean hasMoreData = true;

    public JenkinsConnector(String baseUrl, String login, String token) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.buildNumberUrl = normalizedBase + "/lastBuild/buildNumber";
        this.consoleTextUrl = normalizedBase + "/lastBuild/consoleText";
        this.apiUrl = normalizedBase + "/lastBuild/api/json?tree=building,result";
        this.login = login;
        this.token = token;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Returns true while Jenkins signals monitoring should continue.
     * Becomes false after the build status API reports building = false, or on error.
     */
    public boolean hasMoreData() {
        return hasMoreData;
    }

    /**
     * Fetches the next available chunk of output from Jenkins.
     *
     * <p>Initial phase: Queries /buildNumber to record the initial build number,
     * and continues checking /buildNumber until the build number changes.
     *
     * <p>Log phase: Once the build number changes, performs progressive requests to /consoleText
     * and checks build completion via /api/json?tree=building,result.
     *
     * @return log output chunk (or empty string while waiting / no new data), or error string.
     */
    public String fetchNextChunk() {
        if (!hasMoreData) {
            return "";
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
            return "Initial build number recorded: #" + baselineBuildNumber + ". Waiting for new build to start...";
        }

        if (!currentBuildNumber.equals(baselineBuildNumber)) {
            // Build number changed! Start progressive log reading
            baselineBuildNumber = currentBuildNumber;
            waitingForNewBuild = false;
            start = 0;
            String headerLog = "New build detected: #" + currentBuildNumber + ". Fetching logs...<br>";
            String firstChunk = fetchProgressiveConsoleText();
            return headerLog + firstChunk;
        }

        // Build number has not changed yet
        return "";
    }

    private String fetchProgressiveConsoleText() throws Exception {
        HttpRequest request = createRequestBuilder(consoleTextUrl + "?start=" + start).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            hasMoreData = false;
            return "Error: HTTP " + response.statusCode();
        }

        // Advance the offset for the next request
        start = response.headers()
                .firstValue("X-Text-Size")
                .map(Integer::parseInt)
                .orElseGet(() -> start + (response.body() != null ? response.body().length() : 0));

        String chunk = response.body();
        String formattedChunk = (chunk != null && !chunk.isEmpty()) ? formatHtml(chunk) : "";

        // Check build status via Jenkins API
        checkBuildFinishedViaApi();

        if (!hasMoreData) {
            // Append final build status message if build finished
            String status = fetchBuildResultStatus();
            formattedChunk = formattedChunk + "<br>Build finished: " + status;
        }

        return formattedChunk;
    }

    private void checkBuildFinishedViaApi() {
        try {
            HttpRequest request = createRequestBuilder(apiUrl).GET().build();
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
            HttpRequest request = createRequestBuilder(apiUrl).GET().build();
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
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
        }
        return builder;
    }

    private String formatHtml(String rawText) {
        return rawText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");
    }
}
