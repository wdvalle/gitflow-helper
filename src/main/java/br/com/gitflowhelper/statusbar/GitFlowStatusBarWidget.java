package br.com.gitflowhelper.statusbar;

import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.popup.GitFlowPopup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.AnimatedIcon;
import com.intellij.util.ui.JBUI;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GitFlowStatusBarWidget implements CustomStatusBarWidget {

    private final Project project;
    private StatusBar statusBar;
    private boolean loading;

    private String currentValue = "GitFlowHelper";

    private JPanel component;
    private JProgressBar progressBar;
    private JLabel label;

    public GitFlowStatusBarWidget(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return "GitFlowWidget";
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        ActionParamsService.setProject(project);
        this.statusBar = statusBar;
    }

    @Override
    public void dispose() {
        ActionParamsService.setBranchName(null);
        ActionParamsService.setProject(null);
        ActionParamsService.clearRepos();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        updateUI();
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
        updateUI();
    }

    public void setProgress(int value) {
        if (progressBar != null && component != null) {
            CardLayout layout = (CardLayout) component.getLayout();
            if (value < 10) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(value);
                layout.show(component, "progress");
            } else {
                layout.show(component, "label");
            }
        }
    }

    private void updateUI() {
        if (label != null && component != null) {
            label.setText(currentValue);
            label.setIcon(loading ? AnimatedIcon.Default.INSTANCE : PluginIcons.GitFlow);

            CardLayout layout = (CardLayout) component.getLayout();
            if (loading) {
                progressBar.setIndeterminate(true);
                layout.show(component, "progress");
            } else {
                layout.show(component, "label");
            }
        }
    }

    @Override
    public @Nullable StatusBarWidget.WidgetPresentation getPresentation() {
        return null;
    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            component = new JPanel(new CardLayout());
            component.setOpaque(false);
            component.setBorder(JBUI.Borders.empty(0, 2));
            component.setToolTipText("Click to show Git Flow options");

            progressBar = new JProgressBar(0, 10);
            progressBar.setPreferredSize(JBUI.size(100, 4));
            progressBar.putClientProperty("ProgressBar.thin", Boolean.TRUE);

            JPanel progressWrapper = new JPanel(new GridBagLayout());
            progressWrapper.setOpaque(false);
            progressWrapper.add(progressBar);

            label = new JLabel(currentValue, PluginIcons.GitFlow, SwingConstants.LEFT);
            label.setOpaque(false);
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    new GitFlowPopup().show(component);
                }
            };
            
            label.addMouseListener(mouseAdapter);
            component.addMouseListener(mouseAdapter);
            progressWrapper.addMouseListener(mouseAdapter);
            progressBar.addMouseListener(mouseAdapter);

            component.add(label, "label");
            component.add(progressWrapper, "progress");
            
            ((CardLayout) component.getLayout()).show(component, "label");
        }
        return component;
    }
}
