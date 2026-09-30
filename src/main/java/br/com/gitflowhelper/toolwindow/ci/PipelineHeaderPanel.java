package br.com.gitflowhelper.toolwindow.ci;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStatus;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
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
    private final JButton toggleSplitBtn = new JButton(AllIcons.Actions.PreviewDetails);

    private String buildUrl = null;
    private Runnable onRerunTrigger;
    private Runnable onStopMonitoring;
    private Runnable onToggleSplit;
    private boolean isSplit = false;

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
        infoPanel.add(buildNumberLabel);

        infoPanel.add(statusBadge);

        branchLabel.setIcon(AllIcons.Vcs.Branch);
        branchLabel.setFont(branchLabel.getFont().deriveFont(Font.PLAIN, 12f));
        branchLabel.setVisible(false);
        infoPanel.add(branchLabel);

        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.PLAIN, 12f));
        durationLabel.setForeground(JBColor.GRAY);
        durationLabel.setVisible(false);
        infoPanel.add(durationLabel);

        add(infoPanel, BorderLayout.WEST);

        // Right section: Quick action buttons
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionsPanel.setOpaque(false);

        toggleSplitBtn.setToolTipText("Toggle single panel / split console view");
        toggleSplitBtn.setFocusable(false);
        toggleSplitBtn.addActionListener(e -> {
            if (onToggleSplit != null) {
                onToggleSplit.run();
            }
        });
        actionsPanel.add(toggleSplitBtn);

        JButton openBrowserBtn = new JButton("Open in Browser", AllIcons.Ide.External_link_arrow);
        openBrowserBtn.setToolTipText("Open pipeline URL in default browser");
        openBrowserBtn.setFocusable(false);
        openBrowserBtn.addActionListener(e -> {
            if (buildUrl != null && !buildUrl.isEmpty()) {
                BrowserUtil.browse(buildUrl);
            }
        });
        actionsPanel.add(openBrowserBtn);

        JButton rerunBtn = new JButton(AllIcons.Actions.Execute);
        rerunBtn.setToolTipText("Trigger build again");
        rerunBtn.setFocusable(false);
        rerunBtn.addActionListener(e -> {
            if (onRerunTrigger != null) {
                onRerunTrigger.run();
            }
        });
        actionsPanel.add(rerunBtn);

        JButton stopBtn = new JButton(AllIcons.Actions.Suspend);
        stopBtn.setToolTipText("Stop CI monitoring");
        stopBtn.setFocusable(false);
        stopBtn.addActionListener(e -> {
            if (onStopMonitoring != null) {
                onStopMonitoring.run();
            }
        });
        actionsPanel.add(stopBtn);

        add(actionsPanel, BorderLayout.EAST);
    }

    public void setPlatformName(@NotNull String platformName) {
        platformLabel.setText(platformName);
    }

    public void setSplitMode(boolean split) {
        this.isSplit = split;
        toggleSplitBtn.setToolTipText(split ? "Switch to single panel (DAG only)" : "Switch to two panels (DAG + Console)");
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop) {
        setCallbacks(onRerun, onStop, null);
    }

    public void setCallbacks(@Nullable Runnable onRerun, @Nullable Runnable onStop, @Nullable Runnable onToggleSplit) {
        this.onRerunTrigger = onRerun;
        this.onStopMonitoring = onStop;
        this.onToggleSplit = onToggleSplit;
    }

    public void updatePipelineRun(@Nullable PipelineRun run, @Nullable String platformName) {
        if (platformName != null) {
            platformLabel.setText(platformName);
        }

        if (run == null) {
            buildNumberLabel.setText("Waiting for build...");
            statusBadge.setStatus(PipelineStatus.NOT_STARTED);
            branchLabel.setVisible(false);
            durationLabel.setVisible(false);
            buildUrl = null;
            return;
        }

        this.buildUrl = run.getWebUrl();

        String idText = run.getName() != null && !run.getName().isEmpty()
                ? run.getName()
                : (run.getId() != null ? "#" + run.getId() : "Build");
        buildNumberLabel.setText(idText);

        statusBadge.setStatus(run.getStatus());

        if (run.getBranch() != null && !run.getBranch().isEmpty()) {
            branchLabel.setText(run.getBranch());
            branchLabel.setVisible(true);
        } else {
            branchLabel.setVisible(false);
        }

        String dur = run.getFormattedDuration();
        if (!dur.isEmpty()) {
            durationLabel.setText("⏱ " + dur);
            durationLabel.setVisible(true);
        } else {
            durationLabel.setVisible(false);
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
