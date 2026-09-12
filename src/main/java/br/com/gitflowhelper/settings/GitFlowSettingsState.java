package br.com.gitflowhelper.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GitFlowSettingsState {

    private String featurePrefix = "feature";
    private String releasePrefix = "release";
    private String hotfixPrefix  = "hotfix";

    private String mainBranch;
    private String developBranch;

    private Long    counter;
    private Boolean showDetails;
    private Boolean integrateWithTasks = false;
    private String  preferredUsername;

    /**
     * Map of repository root path to whether it is selected/checked.
     * If a repository is absent, it defaults to true (checked).
     */
    private Map<String, Boolean> selectedRepositories = new HashMap<>();

    /**
     * CI/CD server configurations, one entry per Git repository root.
     * Key: repository root path; value: CI/CD server settings.
     */
    private List<RepoCiEntry> repoCiEntries = new ArrayList<>();

    public GitFlowSettingsState() {
    }

    // ------------------------------------------------------------------
    // Prefix / branch
    // ------------------------------------------------------------------

    public String getFeaturePrefix() { return featurePrefix; }
    public void setFeaturePrefix(String v) { this.featurePrefix = v; }

    public String getReleasePrefix() { return releasePrefix; }
    public void setReleasePrefix(String v) { this.releasePrefix = v; }

    public String getHotfixPrefix()  { return hotfixPrefix; }
    public void setHotfixPrefix(String v)  { this.hotfixPrefix = v; }

    public String getMainBranch()    { return mainBranch; }
    public void setMainBranch(String v)    { this.mainBranch = v; }

    public String getDevelopBranch() { return developBranch; }
    public void setDevelopBranch(String v) { this.developBranch = v; }

    // ------------------------------------------------------------------
    // Counter / display
    // ------------------------------------------------------------------

    public Long getCounter() {
        if (this.counter == null) this.counter = 0L;
        return counter;
    }
    public void setCounter(Long v) { this.counter = v; }

    public Boolean getShowDetails()              { return showDetails; }
    public void setShowDetails(Boolean v)        { this.showDetails = v; }

    public Boolean isIntegrateWithTasks()        { return integrateWithTasks; }
    public void setIntegrateWithTasks(Boolean v) { this.integrateWithTasks = v; }

    public String getPreferredUsername()          { return preferredUsername; }
    public void setPreferredUsername(String v)    { this.preferredUsername = v; }

    // ------------------------------------------------------------------
    // Selected repositories / branches
    // ------------------------------------------------------------------

    public Map<String, Boolean> getSelectedRepositories() {
        if (selectedRepositories == null) selectedRepositories = new HashMap<>();
        return selectedRepositories;
    }

    public void setSelectedRepositories(Map<String, Boolean> selectedRepositories) {
        this.selectedRepositories = selectedRepositories != null ? selectedRepositories : new HashMap<>();
    }

    public boolean isRepoSelected(String repoPath) {
        if (repoPath == null) return true;
        return getSelectedRepositories().getOrDefault(repoPath, true);
    }

    public void setRepoSelected(String repoPath, boolean selected) {
        if (repoPath != null) {
            getSelectedRepositories().put(repoPath, selected);
        }
    }

    // ------------------------------------------------------------------
    // Repo CI entries
    // ------------------------------------------------------------------

    /**
     * Returns the full list of per-repository CI/CD configurations.
     * Triggers one-time migration from legacy flat fields if needed.
     */
    public List<RepoCiEntry> getRepoCiEntries() {
        if (repoCiEntries == null) repoCiEntries = new ArrayList<>();
        return repoCiEntries;
    }

    public void setRepoCiEntries(List<RepoCiEntry> entries) {
        this.repoCiEntries = entries != null ? entries : new ArrayList<>();
    }

    /**
     * Finds the entry for the given repository root path, or {@code null}.
     */
    public RepoCiEntry findEntry(String repoPath) {
        return getRepoCiEntries().stream()
                .filter(e -> e.repoPath.equals(repoPath))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the {@link CiServerConfig} for the given repo path.
     * Creates a new (empty) entry if none exists yet.
     */
    public CiServerConfig getCiServerForRepo(String repoPath, String repoName) {
        RepoCiEntry entry = findEntry(repoPath);
        if (entry == null) {
            entry = new RepoCiEntry(repoPath, repoName);
            getRepoCiEntries().add(entry);
        }
        return entry.ciServer;
    }

    /**
     * Stores a {@link CiServerConfig} for the given repo path.
     * Updates an existing entry or adds a new one.
     */
    public void setCiServerForRepo(String repoPath, String repoName, CiServerConfig cfg) {
        List<RepoCiEntry> entries = getRepoCiEntries();
        for (RepoCiEntry e : entries) {
            if (e.repoPath.equals(repoPath)) {
                e.repoName = repoName;
                e.ciServer = cfg != null ? cfg : new CiServerConfig();
                return;
            }
        }
        RepoCiEntry newEntry = new RepoCiEntry(repoPath, repoName);
        newEntry.ciServer = cfg != null ? cfg : new CiServerConfig();
        entries.add(newEntry);
    }

    /**
     * Removes the entry for the given repository root path if it exists.
     */
    public void removeCiServerForRepo(String repoPath) {
        if (repoCiEntries != null) {
            repoCiEntries.removeIf(e -> e.repoPath.equals(repoPath));
        }
    }

    // ------------------------------------------------------------------

    @Override
    public String toString() {
        return "GitFlowSettingsState{" +
                "counter=" + counter +
                ", featurePrefix='" + featurePrefix + '\'' +
                ", releasePrefix='" + releasePrefix + '\'' +
                ", hotfixPrefix='" + hotfixPrefix + '\'' +
                ", mainBranch='" + mainBranch + '\'' +
                ", developBranch='" + developBranch + '\'' +
                ", showDetails=" + showDetails +
                ", integrateWithTasks=" + integrateWithTasks +
                ", selectedRepositories=" + selectedRepositories +
                ", repoCiEntries=" + repoCiEntries +
                ", preferredUsername='" + preferredUsername + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GitFlowSettingsState that = (GitFlowSettingsState) o;
        return Objects.equals(featurePrefix, that.featurePrefix) &&
                Objects.equals(releasePrefix, that.releasePrefix) &&
                Objects.equals(hotfixPrefix, that.hotfixPrefix) &&
                Objects.equals(mainBranch, that.mainBranch) &&
                Objects.equals(developBranch, that.developBranch) &&
                Objects.equals(counter, that.counter) &&
                Objects.equals(showDetails, that.showDetails) &&
                Objects.equals(integrateWithTasks, that.integrateWithTasks) &&
                Objects.equals(selectedRepositories, that.selectedRepositories) &&
                Objects.equals(repoCiEntries, that.repoCiEntries) &&
                Objects.equals(preferredUsername, that.preferredUsername);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featurePrefix, releasePrefix, hotfixPrefix, mainBranch,
                developBranch, counter, showDetails, integrateWithTasks,
                selectedRepositories, repoCiEntries, preferredUsername);
    }
}
