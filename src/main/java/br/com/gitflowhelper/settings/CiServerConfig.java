package br.com.gitflowhelper.settings;

import java.util.Objects;

/**
 * Holds the CI/CD server configuration for a single project.
 * Integration with CI/CD is considered active when {@link #getCiUrl()} is non-empty.
 */
public class CiServerConfig {

    private String ciType  = "Jenkins";
    private String ciUrl   = "";
    private String ciToken = "";

    public CiServerConfig() {
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getCiType() {
        return ciType;
    }

    public void setCiType(String ciType) {
        this.ciType = ciType;
    }

    public String getCiUrl() {
        return ciUrl;
    }

    public void setCiUrl(String ciUrl) {
        this.ciUrl = ciUrl;
    }

    public String getCiToken() {
        return ciToken;
    }

    public void setCiToken(String ciToken) {
        this.ciToken = ciToken;
    }

    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when a URL is configured, meaning CI/CD integration
     * is considered active — no explicit checkbox needed.
     */
    public boolean isActive() {
        return ciUrl != null && !ciUrl.isBlank();
    }

    /** Returns a deep copy of this config. */
    public CiServerConfig copy() {
        CiServerConfig c = new CiServerConfig();
        c.ciType  = this.ciType;
        c.ciUrl   = this.ciUrl;
        c.ciToken = this.ciToken;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CiServerConfig that = (CiServerConfig) o;
        return Objects.equals(ciType, that.ciType) &&
                Objects.equals(ciUrl, that.ciUrl) &&
                Objects.equals(ciToken, that.ciToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ciType, ciUrl, ciToken);
    }

    @Override
    public String toString() {
        return "CiServerConfig{ciType='" + ciType + "', ciUrl='" + ciUrl + "'}";
    }
}
