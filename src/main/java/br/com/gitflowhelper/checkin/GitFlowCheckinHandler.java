package br.com.gitflowhelper.checkin;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitBranchUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class GitFlowCheckinHandler extends CheckinHandler {
    private final CheckinProjectPanel panel;

    public GitFlowCheckinHandler(CheckinProjectPanel panel) {
        this.panel = panel;
    }

    @Override
    public ReturnResult beforeCheckin() {
        Project project = panel.getProject();
        String currentBranch = GitBranchUtils.getCurrentBranchName(project);
        String mainBranch = GitFlowSettingsService.getInstance(project).getMainBranch();
        String developBranch = GitFlowSettingsService.getInstance(project).getDevelopBranch();

        if (StringUtil.isNotEmpty(currentBranch)) {
            boolean isMain = StringUtil.isNotEmpty(mainBranch) && currentBranch.equals(mainBranch);
            boolean isDevelop = StringUtil.isNotEmpty(developBranch) && currentBranch.equals(developBranch);

            if (isMain || isDevelop) {
                int result = Messages.showYesNoDialog(
                        project,
                        "You are trying to commit directly to a protected branch (" + currentBranch + ").\n" +
                                "It is recommended to use features or hotfixes.\n\n" +
                                "Do you want to proceed anyway?",
                        "Protected Branch Warning",
                        "Commit Anyway",
                        "Cancel",
                        Messages.getWarningIcon()
                );
                if (result != Messages.YES) {
                    return ReturnResult.CANCEL;
                }
            }
        }

        return super.beforeCheckin();
    }
}
