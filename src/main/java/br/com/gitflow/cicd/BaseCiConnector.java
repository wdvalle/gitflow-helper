package br.com.gitflow.cicd;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract base class for CI/CD server connectors (Jenkins, GitLab CI, GitHub Actions, etc.).
 *
 * <p>Provides common infrastructure such as:
 * <ul>
 *   <li>URL validation and normalization</li>
 *   <li>Safe linear HTML tag stripping and error snippet extraction (stack-overflow safe)</li>
 *   <li>Safe JSON payload parsing and HTML response detection</li>
 *   <li>HTTP client initialization with cookie management and redirects</li>
 *   <li>Basic authentication request building (customizable by subclasses)</li>
 *   <li>Log formatting with XML escaping and syntax highlight coloring</li>
 *   <li>Inactivity timeout monitoring and lifecycle flag management</li>
 *   <li>Pipeline blueprint caching for resilient DAG display</li>
 * </ul>
 */
public abstract class BaseCiConnector implements CiConnector {

    public static final long DEFAULT_INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    protected final String baseUrl;
    protected final String normalizedBase;
    protected final String login;
    protected final String token;
    protected final HttpClient httpClient;

    protected volatile boolean hasMoreData = true;
    protected volatile boolean buildTriggered = false;
    protected volatile boolean waitingForNewBuild = true;
    protected volatile long lastDataReceivedTime = System.currentTimeMillis();
    protected long inactivityTimeoutMs = DEFAULT_INACTIVITY_TIMEOUT_MS;

    protected final List<PipelineStage> blueprintStagesCache = new CopyOnWriteArrayList<>();
    protected PipelineRun latestPipelineRun = null;

    /**
     * Initializes the CI connector with base URL and credentials.
     *
     * @param baseUrl the target CI/CD job or server URL
     * @param login the username or account ID
     * @param token the API token, personal access token, or password
     */
    public BaseCiConnector(@NotNull String baseUrl, @Nullable String login, @Nullable String token) {
        this(baseUrl, login, token, null);
    }

    /**
     * Initializes the CI connector with an optional custom {@link HttpClient}.
     */
    public BaseCiConnector(@NotNull String baseUrl,
                           @Nullable String login,
                           @Nullable String token,
                           @Nullable HttpClient customClient) {
        this.baseUrl = baseUrl.trim();
        this.normalizedBase = normalizeBaseUrl(this.baseUrl);
        this.login = login != null ? login.trim() : "";
        this.token = token != null ? token.trim() : "";

        if (customClient != null) {
            this.httpClient = customClient;
        } else {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            this.httpClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // URL Validation & Normalization
    // -----------------------------------------------------------------------

    /**
     * Checks if the given URL is a valid HTTP or HTTPS URL.
     */
    public static boolean isValidHttpUrl(@Nullable String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * Strips trailing slashes from the given URL.
     */
    public static @NotNull String normalizeBaseUrl(@NotNull String url) {
        String base = url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Checks if a string represents a valid purely numeric build number (e.g. "42").
     */
    public static boolean isNumericBuildNumber(@Nullable String text) {
        return text != null && text.trim().matches("\\d+");
    }

    // -----------------------------------------------------------------------
    // HTML / JSON Inspection & Sanitization (StackOverflow Safe)
    // -----------------------------------------------------------------------

    /**
     * Checks whether an HTTP response returned an HTML document rather than data/logs.
     */
    public static boolean isHtmlResponse(@Nullable HttpResponse<?> response) {
        if (response == null) return false;
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String body = response.body() != null ? response.body().toString() : "";
        return isHtmlResponse(contentType, body);
    }

    /**
     * Checks whether the given Content-Type or body content indicates an HTML page.
     */
    public static boolean isHtmlResponse(@Nullable String contentType, @Nullable String body) {
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            return true;
        }
        if (body != null) {
            String trimmed = body.trim().toLowerCase();
            return trimmed.startsWith("<!doctype") || trimmed.startsWith("<html");
        }
        return false;
    }

    /**
     * Safely parses a JSON string into a {@link JsonObject}. Returns {@code null}
     * if the string is empty, starts with HTML tags, or does not represent a valid JSON object.
     */
    public static @Nullable JsonObject safeParseJsonObject(@Nullable String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || isHtmlResponse(null, trimmed)) {
            return null;
        }
        try {
            var elem = JsonParser.parseString(trimmed);
            return elem.isJsonObject() ? elem.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Builds a clear, user-friendly error message for an HTTP response, completely disregarding
     * any HTML body returned by the server (such as 404 / 401 / 403 / 500 HTML error pages).
     *
     * @param actionDescription a brief description of the operation that failed
     * @param statusCode the HTTP status code
     * @param contentType optional Content-Type header from response
     * @param body optional response body
     * @return a clean, friendly error message informing of the problem without HTML markup
     */
    public static @NotNull String buildFriendlyHttpErrorMessage(
            @NotNull String actionDescription,
            int statusCode,
            @Nullable String contentType,
            @Nullable String body
    ) {
        String detail;
        switch (statusCode) {
            case 401:
                detail = "Authentication failed (HTTP 401). Please check your username and API token.";
                break;
            case 403:
                detail = "Access denied (HTTP 403). Please verify permissions or API token.";
                break;
            case 404:
                detail = "Resource or job not found (HTTP 404). Please verify the CI/CD URL.";
                break;
            case 400:
                detail = "Bad request (HTTP 400). The CI server rejected the request.";
                break;
            case 405:
                detail = "Method not allowed (HTTP 405). The CI server does not support this HTTP method.";
                break;
            case 408:
                detail = "Request timeout (HTTP 408). The CI server took too long to respond.";
                break;
            case 500:
                detail = "Internal server error on CI server (HTTP 500).";
                break;
            case 502:
                detail = "Bad gateway (HTTP 502). The CI server or proxy is unreachable.";
                break;
            case 503:
                detail = "Service unavailable (HTTP 503). The CI server is temporarily offline or overloaded.";
                break;
            case 504:
                detail = "Gateway timeout (HTTP 504). The CI server took too long to respond.";
                break;
            default:
                if (statusCode >= 400 && statusCode < 500) {
                    detail = "Client error (HTTP " + statusCode + "). Please verify the CI/CD URL and settings.";
                } else if (statusCode >= 500) {
                    detail = "Server error (HTTP " + statusCode + "). Please check CI server health.";
                } else {
                    detail = "Unexpected response (HTTP " + statusCode + ").";
                }
                break;
        }

        // Only append body if it is NOT HTML, not blank, and is a clean plain-text snippet
        if (body != null && !body.isBlank() && !isHtmlResponse(contentType, body)) {
            String plainSnippet = extractErrorSnippet(body);
            if (!plainSnippet.isEmpty()) {
                return actionDescription + ": " + detail + " (" + plainSnippet + ")";
            }
        }

        return actionDescription + ": " + detail;
    }

    /**
     * Safely extracts a short plain-text snippet of an error body.
     * If the body contains or represents HTML, the HTML is completely disregarded and an empty string
     * is returned, preventing HTML tags and markup from leaking into user error messages.
     */
    public static @NotNull String extractErrorSnippet(@Nullable String body) {
        if (body == null || body.isBlank() || isHtmlResponse(null, body)) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("<") || trimmed.toLowerCase().contains("<html")) {
            return "";
        }
        if (trimmed.length() > 150) {
            trimmed = trimmed.substring(0, 150) + "...";
        }
        return trimmed.replace("\r", " ").replace("\n", " ").trim();
    }

    /**
     * Linearly strips HTML tags from a string without regex, preventing regex engine stack overflows.
     */
    public static @NotNull String stripHtmlSafely(@Nullable String html) {
        if (html == null || html.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(Math.min(html.length(), 500));
        int len = html.length();
        int i = 0;
        while (i < len) {
            char c = html.charAt(i);
            if (c == '<') {
                int closeIdx = html.indexOf('>', i);
                if (closeIdx == -1) break;
                String tag = html.substring(i + 1, closeIdx).trim().toLowerCase();
                if (tag.equals("br") || tag.startsWith("br/") || tag.startsWith("br ")) {
                    sb.append(' ');
                }
                i = closeIdx + 1;
            } else {
                if (c == '\r' || c == '\n' || c == '\t') {
                    sb.append(' ');
                } else {
                    sb.append(c);
                }
                i++;
            }
            if (sb.length() > 500) break;
        }
        return sb.toString().replaceAll(" +", " ").trim();
    }

    // -----------------------------------------------------------------------
    // Log & Message Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats raw text console output into HTML with XML escaping and colored lines.
     */
    public static @NotNull String formatHtmlLog(@Nullable String rawText) {
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
            } else if (trimmed.startsWith("[Pipeline]") || trimmed.startsWith("[CI]")) {
                color = "#81C784"; // Green
            } else if (trimmed.startsWith("[")) {
                color = "#FFB74D"; // Orange
            }

            if (color != null) {
                sb.append("<font color='").append(color).append("'>");
                sb.append(escapedLine);
                sb.append("</font>");
            } else {
                sb.append(escapedLine);
            }

            sb.append("<br>");
        }

        return sb.toString();
    }

    public static @NotNull String formatInfoHtml(@NotNull String message) {
        return "<font color='#FFFFFF'>" + message + "</font><br>";
    }

    public static @NotNull String formatErrorHtml(@NotNull String message) {
        return "<font color='#FF6B68'>Error: " + message + "</font><br>";
    }

    public static @NotNull String formatSuccessHtml(@NotNull String message) {
        return "<font color='#4CAF50'>" + message + "</font><br>";
    }

    // -----------------------------------------------------------------------
    // HTTP Request Helpers & Authentication
    // -----------------------------------------------------------------------

    /**
     * Creates an {@link HttpRequest.Builder} with the given URL and applies authentication.
     */
    protected HttpRequest.Builder createRequestBuilder(@NotNull String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        applyAuthentication(builder);
        return builder;
    }

    /**
     * Applies authentication headers to the HTTP request builder.
     * Default implementation applies HTTP Basic Authentication if token is present.
     * Subclasses for GitLab, GitHub, etc., may override to use Bearer or header tokens.
     */
    protected void applyAuthentication(@NotNull HttpRequest.Builder builder) {
        if (token != null && !token.isEmpty()) {
            String credentials = (login != null && !login.isEmpty() ? login : "") + ":" + token;
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
    }

    // -----------------------------------------------------------------------
    // Blueprint Caching
    // -----------------------------------------------------------------------

    /**
     * Updates the blueprint stage cache from the given list of stages if valid.
     */
    protected void updateBlueprintCache(@Nullable List<PipelineStage> stages) {
        if (stages == null || stages.isEmpty()) return;
        if (stages.size() >= blueprintStagesCache.size()) {
            blueprintStagesCache.clear();
            for (PipelineStage stage : stages) {
                blueprintStagesCache.add(new PipelineStage(stage.getId(), stage.getName(), PipelineStatus.NOT_STARTED, 0));
            }
        }
    }

    @Override
    public @NotNull List<PipelineStage> fetchBlueprintStages() {
        if (blueprintStagesCache.isEmpty()) {
            PipelineRun run = fetchPipelineRun();
            if (run != null && !run.getStages().isEmpty()) {
                updateBlueprintCache(run.getStages());
            }
        }
        return new ArrayList<>(blueprintStagesCache);
    }

    // -----------------------------------------------------------------------
    // Lifecycle, Triggers & Inactivity Monitoring
    // -----------------------------------------------------------------------

    @Override
    public void setBuildTriggered(boolean triggered) {
        this.buildTriggered = triggered;
        if (triggered) {
            this.waitingForNewBuild = true;
            this.latestPipelineRun = null;
        }
    }

    @Override
    public boolean isBuildTriggered() {
        return buildTriggered;
    }

    @Override
    public boolean isWaitingForNewBuild() {
        return waitingForNewBuild;
    }

    @Override
    public boolean hasMoreData() {
        return hasMoreData;
    }

    @Override
    public void stop() {
        this.hasMoreData = false;
    }

    @Override
    public void abortPipeline() {
        stop();
    }

    @Override
    public void abortPipeline(@NotNull String buildId) {
        abortPipeline();
    }

    /**
     * Checks if the inactivity timeout has expired. If so, flags {@code hasMoreData = false}
     * and returns a warning message; otherwise returns {@code null}.
     */
    protected @Nullable String checkInactivityTimeout() {
        if (System.currentTimeMillis() - lastDataReceivedTime > inactivityTimeoutMs) {
            hasMoreData = false;
            return formatInfoHtml("CI/CD monitoring stopped automatically: No data received for "
                    + (inactivityTimeoutMs / 60000) + " minutes.");
        }
        return null;
    }

    /**
     * Marks that data was received, resetting the inactivity timer.
     */
    protected void markDataReceived() {
        this.lastDataReceivedTime = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    @Override
    public @NotNull String getBaseUrl() {
        return baseUrl;
    }

    public @NotNull String getNormalizedBase() {
        return normalizedBase;
    }

    public @NotNull String getLogin() {
        return login;
    }

    public @NotNull String getToken() {
        return token;
    }

    public @NotNull HttpClient getHttpClient() {
        return httpClient;
    }
}
