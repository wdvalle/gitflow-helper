package br.com.gitflow.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.http.HttpClient;

public abstract class IssueTrackerConnector {
    protected final String baseUrl;
    protected final String token;
    protected final HttpClient httpClient;
    protected final Gson gson;

    public IssueTrackerConnector(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public abstract boolean startIssue(String issueId);
    public abstract boolean assignIssue(String issueId, String assignee);
    public abstract boolean closeIssue(String issueId);
    public abstract IssueResponse getIssue(String issueId);
    public String issueToJson(IssueResponse issue) {
        return gson.toJson(issue);
    }
}
