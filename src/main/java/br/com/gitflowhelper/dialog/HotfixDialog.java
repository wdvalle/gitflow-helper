package br.com.gitflowhelper.dialog;

import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class HotfixDialog extends DialogWrapper {

    private final JCheckBox finishTaskCheckBox = new JCheckBox();
    private final Project project;

    public HotfixDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Finish Hotfix");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Do you really want to finish the hotfix?"), gbc);

        if (project != null && GitFlowSettingsService.getInstance(project).isIntegrateWithTasks()) {
            TaskManager taskManager = TaskManager.getManager(project);
            LocalTask activeTask = taskManager.getActiveTask();
            if (activeTask != null && !activeTask.isDefault()) {
                gbc.gridy++;
                finishTaskCheckBox.setText("Finish associated task " + activeTask.getPresentableName());
                finishTaskCheckBox.setSelected(true);
                panel.add(finishTaskCheckBox, gbc);
            }
        }

        return panel;
    }

    public boolean isFinishTask() {
        return finishTaskCheckBox.isSelected();
    }
}
