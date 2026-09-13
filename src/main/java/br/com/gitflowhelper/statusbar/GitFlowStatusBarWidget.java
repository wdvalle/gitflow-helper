package br.com.gitflowhelper.statusbar;

import br.com.gitflowhelper.popup.GitFlowPopup;
import br.com.gitflowhelper.util.ActionParamsService;
import com.intellij.openapi.project.Project;
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
    private volatile boolean loading;

    private String currentValue = "GitFlowHelper";

    private JPanel component;
    private JProgressBar progressBar;
    private JLabel label;
    private GitFlowPopup activePopup;

    public GitFlowStatusBarWidget(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return "GitFlowWidget";
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public void dispose() {
        if (activePopup != null && activePopup.getPopup() != null && !activePopup.getPopup().isDisposed()) {
            activePopup.getPopup().cancel();
        }
        ActionParamsService.clear(project);
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        if (loading && activePopup != null && activePopup.getPopup() != null && !activePopup.getPopup().isDisposed()) {
            activePopup.getPopup().cancel();
        }
        if (!loading && progressBar != null) {
            progressBar.setValue(0);
        }
        SwingUtilities.invokeLater(this::updateUI);
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
        SwingUtilities.invokeLater(this::updateUI);
    }

    public void setProgress(int value) {
        SwingUtilities.invokeLater(() -> {
            if (progressBar != null && component != null) {
                this.loading = (value < 10);
                if (this.loading && activePopup != null && activePopup.getPopup() != null && !activePopup.getPopup().isDisposed()) {
                    activePopup.getPopup().cancel();
                }
                CardLayout layout = (CardLayout) component.getLayout();
                if (value < 10) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                    layout.show(component, "progress");
                } else {
                    progressBar.setValue(0);
                    layout.show(component, "label");
                }
                updateCursorAndTooltip();
                if (label != null && !this.loading) {
                    label.setIcon(PluginIcons.GitFlow);
                }
                if (statusBar != null) {
                    statusBar.updateWidget("GitFlowWidget");
                }
            }
        });
    }

    private void updateUI() {
        if (label != null && component != null) {
            label.setText(currentValue);
            label.setIcon(loading ? AnimatedIcon.Default.INSTANCE : PluginIcons.GitFlow);

            CardLayout layout = (CardLayout) component.getLayout();
            if (loading) {
                layout.show(component, "progress");
            } else {
                layout.show(component, "label");
            }
            updateCursorAndTooltip();
            if (statusBar != null) {
                statusBar.updateWidget("GitFlowWidget");
            }
        }
    }

    private void updateCursorAndTooltip() {
        Cursor cursor = loading ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        String tooltip = loading ? null : "Click to show Git Flow options";

        if (component != null) {
            component.setCursor(cursor);
            component.setToolTipText(tooltip);
        }
        if (label != null) {
            label.setCursor(cursor);
            label.setToolTipText(tooltip);
        }
        if (progressBar != null) {
            progressBar.setCursor(cursor);
            progressBar.setToolTipText(tooltip);
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

            progressBar = new JProgressBar(0, 10);
            progressBar.setPreferredSize(JBUI.size(100, 4));
            progressBar.putClientProperty("ProgressBar.thin", Boolean.TRUE);

            JPanel progressWrapper = new JPanel(new GridBagLayout());
            progressWrapper.setOpaque(false);
            progressWrapper.add(progressBar);

            label = new JLabel(currentValue, PluginIcons.GitFlow, SwingConstants.LEFT);
            label.setOpaque(false);

            updateCursorAndTooltip();
            
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (loading) {
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        activePopup = new GitFlowPopup(project);
                        activePopup.show(component);
                    }
                }
            };
            
            label.addMouseListener(mouseAdapter);
            progressWrapper.addMouseListener(mouseAdapter);
            progressBar.addMouseListener(mouseAdapter);
            component.addMouseListener(mouseAdapter);

            component.add(label, "label");
            component.add(progressWrapper, "progress");
            
            ((CardLayout) component.getLayout()).show(component, "label");
        }
        return component;
    }
}
