package br.com.gitflowhelper.settings;

import java.util.Objects;

public class GitFlowSettingsState {

    private String featurePrefix = "feature";
    private String releasePrefix = "release";
    private String hotfixPrefix = "hotfix";

    private String mainBranch;
    private String developBranch;

    private Long counter;
    private Boolean showDetails;

    public GitFlowSettingsState() {
    }

    public GitFlowSettingsState(String featurePrefix, String releasePrefix, String hotfixPrefix,
                                String mainBranch, String developBranch, Long counter) {
        this.featurePrefix = featurePrefix;
        this.releasePrefix = releasePrefix;
        this.hotfixPrefix = hotfixPrefix;
        this.mainBranch = mainBranch;
        this.developBranch = developBranch;
        this.counter = counter;
    }

    public String getFeaturePrefix() {
        return featurePrefix;
    }

    public void setFeaturePrefix(String featurePrefix) {
        this.featurePrefix = featurePrefix;
    }

    public String getReleasePrefix() {
        return releasePrefix;
    }

    public void setReleasePrefix(String releasePrefix) {
        this.releasePrefix = releasePrefix;
    }

    public String getHotfixPrefix() {
        return hotfixPrefix;
    }

    public void setHotfixPrefix(String hotfixPrefix) {
        this.hotfixPrefix = hotfixPrefix;
    }

    public String getMainBranch() {return mainBranch;}

    public void setMainBranch(String mainBranch) {this.mainBranch = mainBranch;}

    public String getDevelopBranch() {return developBranch;}

    public void setDevelopBranch(String developBranch) {this.developBranch = developBranch;}

    public Long getCounter() {
        if (this.counter == null) {
            this.counter = 0L;
        }
        return counter;
    }

    public void setCounter(Long counter) {this.counter = counter;}

    public Boolean getShowDetails() {return showDetails;}

    public void setShowDetails(Boolean showDetails) {this.showDetails = showDetails;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GitFlowSettingsState)) return false;
        GitFlowSettingsState that = (GitFlowSettingsState) o;
        return Objects.equals(featurePrefix, that.featurePrefix)
                && Objects.equals(releasePrefix, that.releasePrefix)
                && Objects.equals(hotfixPrefix, that.hotfixPrefix)
                && Objects.equals(mainBranch, that.mainBranch)
                && Objects.equals(developBranch, that.developBranch)
                && Objects.equals(counter, that.counter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featurePrefix, releasePrefix, hotfixPrefix, mainBranch, developBranch, counter);
    }

    @Override
    public String toString() {
        return "GitFlowSettingsState{" +
                "featurePrefix='" + featurePrefix + '\'' +
                ", releasePrefix='" + releasePrefix + '\'' +
                ", hotfixPrefix='" + hotfixPrefix + '\'' +
                ", mainBranch='" + mainBranch + '\'' +
                ", developBranch='" + developBranch + '\'' +
                ", counter=" + counter +
                '}';
    }
}
