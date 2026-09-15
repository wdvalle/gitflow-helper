package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class LogFontFamilyComboBoxAction extends ComboBoxAction {

    private final Project project;
    private final ToolWindowPanel toolWindowPanel;

    private static final List<String> PREFERRED_MONOSPACE_FONTS = List.of(
            "JetBrains Mono",
            "Fira Code",
            "Source Code Pro",
            "Menlo",
            "Monaco",
            "Consolas",
            "Courier New",
            "Inconsolata",
            "DejaVu Sans Mono",
            "Cascadia Code",
            "Liberation Mono",
            "Lucida Console"
    );

    public LogFontFamilyComboBoxAction(Project project, ToolWindowPanel toolWindowPanel) {
        this.project = project;
        this.toolWindowPanel = toolWindowPanel;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        String currentFont = getActiveFontFamily(e);
        e.getPresentation().setText(currentFont);
        e.getPresentation().setDescription("Select log font family");
    }

    @Override
    public @NotNull DefaultActionGroup createPopupActionGroup(JComponent button, DataContext context) {
        DefaultActionGroup group = new DefaultActionGroup();

        Project proj = project;
        if (proj == null) {
            proj = CommonDataKeys.PROJECT.getData(context);
        }

        String currentFont = toolWindowPanel != null
                ? toolWindowPanel.getCurrentFontFamily()
                : (proj != null ? GitFlowSettingsService.getInstance(proj).getLogFontFamily() : GitFlowSettingsService.getDefaultFontFamily());

        List<String> systemFonts = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        );

        boolean addedAny = false;
        // 1. Add current font if not part of preferred list but installed
        if (currentFont != null && !currentFont.trim().isEmpty()
                && !PREFERRED_MONOSPACE_FONTS.contains(currentFont)
                && systemFonts.contains(currentFont)) {
            group.add(createFontAction(currentFont, currentFont, proj, button));
            addedAny = true;
        }

        // 2. Add preferred monospace fonts installed on this system
        for (String fontName : PREFERRED_MONOSPACE_FONTS) {
            if (systemFonts.contains(fontName)) {
                group.add(createFontAction(fontName, currentFont, proj, button));
                addedAny = true;
            }
        }

        if (addedAny) {
            group.addSeparator();
        }

        // 3. Submenu with all installed fonts
        DefaultActionGroup allFontsGroup = new DefaultActionGroup("All Fonts", true);
        for (String fontName : systemFonts) {
            allFontsGroup.add(createFontAction(fontName, currentFont, proj, button));
        }
        group.add(allFontsGroup);

        return group;
    }

    private AnAction createFontAction(String fontName, String currentFont, Project proj, JComponent button) {
        return new AnAction(fontName) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (toolWindowPanel != null) {
                    toolWindowPanel.setFontFamily(fontName);
                } else {
                    Project p = proj != null ? proj : e.getProject();
                    if (p != null) {
                        GitFlowSettingsService.getInstance(p).setLogFontFamily(fontName);
                    }
                }
                button.repaint();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(true);
                if (fontName.equalsIgnoreCase(currentFont)) {
                    e.getPresentation().setText(fontName + " ✓");
                } else {
                    e.getPresentation().setText(fontName);
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };
    }

    private String getActiveFontFamily(AnActionEvent e) {
        if (toolWindowPanel != null) {
            return toolWindowPanel.getCurrentFontFamily();
        }
        Project p = project != null ? project : e.getProject();
        if (p != null && !p.isDisposed()) {
            return GitFlowSettingsService.getInstance(p).getLogFontFamily();
        }
        return GitFlowSettingsService.getDefaultFontFamily();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
