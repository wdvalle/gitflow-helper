package br.com.gitflowhelper.settings;

import br.com.gitflowhelper.events.GitFlowSettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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

    public String getFeaturePrefix() {
        return state.getFeaturePrefix();
    }

    public void setFeaturePrefix(String featurePrefix) {
        state.setFeaturePrefix(featurePrefix);
        notifySettingsChanged();
    }

    public String getReleasePrefix() {
        return state.getReleasePrefix();
    }

public void setReleasePrefix(String releasePrefix) {
    state.setReleasePrefix(releasePrefix);
    notifySettingsChanged();
}

    public String getHotfixPrefix() {
        return state.getHotfixPrefix();
    }

    public void setHotfixPrefix(String hotfixPrefix) {
        state.setHotfixPrefix(hotfixPrefix);
        notifySettingsChanged();
    }

    public String getMainBranch() {
        return state.getMainBranch();
    }

    public void setMainBranch(String mainBranch) {
        state.setMainBranch(mainBranch);
        notifySettingsChanged();
    }

    public String getDevelopBranch() {
        return state.getDevelopBranch();
    }

    public void setDevelopBranch(String developBranch) {
        state.setDevelopBranch(developBranch);
        notifySettingsChanged();
    }

    public Long getCounter() {
        return state.getCounter();
    }

    public void setCounter(Long counter) {
        state.setCounter(counter);
        notifySettingsChanged();
    }

    public Boolean getShowDetails() {
        if (state.getShowDetails() == null) {
            state.setShowDetails(true);
        }
        return state.getShowDetails();
    }

    public void setShowDetails(Boolean showDetails) {
        state.setShowDetails(showDetails);
        notifySettingsChanged();
    }

    public boolean isIntegrateWithTasks() {
        return state.isIntegrateWithTasks();
    }

    public void setIntegrateWithTasks(boolean integrateWithTasks) {
        state.setIntegrateWithTasks(integrateWithTasks);
        notifySettingsChanged();
    }

    public boolean isIntegrateWithCI() {
        return state.isIntegrateWithCI();
    }

    public void setIntegrateWithCI(boolean integrateWithCI) {
        state.setIntegrateWithCI(integrateWithCI);
        notifySettingsChanged();
    }

    public String getCiType() {
        return state.getCiType();
    }

    public void setCiType(String ciType) {
        state.setCiType(ciType);
        notifySettingsChanged();
    }

    public String getCiUrl() {
        return state.getCiUrl();
    }

    public void setCiUrl(String ciUrl) {
        state.setCiUrl(ciUrl);
        notifySettingsChanged();
    }

    public String getCiToken() {
        return state.getCiToken();
    }

    public void setCiToken(String ciToken) {
        state.setCiToken(ciToken);
        notifySettingsChanged();
    }

    public void resetAndDeleteStorage() {
        this.state = new GitFlowSettingsState();
        ApplicationManager.getApplication().saveSettings();
        notifySettingsChanged();
    }
}
