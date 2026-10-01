package br.com.gitflowhelper.toolwindow.ci;

import br.com.gitflow.cicd.model.PipelineRun;
import br.com.gitflow.cicd.model.PipelineStage;
import br.com.gitflow.cicd.model.PipelineStatus;
import br.com.gitflow.cicd.model.PipelineStep;
import br.com.gitflowhelper.dialog.StageDetailsDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Custom 2D canvas displaying CI/CD Pipeline stages and steps as a connected DAG.
 * Supports clicking finished stages to inspect step details in a modal dialog.
 */
public class PipelineDagCanvas extends JPanel implements ComponentWithEmptyText, Disposable {

    private static final int STAGE_WIDTH = 210;
    private static final int STAGE_GAP = 46;
    private static final int HEADER_HEIGHT = 38;
    private static final int STEP_ROW_HEIGHT = 30;
    private static final int CORNER_RADIUS = 12;
    private static final int START_X = 30;
    private static final int START_Y = 24;

    private Project project;
    private PipelineRun pipelineRun;
    private int animationAngle = 0;
    private final Timer animationTimer;

    // Loading / initializing state (shows animated spinner before first stage arrives)
    private boolean loading = false;

    // Split mode state (auto-scroll is active only when in split mode and pipeline is running)
    private boolean splitMode = false;

    // Smooth scroll animation
    private Timer smoothScrollTimer;
    private int scrollStartX;
    private int scrollTargetX;
    private int scrollCurrentStep;
    private int scrollTotalSteps;

    // Hover tracking for clickable finished stages
    private int hoveredStageIndex = -1;

    private final StatusText emptyText = new StatusText(this) {
        @Override
        protected boolean isStatusVisible() {
            return !loading && (pipelineRun == null || pipelineRun.getStages().isEmpty());
        }
    };

    public PipelineDagCanvas() {
        this(null);
    }

    public PipelineDagCanvas(@Nullable Project project) {
        this.project = project;
        setBackground(new JBColor(new Color(248, 249, 250), new Color(30, 31, 34)));
        setOpaque(true);

        emptyText.setText("No CI/CD pipeline running");
        emptyText.appendSecondaryText("Run a build or finish a feature branch to monitor pipeline execution.", SimpleTextAttributes.GRAYED_ATTRIBUTES, null);

        animationTimer = new Timer(70, e -> {
            animationAngle = (animationAngle + 20) % 360;
            if (loading || hasRunningEntities()) {
                repaint();
            }
        });

        // Mouse listeners for hover and click interaction on finished stages
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateStageHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredStageIndex != -1) {
                    hoveredStageIndex = -1;
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int idx = getStageIndexAt(e.getPoint());
                    if (idx >= 0 && pipelineRun != null && idx < pipelineRun.getStages().size()) {
                        PipelineStage stage = pipelineRun.getStages().get(idx);
                        if (isStageFinished(stage)) {
                            openStageDetailsDialog(stage);
                        }
                    }
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setProject(@Nullable Project project) {
        this.project = project;
    }

    public @Nullable Project getProject() {
        return project;
    }

    public static boolean isStageFinished(@Nullable PipelineStage stage) {
        if (stage == null) return false;
        PipelineStatus status = stage.getStatus();
        return status == PipelineStatus.SUCCESS
                || status == PipelineStatus.FAILED
                || status == PipelineStatus.ABORTED
                || status == PipelineStatus.SKIPPED
                || status == PipelineStatus.PAUSED;
    }

    public Rectangle getStageBounds(int index, @NotNull PipelineStage stage) {
        int x = START_X + (index * (STAGE_WIDTH + STAGE_GAP));
        int y = START_Y;
        int stepCount = Math.max(1, stage.getSteps().size());
        int cardHeight = HEADER_HEIGHT + (stepCount * STEP_ROW_HEIGHT) + 10;
        return new Rectangle(x, y, STAGE_WIDTH, cardHeight);
    }

    public int getStageIndexAt(@NotNull Point p) {
        if (pipelineRun == null || pipelineRun.getStages().isEmpty() || loading) {
            return -1;
        }
        List<PipelineStage> stages = pipelineRun.getStages();
        for (int i = 0; i < stages.size(); i++) {
            if (getStageBounds(i, stages.get(i)).contains(p)) {
                return i;
            }
        }
        return -1;
    }

    public int getHoveredStageIndex() {
        return hoveredStageIndex;
    }

    public void openStageDetailsDialog(@NotNull PipelineStage stage) {
        StageDetailsDialog dialog = project != null
                ? new StageDetailsDialog(project, stage)
                : new StageDetailsDialog(this, stage);
        dialog.show();
    }

    public void updateStageHover(Point p) {
        int idx = getStageIndexAt(p);
        if (idx >= 0 && pipelineRun != null && idx < pipelineRun.getStages().size()) {
            PipelineStage stage = pipelineRun.getStages().get(idx);
            if (isStageFinished(stage)) {
                if (hoveredStageIndex != idx) {
                    hoveredStageIndex = idx;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Click to view step details for stage \"" + stage.getName() + "\"");
                    repaint();
                }
                return;
            }
        }
        if (hoveredStageIndex != -1) {
            hoveredStageIndex = -1;
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(null);
            repaint();
        }
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        if (loading) {
            this.pipelineRun = null;
            this.hoveredStageIndex = -1;
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(null);
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        } else {
            if (!hasRunningEntities() && animationTimer.isRunning()) {
                animationTimer.stop();
            }
        }
        recomputeCanvasSize();
        repaint();
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isPipelineExecuting() {
        return pipelineRun != null && pipelineRun.getStatus().isRunning();
    }

    public boolean hasRunningEntities() {
        if (!isPipelineExecuting()) return false;
        for (PipelineStage stage : pipelineRun.getStages()) {
            if (stage.getStatus().isRunning()) return true;
            for (PipelineStep step : stage.getSteps()) {
                if (step.getStatus().isRunning()) return true;
            }
        }
        return true;
    }

    public void setSplitMode(boolean splitMode) {
        this.splitMode = splitMode;
        if (splitMode && isPipelineExecuting() && !loading) {
            SwingUtilities.invokeLater(this::scrollToActiveStage);
        }
    }

    public boolean isSplitMode() {
        return splitMode;
    }

    public void updatePipelineRun(@Nullable PipelineRun run) {
        if (run != null && !run.getStages().isEmpty()) {
            this.loading = false;
            this.pipelineRun = run;
        } else if (!loading) {
            this.pipelineRun = run;
        }

        if (loading || hasRunningEntities()) {
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        } else {
            if (animationTimer.isRunning()) {
                animationTimer.stop();
            }
            if (smoothScrollTimer != null && smoothScrollTimer.isRunning()) {
                smoothScrollTimer.stop();
            }
        }

        recomputeCanvasSize();
        repaint();

        if (splitMode && isPipelineExecuting() && !loading) {
            SwingUtilities.invokeLater(this::scrollToActiveStage);
        }
    }

    /**
     * Automatically and smoothly scrolls the viewport horizontally to follow pipeline progression.
     * Triggered exclusively when the dashboard is in split mode and the pipeline is actively running.
     */
    public void scrollToActiveStage() {
        if (!splitMode || loading || !isPipelineExecuting() || pipelineRun == null || pipelineRun.getStages().isEmpty()) {
            return;
        }

        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport == null || viewport.getWidth() <= 0) {
            return;
        }

        int activeIdx = getActiveStageIndex();
        if (activeIdx < 0) {
            return;
        }

        int stageX = START_X + (activeIdx * (STAGE_WIDTH + STAGE_GAP));
        int viewportWidth = viewport.getWidth();
        Point currentPos = viewport.getViewPosition();
        int currentX = currentPos.x;

        int targetX;
        // If the active card is to the right of the visible viewport, scroll so card and margin are visible
        if (stageX + STAGE_WIDTH + 30 > currentX + viewportWidth) {
            targetX = stageX + STAGE_WIDTH + 30 - viewportWidth;
        } else if (stageX - 20 < currentX) {
            // If the active card is to the left of the visible viewport
            targetX = Math.max(0, stageX - 20);
        } else {
            // Already visible in viewport
            return;
        }

        int contentWidth = Math.max(getWidth(), getPreferredSize().width);
        int maxScroll = Math.max(0, contentWidth - viewportWidth);
        targetX = Math.max(0, Math.min(targetX, maxScroll));

        if (targetX == currentX) {
            return;
        }

        startSmoothScroll(viewport, currentX, targetX, contentWidth);
    }

    private void startSmoothScroll(JViewport viewport, int fromX, int toX, int contentWidth) {
        if (smoothScrollTimer != null && smoothScrollTimer.isRunning()) {
            smoothScrollTimer.stop();
        }

        if (viewport.getViewSize().width < contentWidth) {
            viewport.setViewSize(new Dimension(contentWidth, Math.max(viewport.getHeight(), getPreferredSize().height)));
        }

        // Smooth scroll over ~240ms (16 steps * 15ms) using cubic ease-out
        scrollTotalSteps = 16;
        scrollCurrentStep = 0;
        scrollStartX = fromX;
        scrollTargetX = toX;

        smoothScrollTimer = new Timer(15, e -> {
            scrollCurrentStep++;
            if (scrollCurrentStep >= scrollTotalSteps) {
                viewport.setViewPosition(new Point(scrollTargetX, viewport.getViewPosition().y));
                smoothScrollTimer.stop();
            } else {
                double t = (double) scrollCurrentStep / scrollTotalSteps;
                double easeOut = 1.0 - Math.pow(1.0 - t, 3);
                int nextX = (int) Math.round(scrollStartX + (scrollTargetX - scrollStartX) * easeOut);
                viewport.setViewPosition(new Point(nextX, viewport.getViewPosition().y));
            }
        });
        smoothScrollTimer.start();
    }

    public boolean isSmoothScrolling() {
        return smoothScrollTimer != null && smoothScrollTimer.isRunning();
    }

    public int getScrollTargetX() {
        return scrollTargetX;
    }

    public int getActiveStageIndex() {
        if (pipelineRun == null || pipelineRun.getStages().isEmpty()) {
            return -1;
        }
        List<PipelineStage> stages = pipelineRun.getStages();
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getStatus().isRunning()) return i;
        }
        for (int i = stages.size() - 1; i >= 0; i--) {
            if (stages.get(i).getStatus() != PipelineStatus.NOT_STARTED) return i;
        }
        return 0;
    }

    private void recomputeCanvasSize() {
        if (pipelineRun == null || pipelineRun.getStages().isEmpty()) {
            setPreferredSize(new Dimension(500, 300));
            revalidate();
            return;
        }

        List<PipelineStage> stages = pipelineRun.getStages();
        int totalWidth = START_X + (stages.size() * (STAGE_WIDTH + STAGE_GAP)) + 60;

        int maxStageHeight = 0;
        for (PipelineStage stage : stages) {
            int stepCount = Math.max(1, stage.getSteps().size());
            int h = HEADER_HEIGHT + (stepCount * STEP_ROW_HEIGHT) + 12;
            if (h > maxStageHeight) {
                maxStageHeight = h;
            }
        }

        int totalHeight = Math.max(300, START_Y + maxStageHeight + 60);
        setPreferredSize(new Dimension(Math.max(500, totalWidth), totalHeight));
        revalidate();
    }

    @Override
    public @NotNull StatusText getEmptyText() {
        return emptyText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (loading) {
            drawLoadingSpinner((Graphics2D) g);
            return;
        }

        if (pipelineRun == null || pipelineRun.getStages().isEmpty()) {
            emptyText.paint(this, g);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        List<PipelineStage> stages = pipelineRun.getStages();

        // 1. Draw connecting arrows between stages first (so boxes appear on top)
        for (int i = 0; i < stages.size() - 1; i++) {
            PipelineStage current = stages.get(i);
            PipelineStage next = stages.get(i + 1);

            int x1 = START_X + (i * (STAGE_WIDTH + STAGE_GAP)) + STAGE_WIDTH;
            int x2 = START_X + ((i + 1) * (STAGE_WIDTH + STAGE_GAP));
            int y = START_Y + (HEADER_HEIGHT / 2);

            drawStageConnector(g2, current, next, x1, y, x2, y);
        }

        // 2. Draw each stage card and its steps
        for (int i = 0; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);
            int x = START_X + (i * (STAGE_WIDTH + STAGE_GAP));
            drawStageCard(g2, stage, x, START_Y, i);
        }

        g2.dispose();
    }

    private void drawLoadingSpinner(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth() > 0 ? getWidth() : 500;
        int height = getHeight() > 0 ? getHeight() : 300;
        int cx = width / 2;
        int cy = Math.max(70, height / 2 - 25);

        int spinnerSize = 44;
        int sx = cx - (spinnerSize / 2);
        int sy = cy - (spinnerSize / 2);

        // Background track ring
        g2.setColor(new JBColor(new Color(220, 226, 235), new Color(50, 54, 60)));
        g2.setStroke(new BasicStroke(3.5f));
        g2.drawOval(sx, sy, spinnerSize, spinnerSize);

        // Animated rotating spinner arc
        g2.setColor(new JBColor(new Color(33, 150, 243), new Color(64, 169, 255)));
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(sx, sy, spinnerSize, spinnerSize, animationAngle, 110);

        // Title and description
        g2.setColor(JBColor.foreground());
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
        String title = "Starting pipeline...";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, cx - (fm.stringWidth(title) / 2), cy + (spinnerSize / 2) + 30);

        g2.setColor(JBColor.GRAY);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        String desc = "Waiting for the first stage to initialize...";
        FontMetrics fmDesc = g2.getFontMetrics();
        g2.drawString(desc, cx - (fmDesc.stringWidth(desc) / 2), cy + (spinnerSize / 2) + 50);

        g2.dispose();
    }

    private void drawStageConnector(Graphics2D g2, PipelineStage fromStage, PipelineStage toStage, int x1, int y1, int x2, int y2) {
        Color lineColor;
        if (fromStage.getStatus() == PipelineStatus.SUCCESS) {
            lineColor = new JBColor(new Color(46, 139, 87), new Color(98, 181, 67));
        } else if (fromStage.getStatus() == PipelineStatus.FAILED) {
            lineColor = new JBColor(new Color(210, 60, 60), new Color(230, 80, 80));
        } else if (fromStage.getStatus() == PipelineStatus.IN_PROGRESS) {
            lineColor = new JBColor(new Color(33, 150, 243), new Color(41, 140, 230));
        } else {
            lineColor = new JBColor(new Color(190, 195, 200), new Color(75, 78, 82));
        }

        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x1, y1, x2 - 8, y2);

        // Arrow head pointing to x2
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x2 - 8, y2 - 5);
        arrow.lineTo(x2, y2);
        arrow.lineTo(x2 - 8, y2 + 5);
        arrow.closePath();
        g2.fill(arrow);
    }

    private void drawStageCard(Graphics2D g2, PipelineStage stage, int x, int y, int index) {
        List<PipelineStep> steps = stage.getSteps();
        int stepCount = Math.max(1, steps.size());
        int cardHeight = HEADER_HEIGHT + (stepCount * STEP_ROW_HEIGHT) + 10;

        boolean finished = isStageFinished(stage);
        boolean isHovered = (hoveredStageIndex == index && finished);

        // Card outer border and background
        RoundRectangle2D.Double cardShape = new RoundRectangle2D.Double(x, y, STAGE_WIDTH, cardHeight, CORNER_RADIUS, CORNER_RADIUS);

        Color cardBg = new JBColor(new Color(255, 255, 255), new Color(43, 45, 48));
        g2.setColor(cardBg);
        g2.fill(cardShape);

        // Border highlighting based on stage status and hover
        Color borderColor;
        float strokeWidth;
        if (isHovered) {
            borderColor = new JBColor(new Color(25, 118, 210), new Color(100, 181, 246));
            strokeWidth = 2.4f;
        } else if (stage.getStatus() == PipelineStatus.IN_PROGRESS) {
            borderColor = new JBColor(new Color(33, 150, 243), new Color(41, 140, 230));
            strokeWidth = 2.0f;
        } else if (stage.getStatus() == PipelineStatus.SUCCESS) {
            borderColor = new JBColor(new Color(76, 175, 80), new Color(60, 160, 70));
            strokeWidth = 1.5f;
        } else if (stage.getStatus() == PipelineStatus.FAILED) {
            borderColor = new JBColor(new Color(244, 67, 54), new Color(220, 60, 50));
            strokeWidth = 2.0f;
        } else {
            borderColor = new JBColor(new Color(218, 220, 224), new Color(65, 68, 72));
            strokeWidth = 1.0f;
        }

        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.draw(cardShape);

        // Header Background
        Shape oldClip = g2.getClip();
        g2.clip(cardShape);

        Color headerBg;
        if (isHovered) {
            headerBg = new JBColor(new Color(238, 244, 252), new Color(48, 56, 68));
        } else {
            headerBg = new JBColor(new Color(245, 247, 250), new Color(50, 53, 56));
        }
        g2.setColor(headerBg);
        g2.fillRect(x, y, STAGE_WIDTH, HEADER_HEIGHT);

        // Header bottom divider
        g2.setColor(new JBColor(new Color(225, 228, 232), new Color(60, 63, 67)));
        g2.drawLine(x, y + HEADER_HEIGHT, x + STAGE_WIDTH, y + HEADER_HEIGHT);
        g2.setClip(oldClip);

        // Stage Header Icon & Text
        drawStatusIndicator(g2, stage.getStatus(), x + 10, y + 11, 16);

        g2.setColor(isHovered ? new JBColor(new Color(25, 118, 210), new Color(100, 181, 246)) : JBColor.foreground());
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));

        String stageName = stage.getName();
        FontMetrics fm = g2.getFontMetrics();
        String durationText = stage.getFormattedDuration();

        // Right side: duration and clickable indicator if finished
        int rightMargin = 10;
        if (finished) {
            // Draw small chevron or magnifying cue indicating clickable
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
            g2.setColor(isHovered ? new JBColor(new Color(25, 118, 210), new Color(100, 181, 246)) : JBColor.GRAY);
            g2.drawString("›", x + STAGE_WIDTH - 12, y + 23);
            rightMargin = 18;
        }

        if (!durationText.isEmpty()) {
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(isHovered ? new JBColor(new Color(25, 118, 210), new Color(100, 181, 246)) : JBColor.GRAY);
            int durWidth = g2.getFontMetrics().stringWidth(durationText);
            g2.drawString(durationText, x + STAGE_WIDTH - durWidth - rightMargin, y + 23);
            rightMargin += durWidth + 6;
        }

        int maxNameWidth = STAGE_WIDTH - 38 - rightMargin;
        String truncatedName = truncateText(stageName, fm, maxNameWidth);

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(isHovered ? new JBColor(new Color(25, 118, 210), new Color(100, 181, 246)) : JBColor.foreground());
        g2.drawString(truncatedName, x + 32, y + 24);

        // Steps Stack ("um em cima do outro")
        int stepStartY = y + HEADER_HEIGHT + 6;

        for (int j = 0; j < steps.size(); j++) {
            PipelineStep step = steps.get(j);
            int stepY = stepStartY + (j * STEP_ROW_HEIGHT);
            drawStepRow(g2, step, x + 6, stepY, STAGE_WIDTH - 12);
        }
    }

    private void drawStepRow(Graphics2D g2, PipelineStep step, int rx, int ry, int rw) {
        int rh = STEP_ROW_HEIGHT - 4;

        // Step background tint
        Color stepBg;
        if (step.getStatus() == PipelineStatus.SUCCESS) {
            stepBg = new JBColor(new Color(237, 247, 237), new Color(33, 50, 37));
        } else if (step.getStatus() == PipelineStatus.FAILED) {
            stepBg = new JBColor(new Color(253, 237, 237), new Color(60, 33, 33));
        } else if (step.getStatus() == PipelineStatus.IN_PROGRESS) {
            stepBg = new JBColor(new Color(230, 244, 255), new Color(25, 45, 65));
        } else {
            stepBg = new JBColor(new Color(248, 249, 250, 180), new Color(36, 38, 40, 180));
        }

        RoundRectangle2D.Double rowRect = new RoundRectangle2D.Double(rx, ry, rw, rh, 6, 6);
        g2.setColor(stepBg);
        g2.fill(rowRect);

        // Step Status Icon
        drawStatusIndicator(g2, step.getStatus(), rx + 6, ry + 6, 14);

        // Step Name
        g2.setFont(g2.getFont().deriveFont(step.getStatus() == PipelineStatus.IN_PROGRESS ? Font.BOLD : Font.PLAIN, 11f));

        if (step.getStatus() == PipelineStatus.FAILED) {
            g2.setColor(new JBColor(new Color(210, 40, 40), new Color(245, 90, 90)));
        } else if (step.getStatus() == PipelineStatus.SUCCESS) {
            g2.setColor(new JBColor(new Color(35, 120, 40), new Color(130, 200, 120)));
        } else if (step.getStatus() == PipelineStatus.IN_PROGRESS) {
            g2.setColor(new JBColor(new Color(15, 105, 180), new Color(100, 180, 255)));
        } else {
            g2.setColor(JBColor.GRAY);
        }

        FontMetrics fm = g2.getFontMetrics();
        String durationText = step.getFormattedDuration();
        int maxTextWidth = rw - 30 - (durationText.isEmpty() ? 0 : fm.stringWidth(durationText) + 8);
        String truncated = truncateText(step.getName(), fm, maxTextWidth);

        g2.drawString(truncated, rx + 26, ry + 18);

        // Step Duration on the right
        if (!durationText.isEmpty()) {
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(JBColor.GRAY);
            int dw = g2.getFontMetrics().stringWidth(durationText);
            g2.drawString(durationText, rx + rw - dw - 6, ry + 17);
        }
    }

    private void drawStatusIndicator(Graphics2D g2, PipelineStatus status, int ix, int iy, int size) {
        if (status == PipelineStatus.SUCCESS) {
            // Green circle with checkmark
            g2.setColor(new JBColor(new Color(46, 139, 87), new Color(98, 181, 67)));
            g2.fillOval(ix, iy, size, size);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ix + 3, iy + (size / 2), ix + (size / 2) - 1, iy + size - 4);
            g2.drawLine(ix + (size / 2) - 1, iy + size - 4, ix + size - 3, iy + 4);

        } else if (status == PipelineStatus.FAILED) {
            // Red circle with X
            g2.setColor(new JBColor(new Color(210, 60, 60), new Color(230, 80, 80)));
            g2.fillOval(ix, iy, size, size);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = 4;
            g2.drawLine(ix + pad, iy + pad, ix + size - pad, iy + size - pad);
            g2.drawLine(ix + size - pad, iy + pad, ix + pad, iy + size - pad);

        } else if (status == PipelineStatus.IN_PROGRESS) {
            // Animated rotating blue spinner
            g2.setColor(new JBColor(new Color(200, 225, 255), new Color(50, 75, 110)));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawOval(ix, iy, size, size);

            g2.setColor(new JBColor(new Color(33, 150, 243), new Color(64, 169, 255)));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(ix, iy, size, size, animationAngle, 100);

        } else if (status == PipelineStatus.PAUSED || status == PipelineStatus.SKIPPED) {
            // Yellow/orange pause or dash
            g2.setColor(new JBColor(new Color(230, 160, 30), new Color(240, 180, 50)));
            g2.fillOval(ix, iy, size, size);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(ix + 4, iy + (size / 2), ix + size - 4, iy + (size / 2));

        } else {
            // Muted hollow gray dot
            g2.setColor(new JBColor(new Color(180, 185, 190), new Color(90, 93, 98)));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(ix + 2, iy + 2, size - 4, size - 4);
        }
    }

    private String truncateText(String text, FontMetrics fm, int maxWidth) {
        if (maxWidth <= 20) return "...";
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb.toString() + ellipsis;
    }

    @Override
    public void dispose() {
        if (animationTimer.isRunning()) {
            animationTimer.stop();
        }
        if (smoothScrollTimer != null && smoothScrollTimer.isRunning()) {
            smoothScrollTimer.stop();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        dispose();
    }
}
