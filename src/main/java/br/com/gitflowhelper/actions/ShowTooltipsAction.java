package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.statusbar.GitFlowGuideManager;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowTooltipsAction extends BaseAction {

    public ShowTooltipsAction() {
        this("Show Tooltips");
    }

    public ShowTooltipsAction(String actionTitle) {
        super(actionTitle, GitFlowDescriptions.SHOW_TOOLTIPS.getValue(), AllIcons.General.ContextHelp);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            GitFlowGuideManager.resetAndShowAllTooltips(project);
        }
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
    }
}
