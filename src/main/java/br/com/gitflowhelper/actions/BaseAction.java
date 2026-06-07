package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.statusbar.GitFlowStatusBarWidget;
import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.util.GitBranchUtils;
import br.com.gitflowhelper.util.NotificationUtil;
import br.com.gitflowhelper.util.PluginUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;

public abstract class BaseAction extends AnAction /*implements PropertyChangeListener*/ {
    public static final String REMOTE = "origin";
    public static final Long COUNTER_RESET = 50L;

    /*
    No fields allowed
    Classes based on AnAction must not have class fields of any kind.
    This is because an instance of AnAction class exists for the entire
    lifetime of the application. If the AnAction class uses a field to store
    data that has a shorter lifetime and doesn't clear this data promptly,
    the data leaks. For example, any AnAction data that exists only within
    the context of a Project causes the Project to be kept in memory after
    the user has closed it.

    https://plugins.jetbrains.com/docs/intellij/action-system.html
     */

    public BaseAction(
            String actionTitle,
            String description,
            Icon icon) {
        super(actionTitle, description, icon);
    }

    public BaseAction(
            Supplier<@NlsActions.ActionText String> description,
            Icon icon) {
        super(description, icon);
    }

    public BaseAction() { }

    protected abstract void updateImpl(@NotNull AnActionEvent e);

    protected abstract void actionPerformedImpl(@NotNull AnActionEvent e) throws Exception;

    public String getMainBranch() {
        return GitFlowSettingsService.getInstance(getProject()).getMainBranch();
    }
    public String getDevelopBranch() {
        return GitFlowSettingsService.getInstance(getProject()).getDevelopBranch();
    }
    public String getFeaturePrefix() {
        return GitFlowSettingsService.getInstance(getProject()).getFeaturePrefix();
    }
    public String getReleasePrefix() {
        return GitFlowSettingsService.getInstance(getProject()).getReleasePrefix();
    }
    public String getHotfixPrefix() {
        return GitFlowSettingsService.getInstance(getProject()).getHotfixPrefix();
    }
    public Project getProject() {
        return ActionParamsService.getProject();
    }
    public String getBranchName() {
        return ActionParamsService.getBranchName();
    }
    public void addRepo(AnAction action, GitRepository repo) { ActionParamsService.addRepo(action, repo); }
    public GitRepository getRepo(AnAction action) { return ActionParamsService.getRepo(action); }

    public void setLoading(boolean loading) {
        setLoading(loading, false);
    }

    public void setLoading(boolean loading, boolean progress) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(getProject());
        GitFlowStatusBarWidget sbw = (GitFlowStatusBarWidget) statusBar.getWidget("GitFlowWidget");
        sbw.setLoadding(loading);
        if (progress) {
            setProgressImpl(0, statusBar, sbw);
        } else {
            sbw.setCurrentValue("GitFlowHelper");
        }
        statusBar.updateWidget("GitFlowWidget");
    }

    public void setProgress(Integer value) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(getProject());
        GitFlowStatusBarWidget sbw = (GitFlowStatusBarWidget) statusBar.getWidget("GitFlowWidget");
        setProgressImpl(value, statusBar, sbw);
    }

    private void setProgressImpl(Integer value, StatusBar statusBar, GitFlowStatusBarWidget sbw) {
        StringBuilder percent = new StringBuilder();
        percent.repeat("█", Math.max(0, value));
        percent.repeat(" ", Math.max(0, 10-value));
        sbw.setCurrentValue(percent.toString()+" "+(value > 0? value : "")+"0%");
        statusBar.updateWidget("GitFlowWidget");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        var future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ActionParamsService.setBranchName(GitBranchUtils.getCurrentBranchName(getProject()));
        });
        try {
            future.get();
        }  catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        updateImpl(e);
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        try {
            actionPerformedImpl(e);
        } catch (Throwable ex) {
            handleGlobalException(ex, e);
        }
    }

    private void handleGlobalException(Throwable ex, @NotNull AnActionEvent e) {
        NotificationUtil.showGitFlowErrorNotification(e.getProject(), "Error", ex.getMessage());
        PluginUtils.logError(getProject(), PluginUtils.getStackTrace(ex));
    }

}
