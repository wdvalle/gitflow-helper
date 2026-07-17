package br.com.gitflow.cicd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class JenkinsConnector {
    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;

    public JenkinsConnector(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public String getBuildStatus(String jobName) {
        try {
            // No Jenkins, geralmente acessamos /job/JOBNAME/lastBuild/api/json
            String url = baseUrl + "/job/" + jobName + "/lastBuild/api/json";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            if (token != null && !token.isEmpty()) {
                // Se o token for "user:apiToken", usamos Basic Auth
                // Se for apenas o token, depende da configuração do Jenkins, mas geralmente é user:token
                String auth = Base64.getEncoder().encodeToString(token.getBytes());
                builder.header("Authorization", "Basic " + auth);
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                boolean building = json.get("building").getAsBoolean();
                String result = json.get("result").isJsonNull() ? "IN PROGRESS" : json.get("result").getAsString();
                int number = json.get("number").getAsInt();
                
                return "Build #" + number + ": " + (building ? "IN PROGRESS" : result);
            } else {
                return "Error: " + response.statusCode();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
