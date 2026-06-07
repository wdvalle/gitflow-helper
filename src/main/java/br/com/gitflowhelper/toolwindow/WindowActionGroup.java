package br.com.gitflowhelper.toolwindow;

import com.intellij.openapi.actionSystem.DefaultActionGroup;

import javax.swing.*;

public class WindowActionGroup extends DefaultActionGroup {

    public WindowActionGroup(JTextPane textPane, Boolean showDetails) {
        add(new ClearToolWindowAction(textPane));
        add(new StatusComboBoxAction(textPane, showDetails));
        addSeparator();
    }
}
