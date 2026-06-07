package br.com.gitflowhelper.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service(Service.Level.PROJECT)
@State(
        name = "GitFlowSettingsState",
        storages = @Storage("gitflow-helper.xml")
)
public final class GitFlowSettingsService
        implements PersistentStateComponent<GitFlowSettingsState> {

    private GitFlowSettingsState state = new GitFlowSettingsState();

    @Override
    public GitFlowSettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull GitFlowSettingsState state) {
        this.state = state;
    }

    public static GitFlowSettingsService getInstance(Project project) {
        return project.getService(GitFlowSettingsService.class);
    }

    public String getFeaturePrefix() {
        return state.getFeaturePrefix();
    }

    public void setFeaturePrefix(String featurePrefix) {
        state.setFeaturePrefix(featurePrefix);
    }

    public String getReleasePrefix() {
        return state.getReleasePrefix();
    }

    public void setReleasePrefix(String releasePrefix) {state.setReleasePrefix(releasePrefix);}

    public String getHotfixPrefix() {
        return state.getHotfixPrefix();
    }

    public void setHotfixPrefix(String hotfixPrefix) {
        state.setHotfixPrefix(hotfixPrefix);
    }

    public String getMainBranch() {return state.getMainBranch();}

    public void setMainBranch(String mainBranch) {state.setMainBranch(mainBranch);}

    public String getDevelopBranch() {return state.getDevelopBranch();}

    public void setDevelopBranch(String developBranch) {state.setDevelopBranch(developBranch);}

    public Long getCounter() { return state.getCounter(); }

    public void setCounter(Long counter) {state.setCounter(counter);}

    public Boolean getShowDetails() {
        if (state.getShowDetails() == null) {
            state.setShowDetails(true);
        }
        return state.getShowDetails();
    }

    public void setShowDetails(Boolean showDetails) {state.setShowDetails(showDetails);}

    public void resetAndDeleteStorage() {
        this.state = new GitFlowSettingsState();
        ApplicationManager.getApplication().saveSettings();
        Path optionsPath = Path.of(
                PathManager.getOptionsPath(),
                "gitflow-helper.xml"
        );
        try {
            Files.deleteIfExists(optionsPath);
        } catch (IOException e) {
            //throw new RuntimeException(e);
        }
    }
}
