package br.com.gitflowhelper.settings;

import java.util.Objects;

/**
 * Associates a Git repository (identified by its root path) with a
 * {@link CiServerConfig}. Used inside {@link GitFlowSettingsState} to
 * store one CI/CD config per repository.
 *
 * <p>All fields are public so IntelliJ's XmlSerializer can persist them
 * without needing extra annotations.</p>
 */
public class RepoCiEntry {

    /** Absolute path of the repository root (e.g. {@code /home/user/myproject}). */
    public String repoPath = "";

    /** Human-readable name shown in combo boxes (usually the root directory name). */
    public String repoName = "";

    /** CI/CD server configuration for this repository. */
    public CiServerConfig ciServer = new CiServerConfig();

    /** No-arg constructor required by IntelliJ's XmlSerializer. */
    public RepoCiEntry() {
    }

    public RepoCiEntry(String repoPath, String repoName) {
        this.repoPath = repoPath;
        this.repoName = repoName;
    }

    /** Returns a deep copy. */
    public RepoCiEntry copy() {
        RepoCiEntry c = new RepoCiEntry(this.repoPath, this.repoName);
        c.ciServer = this.ciServer.copy();
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepoCiEntry that = (RepoCiEntry) o;
        return Objects.equals(repoPath, that.repoPath) &&
                Objects.equals(repoName, that.repoName) &&
                Objects.equals(ciServer, that.ciServer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoPath, repoName, ciServer);
    }

    @Override
    public String toString() {
        return "RepoCiEntry{repoPath='" + repoPath + "', repoName='" + repoName + "', ciServer=" + ciServer + '}';
    }
}
