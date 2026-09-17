package br.com.gitflowhelper.statusbar;

import br.com.gitflowhelper.events.GitFlowDivergenceListener;
import br.com.gitflowhelper.popup.GitFlowPopup;
import br.com.gitflowhelper.service.DivergenceInfo;
import br.com.gitflowhelper.service.GitFlowDivergenceService;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.ActionParamsService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GitFlowStatusBarWidget implements CustomStatusBarWidget {

    private final Project project;
    private StatusBar statusBar;
    private volatile boolean loading;

    private String currentValue = "GitFlowHelper";

    private JPanel component;
    private JPanel labelWrapper;
    private JProgressBar progressBar;
    private JLabel label;
    private JLabel syncBadge;
    private GitFlowPopup activePopup;
    private volatile DivergenceInfo divergenceInfo = DivergenceInfo.EMPTY;

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

        project.getMessageBus().connect(this).subscribe(
                GitFlowDivergenceListener.TOPIC,
                new GitFlowDivergenceListener() {
                    @Override
                    public void divergenceUpdated(DivergenceInfo info) {
                        divergenceInfo = info;
                        SwingUtilities.invokeLater(GitFlowStatusBarWidget.this::updateBadgeUI);
                    }
                }
        );

        // Fetch initial divergence state if service is already initialized
        GitFlowDivergenceService divergenceService = GitFlowDivergenceService.getInstance(project);
        if (divergenceService != null) {
            this.divergenceInfo = divergenceService.getLatestInfo();
            SwingUtilities.invokeLater(this::updateBadgeUI);
        }

        SwingUtilities.invokeLater(() -> {
            if (component != null && component.isShowing()) {
                GitFlowGuideManager.checkAndShowStatusBarGotIt(project, component);
            }
        });
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
        if (!loading && project != null && !project.isDisposed()) {
            GitFlowDivergenceService divergenceService = GitFlowDivergenceService.getInstance(project);
            if (divergenceService != null) {
                divergenceService.requestCheck();
            }
        }
        SwingUtilities.invokeLater(() -> {
            updateUI();
            updateBadgeUI();
        });
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
                if (!this.loading && project != null && !project.isDisposed()) {
                    GitFlowDivergenceService divergenceService = GitFlowDivergenceService.getInstance(project);
                    if (divergenceService != null) {
                        divergenceService.requestCheck();
                    }
                }
                updateBadgeUI();
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

    private void updateBadgeUI() {
        if (syncBadge == null) return;

        DivergenceInfo info = this.divergenceInfo;
        if (info != null && info.hasDivergence() && !loading) {
            int count = info.getCommitsBehind();
            String commitsStr = count + " commit" + (count > 1 ? "s" : "");
            syncBadge.setText(" \u2b07 " + count + " ");
            if (label != null && label.getFont() != null) {
                syncBadge.setFont(label.getFont().deriveFont(Font.BOLD, Math.max(10f, label.getFont().getSize() - 1f)));
            }
            syncBadge.setForeground(new JBColor(new Color(0xB2, 0x48, 0x00), new Color(0xFF, 0xA0, 0x40)));
            syncBadge.setBackground(new JBColor(new Color(0xFF, 0xF3, 0xCD), new Color(0x3B, 0x30, 0x14)));
            syncBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new JBColor(new Color(0xFF, 0xDD, 0x80), new Color(0x66, 0x54, 0x22)), 1, true),
                    JBUI.Borders.empty(1, 4)
            ));
            syncBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            syncBadge.setToolTipText("Branch '" + info.getBranchName() + "' is " + commitsStr + " behind '" + info.getBaseBranch() + "'. Click to sync.");
            syncBadge.setVisible(true);
        } else {
            syncBadge.setVisible(false);
        }

        if (labelWrapper != null) {
            labelWrapper.revalidate();
            labelWrapper.repaint();
        }
        if (component != null) {
            component.revalidate();
            component.repaint();
        }

        if (statusBar != null) {
            statusBar.updateWidget("GitFlowWidget");
        }
    }

    private void updateCursorAndTooltip() {
        Cursor cursor = loading ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        String tooltip = loading ? null : "Click to show Git Flow options";

        if (component != null) {
            component.setCursor(cursor);
            component.setToolTipText(tooltip);
        }
        if (labelWrapper != null) {
            labelWrapper.setCursor(cursor);
            labelWrapper.setToolTipText(tooltip);
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

    private void triggerSync() {
        if (project == null || project.isDisposed()) return;
        DivergenceInfo info = this.divergenceInfo;
        if (info == null || !info.hasDivergence()) return;

        String branchName = info.getBranchName();
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);

        String actionId = "GitFlowHelper.FeatureSyncAction";
        if (branchName != null && settings != null) {
            if (settings.getReleasePrefix() != null && branchName.startsWith(settings.getReleasePrefix())) {
                actionId = "GitFlowHelper.ReleaseSyncAction";
            } else if (settings.getHotfixPrefix() != null && branchName.startsWith(settings.getHotfixPrefix())) {
                actionId = "GitFlowHelper.HotfixSyncAction";
            }
        }

        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action != null) {
            ActionParamsService.setBranchName(project, branchName);
            ActionManager.getInstance().tryToExecute(
                    action,
                    null,
                    syncBadge != null ? syncBadge : component,
                    ActionPlaces.STATUS_BAR_PLACE,
                    true
            );
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
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setOpaque(false);

            syncBadge = new JLabel();
            syncBadge.setVerticalAlignment(SwingConstants.CENTER);
            syncBadge.setOpaque(true);
            syncBadge.setVisible(false);

            syncBadge.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!loading && syncBadge.isVisible()) {
                        syncBadge.setBackground(new JBColor(new Color(0xFF, 0xE8, 0xA1), new Color(0x4D, 0x3E, 0x1A)));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!loading && syncBadge.isVisible()) {
                        syncBadge.setBackground(new JBColor(new Color(0xFF, 0xF3, 0xCD), new Color(0x3B, 0x30, 0x14)));
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (loading) return;
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        e.consume();
                        triggerSync();
                    }
                }
            });

            labelWrapper = new JPanel(new GridBagLayout());
            labelWrapper.setOpaque(false);
            labelWrapper.setBorder(JBUI.Borders.empty());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;

            gbc.gridx = 0;
            gbc.weightx = 0.0;
            gbc.insets = JBUI.emptyInsets();
            labelWrapper.add(label, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.0;
            gbc.insets = JBUI.insetsLeft(4);
            labelWrapper.add(syncBadge, gbc);

            updateCursorAndTooltip();

            MouseAdapter popupMouseAdapter = new MouseAdapter() {
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

            labelWrapper.addMouseListener(popupMouseAdapter);
            label.addMouseListener(popupMouseAdapter);
            progressWrapper.addMouseListener(popupMouseAdapter);
            progressBar.addMouseListener(popupMouseAdapter);

            component.add(labelWrapper, "label");
            component.add(progressWrapper, "progress");

            ((CardLayout) component.getLayout()).show(component, "label");

            updateBadgeUI();

            component.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && component.isShowing()) {
                    GitFlowGuideManager.checkAndShowStatusBarGotIt(project, component);
                }
            });
        }
        return component;
    }
}
