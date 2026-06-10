package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class LikeAction extends BaseAction {
    public LikeAction(String actionTitle) {
        super(actionTitle, GitFlowDescriptions.INIT.getValue(), AllIcons.Ide.LikeSelected);
    }

    @Override
    protected void updateImpl(@NotNull AnActionEvent e) {
    }

    @Override
    protected void actionPerformedImpl(@NotNull AnActionEvent e) throws Exception {
        Long counter = GitFlowSettingsService.getInstance(e.getProject()).getCounter();
        Long diff = COUNTER_RESET - counter % COUNTER_RESET;
        GitFlowSettingsService.getInstance(e.getProject()).setCounter(counter+diff);
        BrowserUtil.browse("https://plugins.jetbrains.com/plugin/30207-git-flow-helper/reviews");
    }
}
