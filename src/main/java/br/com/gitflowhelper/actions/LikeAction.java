package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.GitFlowReviewCounter;
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
        long counter = GitFlowReviewCounter.getCounter();
        long diff = COUNTER_RESET - counter % COUNTER_RESET;
        GitFlowReviewCounter.setCounter(counter + diff);
        BrowserUtil.browse("https://plugins.jetbrains.com/plugin/30207-git-flow-helper/reviews");
    }
}
