package br.com.gitflow.cicd;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class JenkinsConnector {
    private final String login;
    private final String token;
    private final String logUrl;
    private final HttpClient httpClient;

    // Tracks the byte offset for the next progressive request
    private int start = 0;

    // True while Jenkins reports there is still data being produced (X-More-Data: true)
    private boolean hasMoreData = true;

    public JenkinsConnector(String baseUrl, String login, String token) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // Base URL should point to the last build, e.g.: http://jenkins/job/JOBNAME/lastBuild
        // The progressive text endpoint is appended automatically.
        this.logUrl = normalizedBase + "/logText/progressiveText";
        this.login = login;
        this.token = token;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Returns true while Jenkins signals there is still log data being produced.
     * Becomes false after a response with X-More-Data absent or "false", or on error.
     */
    public boolean hasMoreData() {
        return hasMoreData;
    }

    /**
     * Fetches the next available chunk of log output from Jenkins.
     * Each call performs a single HTTP request starting from the current offset.
     * Updates {@link #hasMoreData} and advances the internal offset automatically.
     *
     * @return the log chunk (HTML-safe, with line breaks converted to &lt;br&gt;),
     *         an empty string if no new content was available yet,
     *         or an "Error: ..." string on failure.
     */
    public String fetchNextChunk() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(logUrl + "?start=" + start))
                    .GET();

            if (token != null && !token.isEmpty()) {
                // Basic Auth: login:token encoded in Base64
                String credentials = (login != null && !login.isEmpty() ? login : "") + ":" + token;
                builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                hasMoreData = false;
                return "Error: " + response.statusCode();
            }

            // Advance the offset for the next request
            start = response.headers()
                    .firstValue("X-Text-Size")
                    .map(Integer::parseInt)
                    .orElse(start);

            // Update the more-data flag from the response header
            hasMoreData = response.headers()
                    .firstValue("X-More-Data")
                    .map("true"::equalsIgnoreCase)
                    .orElse(false);

            String chunk = response.body();
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }

            // Escape HTML special chars, then preserve line breaks for the HTML pane
            return chunk
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\r\n", "<br>")
                    .replace("\n", "<br>");

        } catch (Exception e) {
            hasMoreData = false;
            return "Error: " + e.getMessage();
        }
    }
}
