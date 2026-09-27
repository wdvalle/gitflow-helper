package br.com.gitflowhelper.settings;

import java.util.Objects;

/**
 * Holds the CI/CD server configuration for a single project/repository.
 * Integration with CI/CD is considered active when {@link #getCiUrl()} is non-empty.
 * Sensitive tokens are stored securely in PasswordSafe and not held in this state.
 */
public class CiServerConfig {

    public static final String ACTION_TRIGGER_BUILD = "Trigger build on feature finish";
    public static final String ACTION_WAIT_BUILD    = "Wait build start and listen logs";
    public static final String ACTION_DO_NOTHING    = "Do nothing";

    private String ciType  = "Jenkins";
    private String ciUrl   = "";
    private String ciLogin = "";
    private String action  = ACTION_WAIT_BUILD;

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

    public String getAction() {
        return (action == null || action.isBlank()) ? ACTION_WAIT_BUILD : action;
    }

    public void setAction(String action) {
        this.action = (action == null || action.isBlank()) ? ACTION_WAIT_BUILD : action;
    }

    public boolean isTriggerBuild() {
        return ACTION_TRIGGER_BUILD.equals(getAction());
    }

    public boolean isWaitBuild() {
        return ACTION_WAIT_BUILD.equals(getAction());
    }

    public boolean isDoNothing() {
        return ACTION_DO_NOTHING.equals(getAction());
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
        c.action  = this.action;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CiServerConfig that = (CiServerConfig) o;
        return Objects.equals(ciType, that.ciType) &&
                Objects.equals(ciUrl, that.ciUrl) &&
                Objects.equals(ciLogin, that.ciLogin) &&
                Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ciType, ciUrl, ciLogin, action);
    }

    @Override
    public String toString() {
        return "CiServerConfig{ciType='" + ciType + "', ciUrl='" + ciUrl + "', ciLogin='" + ciLogin + "', action='" + action + "'}";
    }
}
