package br.com.gitflowhelper;

import br.com.gitflowhelper.actions.ShowTooltipsAction;
import br.com.gitflowhelper.statusbar.GitFlowGuideManager;
import br.com.gitflowhelper.toolwindow.GitFlowToolWindowFactory;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GuideManagerTest {

    @Test
    public void testShowTooltipsActionPresentation() {
        ShowTooltipsAction action = new ShowTooltipsAction();
        assertEquals("Show Tooltips", action.getTemplatePresentation().getText());
        assertEquals(GitFlowDescriptions.SHOW_TOOLTIPS.getValue(), action.getTemplatePresentation().getDescription());
        assertNotNull(action.getTemplatePresentation().getIcon());
    }

    @Test
    public void testTooltipIdsAreDistinctAndDefined() {
        List<String> ids = List.of(
                GitFlowGuideManager.GOT_IT_STATUS_BAR_ID,
                GitFlowGuideManager.GOT_IT_TOOL_WINDOW_STRIPE_ID,
                GitFlowToolWindowFactory.GOT_IT_LOGS_TAB_ID,
                GitFlowToolWindowFactory.GOT_IT_ISSUES_TAB_ID,
                GitFlowToolWindowFactory.GOT_IT_FLOW_TAB_ID,
                GitFlowToolWindowFactory.GOT_IT_CICD_TAB_ID
        );

        for (String id : ids) {
            assertNotNull(id);
            assertFalse(id.isEmpty());
        }

        Set<String> uniqueIds = new HashSet<>(ids);
        assertEquals(ids.size(), uniqueIds.size(), "All tooltip IDs must be unique");
    }

    @Test
    public void testResetAllTooltipsSafeWhenNoAppRunning() {
        assertDoesNotThrow(GitFlowGuideManager::resetAllTooltips);
        assertDoesNotThrow(GitFlowToolWindowFactory::resetTourState);
        assertDoesNotThrow(GitFlowGuideManager::isInitialGuidePending);
    }
}
