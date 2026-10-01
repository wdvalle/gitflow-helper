package br.com.gitflowhelper.toolwindow.ci;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStatus;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Top header panel displaying basic pipeline execution info, status badges, and quick actions.
 */
public class PipelineHeaderPanel extends JPanel {

    private final JLabel platformLabel = new JLabel("CI/CD");
    private final JLabel buildNumberLabel = new JLabel("No Build");
    private final StatusBadge statusBadge = new StatusBadge();
    private final JLabel branchLabel = new JLabel();
    private final JLabel durationLabel = new JLabel();

    private final HeaderActionButton rerunBtn = new HeaderActionButton(AllIcons.Actions.Execute, "Start pipeline");
    private final HeaderActionButton stopBtn = new HeaderActionButton(AllIcons.Actions.Suspend, "Stop pipeline execution");
    private final HeaderActionButton clearBtn = new HeaderActionButton(AllIcons.Actions.GC, "Clear pipeline output");
    private final HeaderActionButton openBrowserBtn = new HeaderActionButton(AllIcons.Ide.External_link_arrow, "Open pipeline URL in default browser");

    private final JToggleButton toggleSplitBtn = new JToggleButton(AllIcons.Actions.PreviewDetails) {
        {
            setContentAreaFilled(false);
            setOpaque(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFocusable(false);
            setMargin(new Insets(2, 4, 2, 4));
            setPreferredSize(new Dimension(28, 24));
            putClientProperty("JButton.buttonType", "toolBarButton");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                Color selBg = new JBColor(new Color(215, 232, 255), new Color(40, 65, 98));
                Color selBorder = new JBColor(new Color(130, 175, 235), new Color(70, 110, 165));
                g2.setColor(selBg);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                g2.setColor(selBorder);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
            } else if (getModel().isPressed()) {
                Color pressedBg = new JBColor(new Color(220, 224, 230), new Color(68, 72, 78));
                g2.setColor(pressedBg);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
            } else if (getModel().isRollover()) {
                Color hoverBg = new JBColor(new Color(238, 240, 243), new Color(55, 58, 62));
                g2.setColor(hoverBg);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    };

    private String buildUrl = null;
    private Runnable onRerunTrigger;
    private Runnable onStopMonitoring;
    private Runnable onClearTrigger;
    private Runnable onToggleSplit;
    private boolean isSplit = false;
    private boolean isRunning = false;

    public PipelineHeaderPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                new EmptyBorder(6, 12, 6, 12)
        ));
        setBackground(new JBColor(new Color(250, 251, 252), new Color(38, 40, 42)));

        // Left section: Platform + Build # + Status + Branch + Duration
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        infoPanel.setOpaque(false);

        platformLabel.setFont(platformLabel.getFont().deriveFont(Font.BOLD, 12f));
        platformLabel.setForeground(JBColor.GRAY);
        infoPanel.add(platformLabel);

        buildNumberLabel.setFont(buildNumberLabel.getFont().deriveFont(Font.BOLD, 13f));
        buildNumberLabel.setVisible(false);
        infoPanel.add(buildNumberLabel);

        infoPanel.add(statusBadge);
        rerunBtn.setHighlighted(true);

        branchLabel.setIcon(AllIcons.Vcs.Branch);
        branchLabel.setFont(branchLabel.getFont().deriveFont(Font.PLAIN, 12f));
        branchLabel.setVisible(false);
        infoPanel.add(branchLabel);

        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.PLAIN, 12f));
        durationLabel.setForeground(JBColor.GRAY);
        durationLabel.setVisible(false);
        infoPanel.add(durationLabel);

        add(infoPanel, BorderLayout.WEST);

        // Right section: Quick action buttons (Start, Stop, Clear, Open Browser, Toggle Console)
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionsPanel.setOpaque(false);

        rerunBtn.addActionListener(e -> {
            if (!isRunning && onRerunTrigger != null) {
                setRunning(true);
                onRerunTrigger.run();
            }
        });
        actionsPanel.add(rerunBtn);

        stopBtn.addActionListener(e -> {
            if (isRunning && onStopMonitoring != null) {
                setRunning(false);
                onStopMonitoring.run();
            }
        });
        actionsPanel.add(stopBtn);

        clearBtn.addActionListener(e -> {
            if (onClearTrigger != null) {
                onClearTrigger.run();
            }
        });
        actionsPanel.add(clearBtn);

        openBrowserBtn.setEnabled(false);
        openBrowserBtn.addActionListener(e -> {
            if (buildUrl != null && !buildUrl.isEmpty()) {
                BrowserUtil.browse(buildUrl);
            }
        });
        actionsPanel.add(openBrowserBtn);

        toggleSplitBtn.setToolTipText("Show console logs (hidden)");
        toggleSplitBtn.setSelected(false);
        toggleSplitBtn.addActionListener(e -> {
            if (onToggleSplit != null) {
                onToggleSplit.run();
            }
        });
        actionsPanel.add(toggleSplitBtn);

        add(actionsPanel, BorderLayout.EAST);

        // Initial state: pipeline is not running (start enabled, stop disabled)
        setRunning(false);
    }

    public void setPlatformName(@NotNull String platformName) {
        platformLabel.setText(platformName);
    }

    public String getPlatformName() {
        return platformLabel.getText();
    }

    public void setSplitMode(boolean split) {
        this.isSplit = split;
        toggleSplitBtn.setSelected(split);
        toggleSplitBtn.setToolTipText(split ? "Hide console logs (panel open)" : "Show console logs (panel hidden)");
        toggleSplitBtn.repaint();
    }

    public boolean isSplitMode() {
        return isSplit;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
        rerunBtn.setEnabled(!running);
        rerunBtn.setHighlighted(!running);
        stopBtn.setEnabled(running);
        rerunBtn.setToolTipText(!running ? "Start pipeline" : "Pipeline is currently running");
        stopBtn.setToolTipText(running ? "Stop pipeline execution" : "No pipeline is currently running");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public JButton getStartButton() {
        return rerunBtn;
    }

    public JButton getStopButton() {
        return stopBtn;
    }

    public JButton getClearButton() {
        return clearBtn;
    }

    public JButton getOpenBrowserButton() {
        return openBrowserBtn;
    }

    public JToggleButton getToggleSplitBtn() {
        return toggleSplitBtn;
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop) {
        setCallbacks(onRerun, onStop, null, null);
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onToggleSplit) {
        setCallbacks(onRerun, onStop, onToggleSplit, null);
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onToggleSplit, @Nullable Runnable onClear) {
        this.onRerunTrigger = onRerun;
        this.onStopMonitoring = onStop;
        this.onToggleSplit = onToggleSplit;
        this.onClearTrigger = onClear;
    }

    public void markAborted() {
        statusBadge.setStatus(PipelineStatus.ABORTED);
        setRunning(false);
    }

    public void updatePipelineRun(@Nullable PipelineRun run, @Nullable String platformName) {
        if (platformName != null) {
            platformLabel.setText(platformName);
        }

        if (run == null) {
            buildNumberLabel.setVisible(false);
            statusBadge.setStatus(PipelineStatus.NOT_STARTED);
            statusBadge.setVisible(true);
            branchLabel.setVisible(false);
            durationLabel.setVisible(false);
            buildUrl = null;
            openBrowserBtn.setEnabled(false);
            rerunBtn.setHighlighted(true);
            return;
        }

        this.buildUrl = run.getWebUrl();
        openBrowserBtn.setEnabled(buildUrl != null && !buildUrl.isEmpty());

        String idText = run.getName() != null && !run.getName().isEmpty()
                ? run.getName()
                : (run.getId() != null ? "#" + run.getId() : "Build");
        buildNumberLabel.setText(idText);
        buildNumberLabel.setVisible(true);

        statusBadge.setStatus(run.getStatus());
        statusBadge.setVisible(true);
        rerunBtn.setHighlighted(!run.getStatus().isRunning());
        if (run.getStatus().isRunning()) {
            setRunning(true);
        } else if (run.getStatus().isTerminal()) {
            setRunning(false);
        }

        if (run.getBranch() != null && !run.getBranch().isEmpty()) {
            branchLabel.setText(run.getBranch());
            branchLabel.setVisible(true);
        } else {
            branchLabel.setVisible(false);
        }

        String dur = run.getFormattedDuration();
        if (!dur.isEmpty()) {
            durationLabel.setText("\u23f1 " + dur);
            durationLabel.setVisible(true);
        } else {
            durationLabel.setVisible(false);
        }
    }

    /**
     * Unified toolbar action button sharing identical sizing, insets, and antialiased hover/pressed highlights.
     */
    public static class HeaderActionButton extends JButton {
        private boolean highlighted = false;

        public HeaderActionButton(@NotNull Icon icon, @NotNull String tooltip) {
            super(icon);
            setToolTipText(tooltip);
            setFocusable(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setMargin(new Insets(2, 4, 2, 4));
            setPreferredSize(new Dimension(28, 24));
            putClientProperty("JButton.buttonType", "toolBarButton");
        }

        public void setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
            repaint();
        }

        public boolean isHighlighted() {
            return highlighted;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isEnabled()) {
                if (getModel().isPressed()) {
                    Color pressedBg = new JBColor(new Color(220, 224, 230), new Color(68, 72, 78));
                    g2.setColor(pressedBg);
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                } else if (getModel().isRollover()) {
                    Color hoverBg = new JBColor(new Color(238, 240, 243), new Color(55, 58, 62));
                    g2.setColor(hoverBg);
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                } else if (highlighted) {
                    Color highlightBg = new JBColor(new Color(232, 245, 233), new Color(30, 52, 36));
                    Color highlightBorder = new JBColor(new Color(165, 214, 167), new Color(56, 125, 60));
                    g2.setColor(highlightBg);
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                    g2.setColor(highlightBorder);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class StatusBadge extends JComponent {
        private PipelineStatus status = PipelineStatus.NOT_STARTED;

        public StatusBadge() {
            setOpaque(false);
        }

        public void setStatus(@NotNull PipelineStatus status) {
            this.status = status;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int width = fm.stringWidth(status.getDisplayName()) + 20;
            return new Dimension(Math.max(70, width), 22);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg;
            Color fg;
            switch (status) {
                case SUCCESS:
                    bg = new JBColor(new Color(230, 246, 230), new Color(30, 56, 34));
                    fg = new JBColor(new Color(35, 125, 45), new Color(110, 205, 115));
                    break;
                case FAILED:
                    bg = new JBColor(new Color(253, 235, 235), new Color(60, 28, 28));
                    fg = new JBColor(new Color(210, 45, 45), new Color(245, 95, 95));
                    break;
                case ABORTED:
                    bg = new JBColor(new Color(245, 235, 235), new Color(55, 40, 40));
                    fg = new JBColor(new Color(180, 80, 80), new Color(220, 120, 120));
                    break;
                case IN_PROGRESS:
                    bg = new JBColor(new Color(230, 244, 255), new Color(25, 45, 68));
                    fg = new JBColor(new Color(15, 115, 205), new Color(90, 175, 255));
                    break;
                default:
                    bg = new JBColor(new Color(240, 242, 245), new Color(48, 50, 54));
                    fg = JBColor.GRAY;
                    break;
            }

            RoundRectangle2D.Double roundRect = new RoundRectangle2D.Double(0, 1, getWidth() - 1, getHeight() - 2, 8, 8);
            g2.setColor(bg);
            g2.fill(roundRect);

            g2.setColor(fg);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            int strWidth = fm.stringWidth(status.getDisplayName());
            int x = (getWidth() - strWidth) / 2;
            int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(status.getDisplayName(), x, y);

            g2.dispose();
        }
    }
}
