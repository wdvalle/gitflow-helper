package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LogFontSizeComboBoxAction extends ComboBoxAction {

    private static final int[] FONT_SIZES = {8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 28};

    private final Project project;
    private final ToolWindowPanel toolWindowPanel;

    public LogFontSizeComboBoxAction(Project project, ToolWindowPanel toolWindowPanel) {
        this.project = project;
        this.toolWindowPanel = toolWindowPanel;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        int currentSize = getActiveFontSize(e);
        e.getPresentation().setText(currentSize + " pt");
        e.getPresentation().setDescription("Select log font size");
    }

    @Override
    public @NotNull DefaultActionGroup createPopupActionGroup(JComponent button, DataContext context) {
        DefaultActionGroup group = new DefaultActionGroup();

        Project proj = project;
        if (proj == null) {
            proj = CommonDataKeys.PROJECT.getData(context);
        }

        int currentSize = toolWindowPanel != null
                ? toolWindowPanel.getCurrentFontSize()
                : (proj != null ? GitFlowSettingsService.getInstance(proj).getLogFontSize() : GitFlowSettingsService.getDefaultFontSize());

        for (int size : FONT_SIZES) {
            final Project finalProj = proj;
            group.add(new AnAction(size + " pt") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    if (toolWindowPanel != null) {
                        toolWindowPanel.setFontSize(size);
                    } else {
                        Project p = finalProj != null ? finalProj : e.getProject();
                        if (p != null) {
                            GitFlowSettingsService.getInstance(p).setLogFontSize(size);
                        }
                    }
                    button.repaint();
                }

                @Override
                public void update(@NotNull AnActionEvent e) {
                    e.getPresentation().setEnabled(true);
                    if (size == currentSize) {
                        e.getPresentation().setText(size + " pt ✓");
                    } else {
                        e.getPresentation().setText(size + " pt");
                    }
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.BGT;
                }
            });
        }

        return group;
    }

    private int getActiveFontSize(AnActionEvent e) {
        if (toolWindowPanel != null) {
            return toolWindowPanel.getCurrentFontSize();
        }
        Project p = project != null ? project : e.getProject();
        if (p != null && !p.isDisposed()) {
            return GitFlowSettingsService.getInstance(p).getLogFontSize();
        }
        return GitFlowSettingsService.getDefaultFontSize();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
