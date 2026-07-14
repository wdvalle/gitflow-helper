package br.com.gitflowhelper.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ReleaseSyncAction extends SyncAction {
    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
        super.updateImpl(e);
        if (e.getPresentation().isEnabled()) {
            String branch = getBranchName(e.getProject());
            e.getPresentation().setEnabled(branch != null && branch.startsWith(getReleasePrefix(e.getProject())));
        }
    }
}
