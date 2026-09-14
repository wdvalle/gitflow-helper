package br.com.gitflowhelper.toolwindow;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class WindowActionGroup extends DefaultActionGroup {

    public WindowActionGroup(Project project, ToolWindowPanel toolWindowPanel, JTextPane textPane, Boolean showDetails) {
        add(new ClearToolWindowAction(toolWindowPanel, textPane));
        add(new StatusComboBoxAction(textPane, showDetails));
        addSeparator();
        add(new LogFontFamilyComboBoxAction(project, toolWindowPanel));
        add(new LogFontSizeComboBoxAction(project, toolWindowPanel));
        addSeparator();
    }

    public WindowActionGroup(JTextPane textPane, Boolean showDetails) {
        this(null, null, textPane, showDetails);
    }
}
