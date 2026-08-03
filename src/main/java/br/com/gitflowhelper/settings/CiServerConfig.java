package br.com.gitflowhelper.settings;

import java.util.Objects;

/**
 * Holds the CI/CD server configuration for a single project/repository.
 * Integration with CI/CD is considered active when {@link #getCiUrl()} is non-empty.
 * Sensitive tokens are stored securely in PasswordSafe and not held in this state.
 */
public class CiServerConfig {

    private String ciType  = "Jenkins";
    private String ciUrl   = "";
    private String ciLogin = "";

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

    public String getCiLogin() {
        return ciLogin;
    }

    public void setCiLogin(String ciLogin) {
        this.ciLogin = ciLogin;
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
        c.ciLogin = this.ciLogin;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CiServerConfig that = (CiServerConfig) o;
        return Objects.equals(ciType, that.ciType) &&
                Objects.equals(ciUrl, that.ciUrl) &&
                Objects.equals(ciLogin, that.ciLogin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ciType, ciUrl, ciLogin);
    }

    @Override
    public String toString() {
        return "CiServerConfig{ciType='" + ciType + "', ciUrl='" + ciUrl + "', ciLogin='" + ciLogin + "'}";
    }
}
