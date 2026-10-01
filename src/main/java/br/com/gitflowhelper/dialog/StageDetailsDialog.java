package br.com.gitflowhelper.dialog;

import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Modal dialog displaying detailed execution information for a pipeline stage
 * and all its steps in a collapsible/expandable accordion view.
 */
public class StageDetailsDialog extends DialogWrapper {

    private final PipelineStage stage;
    private final List<StepCollapsiblePanel> stepPanels = new ArrayList<>();
    private JPanel stepsContainer;

    public StageDetailsDialog(@Nullable Project project, @NotNull PipelineStage stage) {
        super(project, true);
        this.stage = stage;
        initDialog();
    }

    public StageDetailsDialog(@NotNull Component parent, @NotNull PipelineStage stage) {
        super(parent, true);
        this.stage = stage;
        initDialog();
    }

    private void initDialog() {
        setTitle("Stage Details \u2014 " + stage.getName());
        setResizable(true);
        init();
        setSize(640, 520);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(JBUI.Borders.empty(12, 14, 10, 14));
        root.setPreferredSize(new Dimension(620, 480));

        // 1. Top Header Summary
        root.add(createStageSummaryHeader(), BorderLayout.NORTH);

        // 2. Center ScrollPane with Collapsible Steps
        stepsContainer = new JPanel();
        stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
        stepsContainer.setOpaque(false);

        List<PipelineStep> steps = stage.getSteps();
        stepPanels.clear();

        if (steps.isEmpty()) {
            JPanel emptyPanel = new JPanel(new BorderLayout());
            emptyPanel.setBorder(JBUI.Borders.empty(40, 20));
            emptyPanel.setOpaque(false);
            JBLabel emptyLabel = new JBLabel("No individual steps recorded for this stage.", SwingConstants.CENTER);
            emptyLabel.setForeground(JBColor.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyPanel.add(emptyLabel, BorderLayout.CENTER);
            stepsContainer.add(emptyPanel);
        } else {
            for (int i = 0; i < steps.size(); i++) {
                PipelineStep step = steps.get(i);
                StepCollapsiblePanel panel = new StepCollapsiblePanel(step, i + 1, () -> {
                    stepsContainer.revalidate();
                    stepsContainer.repaint();
                });
                // By default all steps are collapsed
                panel.setExpanded(false);
                stepPanels.add(panel);
                stepsContainer.add(panel);
                if (i < steps.size() - 1) {
                    stepsContainer.add(Box.createVerticalStrut(8));
                }
            }
        }

        JBScrollPane scrollPane = new JBScrollPane(stepsContainer);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.namedColor("Component.borderColor", new JBColor(new Color(220, 224, 230), new Color(55, 58, 62))), 1, true),
                JBUI.Borders.empty(6)
        ));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        return root;
    }

    private JPanel createStageSummaryHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(6));

        // Left: Stage Icon, Name, Status, Duration
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        JLabel iconLabel = new JLabel(new StatusIcon(stage.getStatus(), 18));
        titleRow.add(iconLabel);

        JBLabel stageTitle = new JBLabel(stage.getName());
        stageTitle.setFont(stageTitle.getFont().deriveFont(Font.BOLD, 15f));
        titleRow.add(stageTitle);

        // Status Pill
        JLabel statusBadge = createStatusBadge(stage.getStatus());
        titleRow.add(statusBadge);

        leftPanel.add(titleRow);
        leftPanel.add(Box.createVerticalStrut(4));

        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        subRow.setOpaque(false);

        String dur = stage.getFormattedDuration();
        if (!dur.isEmpty()) {
            JBLabel durLabel = new JBLabel("Duration: " + dur);
            durLabel.setForeground(JBColor.GRAY);
            durLabel.setFont(durLabel.getFont().deriveFont(Font.PLAIN, 11f));
            subRow.add(durLabel);
        }

        int count = stage.getSteps().size();
        JBLabel countLabel = new JBLabel(count + (count == 1 ? " step" : " steps"));
        countLabel.setForeground(JBColor.GRAY);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 11f));
        subRow.add(countLabel);

        if (stage.getStartTimeMillis() > 0) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(stage.getStartTimeMillis()));
            JBLabel timeLabel = new JBLabel("Started: " + dateStr);
            timeLabel.setForeground(JBColor.GRAY);
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
            subRow.add(timeLabel);
        }

        leftPanel.add(subRow);
        header.add(leftPanel, BorderLayout.CENTER);

        // Right: "Expand all" and "Collapse all" buttons
        if (!stage.getSteps().isEmpty()) {
            JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actionsPanel.setOpaque(false);

            JButton expandAllBtn = new JButton("Expand all", AllIcons.Actions.Expandall);
            expandAllBtn.putClientProperty("JButton.buttonType", "toolBarButton");
            expandAllBtn.setToolTipText("Expand all step details");
            expandAllBtn.addActionListener(e -> setAllExpanded(true));
            actionsPanel.add(expandAllBtn);

            JButton collapseAllBtn = new JButton("Collapse all", AllIcons.Actions.Collapseall);
            collapseAllBtn.putClientProperty("JButton.buttonType", "toolBarButton");
            collapseAllBtn.setToolTipText("Collapse all step details");
            collapseAllBtn.addActionListener(e -> setAllExpanded(false));
            actionsPanel.add(collapseAllBtn);

            header.add(actionsPanel, BorderLayout.EAST);
        }

        return header;
    }

    public void setAllExpanded(boolean expanded) {
        for (StepCollapsiblePanel panel : stepPanels) {
            panel.setExpanded(expanded);
        }
        if (stepsContainer != null) {
            stepsContainer.revalidate();
            stepsContainer.repaint();
        }
    }

    public List<StepCollapsiblePanel> getStepPanels() {
        return stepPanels;
    }

    private JLabel createStatusBadge(PipelineStatus status) {
        JLabel badge = new JLabel(" " + status.getDisplayName().toUpperCase() + " ");
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        Color textColor;
        Color bgColor;
        Color borderColor;

        if (status == PipelineStatus.SUCCESS) {
            textColor = new JBColor(new Color(35, 120, 40), new Color(130, 200, 120));
            bgColor = new JBColor(new Color(237, 247, 237), new Color(33, 50, 37));
            borderColor = new JBColor(new Color(180, 225, 185), new Color(50, 85, 55));
        } else if (status == PipelineStatus.FAILED) {
            textColor = new JBColor(new Color(210, 40, 40), new Color(245, 90, 90));
            bgColor = new JBColor(new Color(253, 237, 237), new Color(60, 33, 33));
            borderColor = new JBColor(new Color(245, 185, 185), new Color(90, 50, 50));
        } else if (status == PipelineStatus.IN_PROGRESS) {
            textColor = new JBColor(new Color(15, 105, 180), new Color(100, 180, 255));
            bgColor = new JBColor(new Color(230, 244, 255), new Color(25, 45, 65));
            borderColor = new JBColor(new Color(175, 215, 255), new Color(40, 70, 100));
        } else {
            textColor = JBColor.GRAY;
            bgColor = new JBColor(new Color(245, 245, 245), new Color(45, 45, 48));
            borderColor = new JBColor(new Color(210, 210, 210), new Color(65, 65, 70));
        }

        badge.setForeground(textColor);
        badge.setOpaque(true);
        badge.setBackground(bgColor);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                JBUI.Borders.empty(1, 4)
        ));
        return badge;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    // -----------------------------------------------------------------------
    // Collapsible Step Panel Component
    // -----------------------------------------------------------------------

    public static class StepCollapsiblePanel extends JPanel {
        private final PipelineStep step;
        private final int index;
        private boolean expanded = false;
        private final JLabel chevronLabel;
        private final JPanel headerPanel;
        private final JPanel contentPanel;
        private final Runnable onToggle;

        public StepCollapsiblePanel(@NotNull PipelineStep step, int index, @Nullable Runnable onToggle) {
            super(new BorderLayout());
            this.step = step;
            this.index = index;
            this.onToggle = onToggle;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            Color borderColor = new JBColor(new Color(220, 224, 230), new Color(58, 61, 65));

            // 1. Clickable Header
            headerPanel = new JPanel(new BorderLayout(8, 0));
            headerPanel.setOpaque(true);
            headerPanel.setBackground(new JBColor(new Color(248, 249, 251), new Color(40, 42, 45)));
            headerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor, 1, true),
                    JBUI.Borders.empty(7, 10)
            ));
            headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Left part: Chevron + Status Icon + Step index/name
            JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            leftHeader.setOpaque(false);

            chevronLabel = new JLabel(AllIcons.General.ArrowRight);
            leftHeader.add(chevronLabel);

            JLabel statusIcon = new JLabel(new StatusIcon(step.getStatus(), 14));
            leftHeader.add(statusIcon);

            JBLabel nameLabel = new JBLabel(step.getName());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
            leftHeader.add(nameLabel);

            headerPanel.add(leftHeader, BorderLayout.WEST);

            // Right part: Duration + expansion hint
            JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightHeader.setOpaque(false);

            String dur = step.getFormattedDuration();
            if (!dur.isEmpty()) {
                JBLabel durLabel = new JBLabel(dur);
                durLabel.setForeground(JBColor.GRAY);
                durLabel.setFont(durLabel.getFont().deriveFont(Font.PLAIN, 11f));
                rightHeader.add(durLabel);
            }

            headerPanel.add(rightHeader, BorderLayout.EAST);

            // Click listener on header toggles expansion
            MouseAdapter toggleListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        setExpanded(!expanded);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    headerPanel.setBackground(new JBColor(new Color(238, 242, 248), new Color(48, 51, 55)));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    headerPanel.setBackground(new JBColor(new Color(248, 249, 251), new Color(40, 42, 45)));
                }
            };
            headerPanel.addMouseListener(toggleListener);
            for (Component c : headerPanel.getComponents()) {
                c.addMouseListener(toggleListener);
            }
            for (Component c : leftHeader.getComponents()) {
                c.addMouseListener(toggleListener);
            }
            for (Component c : rightHeader.getComponents()) {
                c.addMouseListener(toggleListener);
            }

            add(headerPanel, BorderLayout.NORTH);

            // 2. Expandable Content Panel
            contentPanel = createContentPanel(borderColor);
            contentPanel.setVisible(false);
            add(contentPanel, BorderLayout.CENTER);
        }

        private JPanel createContentPanel(Color borderColor) {
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setOpaque(true);
            content.setBackground(new JBColor(new Color(253, 254, 255), new Color(34, 36, 38)));
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 1, 1, borderColor),
                    JBUI.Borders.empty(10, 14, 10, 14)
            ));

            content.add(createDetailRow("Step ID:", step.getId()));
            content.add(Box.createVerticalStrut(4));
            content.add(createDetailRow("Status:", step.getStatus().getDisplayName()));
            content.add(Box.createVerticalStrut(4));

            String dur = step.getFormattedDuration();
            String durDetail = dur.isEmpty() ? "0 ms" : dur + " (" + step.getDurationMillis() + " ms)";
            content.add(createDetailRow("Duration:", durDetail));
            content.add(Box.createVerticalStrut(4));

            if (step.getStartTimeMillis() > 0) {
                String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(step.getStartTimeMillis()));
                content.add(createDetailRow("Start Time:", dateStr));
                content.add(Box.createVerticalStrut(6));
            }

            // Quick action: copy step summary
            JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            copyPanel.setOpaque(false);
            JButton copyBtn = new JButton("Copy info", AllIcons.Actions.Copy);
            copyBtn.putClientProperty("JButton.buttonType", "toolBarButton");
            copyBtn.setFont(copyBtn.getFont().deriveFont(Font.PLAIN, 10f));
            copyBtn.setToolTipText("Copy step details to clipboard");
            copyBtn.addActionListener(e -> {
                String text = "Step: " + step.getName() + "\n" +
                        "ID: " + step.getId() + "\n" +
                        "Status: " + step.getStatus().getDisplayName() + "\n" +
                        "Duration: " + step.getFormattedDuration() + " (" + step.getDurationMillis() + " ms)";
                CopyPasteManager.getInstance().setContents(new StringSelection(text));
            });
            copyPanel.add(copyBtn);
            content.add(copyPanel);

            return content;
        }

        private JPanel createDetailRow(String label, String value) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.setOpaque(false);

            JBLabel lbl = new JBLabel(label);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
            lbl.setForeground(JBColor.GRAY);
            row.add(lbl);

            JBLabel val = new JBLabel(value);
            val.setFont(val.getFont().deriveFont(Font.PLAIN, 11f));
            row.add(val);

            return row;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
            chevronLabel.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
            contentPanel.setVisible(expanded);
            if (onToggle != null) {
                onToggle.run();
            }
        }

        public boolean isExpanded() {
            return expanded;
        }

        public PipelineStep getStep() {
            return step;
        }
    }

    // -----------------------------------------------------------------------
    // Status Icon Helper
    // -----------------------------------------------------------------------

    public static class StatusIcon implements Icon {
        private final PipelineStatus status;
        private final int size;

        public StatusIcon(@NotNull PipelineStatus status, int size) {
            this.status = status;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (status == PipelineStatus.SUCCESS) {
                g2.setColor(new JBColor(new Color(46, 139, 87), new Color(98, 181, 67)));
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 3, y + (size / 2), x + (size / 2) - 1, y + size - 4);
                g2.drawLine(x + (size / 2) - 1, y + size - 4, x + size - 3, y + 4);

            } else if (status == PipelineStatus.FAILED) {
                g2.setColor(new JBColor(new Color(210, 60, 60), new Color(230, 80, 80)));
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = 4;
                g2.drawLine(x + pad, y + pad, x + size - pad, y + size - pad);
                g2.drawLine(x + size - pad, y + pad, x + pad, y + size - pad);

            } else if (status == PipelineStatus.IN_PROGRESS) {
                g2.setColor(new JBColor(new Color(33, 150, 243), new Color(64, 169, 255)));
                g2.fillOval(x, y, size, size);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 2, y + 2, size - 4, size - 4);

            } else if (status == PipelineStatus.PAUSED || status == PipelineStatus.SKIPPED) {
                g2.setColor(new JBColor(new Color(230, 160, 30), new Color(240, 180, 50)));
                g2.fillOval(x, y, size, size);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + 4, y + (size / 2), x + size - 4, y + (size / 2));

            } else {
                g2.setColor(new JBColor(new Color(180, 185, 190), new Color(90, 93, 98)));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 2, y + 2, size - 4, size - 4);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
