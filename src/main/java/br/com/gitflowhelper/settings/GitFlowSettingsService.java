package br.com.gitflowhelper.settings;

import br.com.gitflowhelper.events.GitFlowSettingsListener;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Service(Service.Level.PROJECT)
@State(
        name = "GitFlowSettingsState",
        storages = @Storage("gitflow-helper.xml")
)
public final class GitFlowSettingsService
        implements PersistentStateComponent<GitFlowSettingsState> {

    private final Project project;
    private GitFlowSettingsState state = new GitFlowSettingsState();

    public GitFlowSettingsService(Project project) {
        this.project = project;
    }

    @Override
    public GitFlowSettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull GitFlowSettingsState state) {
        this.state = state;
        project.getMessageBus().syncPublisher(GitFlowSettingsListener.TOPIC).settingsChanged();
    }

    public static GitFlowSettingsService getInstance(Project project) {
        return project.getService(GitFlowSettingsService.class);
    }

    private void notifySettingsChanged() {
        if (project != null && !project.isDisposed()) {
            project.getMessageBus().syncPublisher(GitFlowSettingsListener.TOPIC).settingsChanged();
        }
    }

    // ------------------------------------------------------------------
    // Git-flow prefix / branch
    // ------------------------------------------------------------------

    public String getFeaturePrefix() { return state.getFeaturePrefix(); }
    public void setFeaturePrefix(String v) { state.setFeaturePrefix(v); notifySettingsChanged(); }

    public String getReleasePrefix() { return state.getReleasePrefix(); }
    public void setReleasePrefix(String v) { state.setReleasePrefix(v); notifySettingsChanged(); }

    public String getHotfixPrefix() { return state.getHotfixPrefix(); }
    public void setHotfixPrefix(String v) { state.setHotfixPrefix(v); notifySettingsChanged(); }

    public String getMainBranch()    { return state.getMainBranch(); }
    public void setMainBranch(String v) { state.setMainBranch(v); notifySettingsChanged(); }

    public String getDevelopBranch() { return state.getDevelopBranch(); }
    public void setDevelopBranch(String v) { state.setDevelopBranch(v); notifySettingsChanged(); }

    // ------------------------------------------------------------------
    // Counter / display
    // ------------------------------------------------------------------

    public Long getCounter() { return state.getCounter(); }
    public void setCounter(Long v) { state.setCounter(v); notifySettingsChanged(); }

    public Boolean getShowDetails() {
        if (state.getShowDetails() == null) state.setShowDetails(true);
        return state.getShowDetails();
    }
    public void setShowDetails(Boolean v) { state.setShowDetails(v); notifySettingsChanged(); }

    public boolean isIntegrateWithTasks() { return state.isIntegrateWithTasks(); }
    public void setIntegrateWithTasks(boolean v) { state.setIntegrateWithTasks(v); notifySettingsChanged(); }

    public String getPreferredUsername() { return state.getPreferredUsername(); }
    public void setPreferredUsername(String v) { state.setPreferredUsername(v); notifySettingsChanged(); }

    // ------------------------------------------------------------------
    // CI/CD – per repository
    // ------------------------------------------------------------------

    /**
     * Returns all per-repository CI/CD entries for this project.
     */
    @NotNull
    public List<RepoCiEntry> getRepoCiEntries() {
        return state.getRepoCiEntries();
    }

    /**
     * Returns the {@link CiServerConfig} for the given repository root path.
     * If no entry exists yet, an empty (inactive) config is created on demand.
     *
     * @param repoPath absolute path of the repository root
     * @param repoName display name (directory name / remote name)
     */
    @NotNull
    public CiServerConfig getCiServerForRepo(@NotNull String repoPath, @NotNull String repoName) {
        return state.getCiServerForRepo(repoPath, repoName);
    }

    /**
     * Persists the {@link CiServerConfig} for a given repository and notifies listeners.
     *
     * @param repoPath absolute path of the repository root
     * @param repoName display name
     * @param cfg      configuration to store
     */
    public void setCiServerForRepo(@NotNull String repoPath,
                                   @NotNull String repoName,
                                   @NotNull CiServerConfig cfg) {
        state.setCiServerForRepo(repoPath, repoName, cfg);
        notifySettingsChanged();
    }

    /**
     * Removes the CI/CD server configuration for a given repository and deletes its token.
     */
    public void removeCiServerForRepo(@NotNull String repoPath) {
        state.removeCiServerForRepo(repoPath);
        saveTokenForRepo(repoPath, null);
        notifySettingsChanged();
    }

    /**
     * Returns the entry for the given repo path, or {@code null} if not configured.
     */
    @Nullable
    public RepoCiEntry getRepoCiEntry(@NotNull String repoPath) {
        return state.findEntry(repoPath);
    }

    /**
     * Returns {@code true} if <em>any</em> repository in this project has
     * an active CI/CD URL configured.
     */
    public boolean isIntegrateWithCI() {
        return state.getRepoCiEntries().stream().anyMatch(e -> e.ciServer.isActive());
    }

    /**
     * Returns {@code true} if the specific repository has an active CI/CD URL.
     *
     * @param repoPath absolute path of the repository root
     */
    public boolean isIntegrateWithCIForRepo(@NotNull String repoPath) {
        RepoCiEntry entry = state.findEntry(repoPath);
        return entry != null && entry.ciServer.isActive();
    }

    // ------------------------------------------------------------------
    // PasswordSafe Credential Storage
    // ------------------------------------------------------------------

    @NotNull
    private CredentialAttributes createCredentialAttributes(@NotNull String repoPath) {
        return new CredentialAttributes("GitFlowHelper:" + repoPath, "ciToken");
    }

    @Nullable
    public String getTokenForRepo(@NotNull String repoPath) {
        CredentialAttributes attributes = createCredentialAttributes(repoPath);
        return PasswordSafe.getInstance().getPassword(attributes);
    }

    public void saveTokenForRepo(@NotNull String repoPath, @Nullable String token) {
        CredentialAttributes attributes = createCredentialAttributes(repoPath);
        PasswordSafe.getInstance().setPassword(attributes, token);
    }

    public void resetAndDeleteStorage() {
        this.state = new GitFlowSettingsState();
        ApplicationManager.getApplication().saveSettings();
        notifySettingsChanged();
    }
}
