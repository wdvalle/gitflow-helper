package br.com.gitflow.cicd.model;

import br.com.gitflowhelper.dialog.StageDetailsDialog;
import com.intellij.openapi.ui.DialogWrapper;
import java.util.List;
import br.com.gitflowhelper.toolwindow.ci.PipelineDagCanvas;
import br.com.gitflowhelper.toolwindow.ci.PipelineHeaderPanel;
import br.com.gitflowhelper.toolwindow.ci.RepoCiDashboardPanel;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineModelsAndCanvasTest {

    @Test
    public void testPipelineStatusMappings() {
        // Jenkins mappings
        assertEquals(PipelineStatus.SUCCESS, PipelineStatus.fromJenkinsStatus("SUCCESS"));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromJenkinsStatus("IN_PROGRESS"));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromJenkinsStatus("BUILDING"));
        assertEquals(PipelineStatus.FAILED, PipelineStatus.fromJenkinsStatus("FAILURE"));
        assertEquals(PipelineStatus.FAILED, PipelineStatus.fromJenkinsStatus("FAILED"));
        assertEquals(PipelineStatus.ABORTED, PipelineStatus.fromJenkinsStatus("ABORTED"));
        assertEquals(PipelineStatus.SKIPPED, PipelineStatus.fromJenkinsStatus("NOT_BUILT"));
        assertEquals(PipelineStatus.PAUSED, PipelineStatus.fromJenkinsStatus("PAUSED_PENDING_INPUT"));
        assertEquals(PipelineStatus.UNKNOWN, PipelineStatus.fromJenkinsStatus("SOMETHING_ELSE"));

        // GitLab mappings
        assertEquals(PipelineStatus.SUCCESS, PipelineStatus.fromGitLabStatus("success"));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromGitLabStatus("running"));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromGitLabStatus("pending"));
        assertEquals(PipelineStatus.FAILED, PipelineStatus.fromGitLabStatus("failed"));
        assertEquals(PipelineStatus.ABORTED, PipelineStatus.fromGitLabStatus("canceled"));
        assertEquals(PipelineStatus.SKIPPED, PipelineStatus.fromGitLabStatus("skipped"));
        assertEquals(PipelineStatus.PAUSED, PipelineStatus.fromGitLabStatus("manual"));

        // GitHub mappings
        assertEquals(PipelineStatus.SUCCESS, PipelineStatus.fromGitHubStatus("completed", "success"));
        assertEquals(PipelineStatus.FAILED, PipelineStatus.fromGitHubStatus("completed", "failure"));
        assertEquals(PipelineStatus.ABORTED, PipelineStatus.fromGitHubStatus("completed", "cancelled"));
        assertEquals(PipelineStatus.SKIPPED, PipelineStatus.fromGitHubStatus("completed", "skipped"));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromGitHubStatus("in_progress", null));
        assertEquals(PipelineStatus.IN_PROGRESS, PipelineStatus.fromGitHubStatus("queued", null));

        // Predicates & Colors
        assertTrue(PipelineStatus.IN_PROGRESS.isRunning());
        assertFalse(PipelineStatus.SUCCESS.isRunning());
        assertTrue(PipelineStatus.SUCCESS.isTerminal());
        assertTrue(PipelineStatus.FAILED.isTerminal());
        assertNotNull(PipelineStatus.SUCCESS.getColor());
        assertNotNull(PipelineStatus.FAILED.getColor());
        assertNotNull(PipelineStatus.IN_PROGRESS.getColor());
    }

    @Test
    public void testPipelineModelsHierarchy() {
        PipelineStep step1 = new PipelineStep("1", "Checkout git", PipelineStatus.SUCCESS, 3000);
        assertEquals("3s", step1.getFormattedDuration());

        PipelineStep step2 = new PipelineStep("2", "Run tests", PipelineStatus.IN_PROGRESS, 65000);
        assertEquals("1m 05s", step2.getFormattedDuration());

        PipelineStage stage1 = new PipelineStage("s1", "Build & Test", PipelineStatus.IN_PROGRESS, 68000);
        stage1.addStep(step1);
        stage1.addStep(step2);

        assertEquals(2, stage1.getSteps().size());
        assertEquals("1m 08s", stage1.getFormattedDuration());

        PipelineRun run = new PipelineRun("101", "Build #101", PipelineStatus.IN_PROGRESS);
        run.setBranch("feature/user-login");
        run.setDurationMillis(70000);
        run.setWebUrl("http://ci.mycompany.com/job/test/101");
        run.addStage(stage1);

        assertEquals("101", run.getId());
        assertEquals("Build #101", run.getName());
        assertEquals("feature/user-login", run.getBranch());
        assertEquals(PipelineStatus.IN_PROGRESS, run.getStatus());
        assertEquals(1, run.getStages().size());
        assertEquals("1m 10s", run.getFormattedDuration());
    }

    @Test
    public void testPipelineDagCanvasPaintingAndAutoScroll() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            // 1. Paint empty canvas
            BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.setSize(800, 400);
            canvas.paint(g2);

            assertEquals(-1, canvas.getActiveStageIndex());

            // 2. Populate with stages: Stage 1 (Success) -> Stage 2 (In Progress) -> Stage 3 (Not started)
            PipelineRun run = new PipelineRun("50", "#50", PipelineStatus.IN_PROGRESS);

            PipelineStage stage1 = new PipelineStage("s1", "Checkout", PipelineStatus.SUCCESS, 4000);
            stage1.addStep(new PipelineStep("st1", "git clone", PipelineStatus.SUCCESS, 2000));
            stage1.addStep(new PipelineStep("st2", "git checkout develop", PipelineStatus.SUCCESS, 2000));
            run.addStage(stage1);

            PipelineStage stage2 = new PipelineStage("s2", "Build & Test", PipelineStatus.IN_PROGRESS, 22000);
            stage2.addStep(new PipelineStep("st3", "compile Java", PipelineStatus.SUCCESS, 12000));
            stage2.addStep(new PipelineStep("st4", "run unit tests", PipelineStatus.IN_PROGRESS, 10000));
            run.addStage(stage2);

            PipelineStage stage3 = new PipelineStage("s3", "Deploy", PipelineStatus.NOT_STARTED, 0);
            stage3.addStep(new PipelineStep("st5", "docker push", PipelineStatus.NOT_STARTED, 0));
            run.addStage(stage3);

            canvas.updatePipelineRun(run);

            // Active stage must be Stage 2 (index 1) which is IN_PROGRESS
            assertEquals(1, canvas.getActiveStageIndex());

            // Verify canvas computed preferred size to fit stages
            assertTrue(canvas.getPreferredSize().width >= 700);
            assertTrue(canvas.getPreferredSize().height >= 200);

            // Paint canvas with populated DAG
            canvas.paint(g2);
            g2.dispose();
        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testLoadingSpinnerStateAndTransitionToDag() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            // 1. Initially not loading, no run
            assertFalse(canvas.isLoading());

            // 2. Set loading = true (initiating build / waiting for pipeline)
            canvas.setLoading(true);
            assertTrue(canvas.isLoading());

            // Paint loading spinner without error
            BufferedImage img = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.setSize(600, 300);
            canvas.paint(g2);

            // 3. Update with empty run: loading must remain true until stages arrive
            PipelineRun emptyRun = new PipelineRun("1", "#1", PipelineStatus.IN_PROGRESS);
            canvas.updatePipelineRun(emptyRun);
            assertTrue(canvas.isLoading());

            // 4. Update with actual stage: loading must automatically transition to false
            PipelineStage stage1 = new PipelineStage("s1", "Checkout", PipelineStatus.IN_PROGRESS, 1000);
            stage1.addStep(new PipelineStep("step1", "git clone", PipelineStatus.IN_PROGRESS, 1000));
            emptyRun.addStage(stage1);

            canvas.updatePipelineRun(emptyRun);
            assertFalse(canvas.isLoading(), "Loading must become false as soon as the first stage arrives");

            // Paint DAG
            canvas.paint(g2);
            g2.dispose();
        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testSmoothAutoScrollOnlyWhenSplitModeAndRunning() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setBounds(0, 0, 300, 300);
        scrollPane.getViewport().setBounds(0, 0, 300, 300);
        canvas.setBounds(0, 0, 800, 300);

        try {
            PipelineRun run = new PipelineRun("60", "#60", PipelineStatus.IN_PROGRESS);
            PipelineStage stage1 = new PipelineStage("s1", "Checkout", PipelineStatus.SUCCESS, 2000);
            stage1.addStep(new PipelineStep("st1", "clone", PipelineStatus.SUCCESS, 2000));
            run.addStage(stage1);

            PipelineStage stage2 = new PipelineStage("s2", "Build & Test", PipelineStatus.IN_PROGRESS, 5000);
            stage2.addStep(new PipelineStep("st2", "test", PipelineStatus.IN_PROGRESS, 5000));
            run.addStage(stage2);

            // 1. Single panel mode (splitMode = false): auto-scroll must NOT run even if pipeline is IN_PROGRESS
            canvas.setSplitMode(false);
            canvas.updatePipelineRun(run);
            canvas.scrollToActiveStage();
            assertFalse(canvas.isSmoothScrolling(), "Should not auto-scroll when not in split mode");

            // 2. Split mode = true, but pipeline is completed: auto-scroll must NOT run
            run.setStatus(PipelineStatus.SUCCESS);
            stage2.setStatus(PipelineStatus.SUCCESS);
            canvas.setSplitMode(true);
            canvas.updatePipelineRun(run);
            canvas.scrollToActiveStage();
            assertFalse(canvas.isSmoothScrolling(), "Should not auto-scroll when pipeline is finished");

            // 3. Split mode = true AND pipeline is IN_PROGRESS: smooth auto-scroll MUST trigger
            run.setStatus(PipelineStatus.IN_PROGRESS);
            stage2.setStatus(PipelineStatus.IN_PROGRESS);
            canvas.updatePipelineRun(run);
            canvas.scrollToActiveStage();
            assertTrue(canvas.isSmoothScrolling(), "Must trigger smooth auto-scroll in split mode while running");
            assertTrue(canvas.getScrollTargetX() > 0, "Target X should be positive to make active stage visible");

            // 4. When pipeline finishes, smooth scroll is canceled
            run.setStatus(PipelineStatus.SUCCESS);
            stage2.setStatus(PipelineStatus.SUCCESS);
            canvas.updatePipelineRun(run);
            assertFalse(canvas.isSmoothScrolling(), "Finishing pipeline must stop any ongoing smooth scroll");

        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testCleanPlainTextExtraction() {
        String html = "<html><head><style>body { color: red; }</style></head><body>"
                + "<font color='#FFFFFF'>2026/09/30 21:59:33: Starting build...</font><br>"
                + "[Pipeline] { (Checkout)<br>"
                + "[Pipeline] git clone https://github.com/myrepo.git &amp; checkout<br>"
                + "[Pipeline] echo &quot;Hello &lt;World&gt;&quot;<br>"
                + "[Pipeline] }<br>"
                + "</body></html>";

        String plainText = RepoCiDashboardPanel.extractCleanPlainText(html);

        assertFalse(plainText.contains("<font"), "Tags like <font> must be stripped");
        assertFalse(plainText.contains("<html>"), "<html> tags must be stripped");
        assertFalse(plainText.contains("style"), "Styles in <head> must be removed");
        assertFalse(plainText.contains("&amp;"), "Entities like &amp; must be unescaped");
        assertTrue(plainText.contains("2026/09/30 21:59:33: Starting build..."));
        assertTrue(plainText.contains("[Pipeline] { (Checkout)"));
        assertTrue(plainText.contains("git clone https://github.com/myrepo.git & checkout"));
        assertTrue(plainText.contains("echo \"Hello <World>\""));

        // Verify line breaks exist
        String[] lines = plainText.split("\n");
        assertEquals(5, lines.length, "Should split into exactly 5 lines with proper line breaks");
        assertEquals("2026/09/30 21:59:33: Starting build...", lines[0]);
        assertEquals("[Pipeline] { (Checkout)", lines[1]);
        assertEquals("[Pipeline] }", lines[4]);
    }

    @Test
    public void testPipelineHeaderPanel() {
        PipelineHeaderPanel header = new PipelineHeaderPanel();
        header.setPlatformName("Jenkins");
        header.setSplitMode(false);

        PipelineRun run = new PipelineRun("12", "Build #12", PipelineStatus.SUCCESS);
        run.setBranch("develop");
        run.setDurationMillis(35000);
        run.setWebUrl("http://localhost:8080/job/test/12");

        header.updatePipelineRun(run, "Jenkins");
        header.setSplitMode(true);
        assertNotNull(header);
    }
    @Test
    public void testStageFinishedPredicates() {
        PipelineStage stageSuccess = new PipelineStage("1", "Checkout", PipelineStatus.SUCCESS, 2000);
        PipelineStage stageFailed = new PipelineStage("2", "Test", PipelineStatus.FAILED, 5000);
        PipelineStage stageAborted = new PipelineStage("3", "Deploy", PipelineStatus.ABORTED, 1000);
        PipelineStage stageSkipped = new PipelineStage("4", "Notify", PipelineStatus.SKIPPED, 0);
        PipelineStage stagePaused = new PipelineStage("5", "Approval", PipelineStatus.PAUSED, 60000);
        PipelineStage stageInProgress = new PipelineStage("6", "Build", PipelineStatus.IN_PROGRESS, 12000);
        PipelineStage stageNotStarted = new PipelineStage("7", "Release", PipelineStatus.NOT_STARTED, 0);

        assertTrue(PipelineDagCanvas.isStageFinished(stageSuccess));
        assertTrue(PipelineDagCanvas.isStageFinished(stageFailed));
        assertTrue(PipelineDagCanvas.isStageFinished(stageAborted));
        assertTrue(PipelineDagCanvas.isStageFinished(stageSkipped));
        assertTrue(PipelineDagCanvas.isStageFinished(stagePaused));

        assertFalse(PipelineDagCanvas.isStageFinished(stageInProgress));
        assertFalse(PipelineDagCanvas.isStageFinished(stageNotStarted));
        assertFalse(PipelineDagCanvas.isStageFinished(null));
    }

    @Test
    public void testStageHitTestingAndBounds() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            PipelineRun run = new PipelineRun("1", "#1", PipelineStatus.IN_PROGRESS);
            PipelineStage s1 = new PipelineStage("s1", "Checkout", PipelineStatus.SUCCESS, 2000);
            s1.addStep(new PipelineStep("st1", "git clone", PipelineStatus.SUCCESS, 2000));
            run.addStage(s1);

            PipelineStage s2 = new PipelineStage("s2", "Build", PipelineStatus.FAILED, 5000);
            s2.addStep(new PipelineStep("st2", "compile", PipelineStatus.SUCCESS, 3000));
            s2.addStep(new PipelineStep("st3", "test", PipelineStatus.FAILED, 2000));
            run.addStage(s2);

            canvas.updatePipelineRun(run);

            Rectangle bounds0 = canvas.getStageBounds(0, s1);
            Rectangle bounds1 = canvas.getStageBounds(1, s2);

            assertTrue(bounds0.x < bounds1.x, "Stage 1 should be positioned after Stage 0 horizontally");
            assertEquals(210, bounds0.width);
            assertEquals(210, bounds1.width);

            // Point inside stage 0
            assertEquals(0, canvas.getStageIndexAt(new Point(bounds0.x + 10, bounds0.y + 10)));

            // Point inside stage 1
            assertEquals(1, canvas.getStageIndexAt(new Point(bounds1.x + 10, bounds1.y + 10)));

            // Point outside any card (far to the right or top)
            assertEquals(-1, canvas.getStageIndexAt(new Point(bounds1.x + bounds1.width + 100, 10)));
            assertEquals(-1, canvas.getStageIndexAt(new Point(5, 5)));
        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testStageDetailsDialogAccordionAndExpandAll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PipelineStage stage = new PipelineStage("s1", "Build & Test", PipelineStatus.SUCCESS, 45000);
            stage.setStartTimeMillis(System.currentTimeMillis() - 45000);
            PipelineStep step1 = new PipelineStep("step-1", "Compile Code", PipelineStatus.SUCCESS, 15000);
            PipelineStep step2 = new PipelineStep("step-2", "Run Unit Tests", PipelineStatus.SUCCESS, 20000);
            PipelineStep step3 = new PipelineStep("step-3", "Package Artifact", PipelineStatus.SUCCESS, 10000);
            stage.addStep(step1);
            stage.addStep(step2);
            stage.addStep(step3);

            JFrame testFrame = new JFrame("Test Window");
            JPanel dummyParent = new JPanel();
            testFrame.add(dummyParent);
            StageDetailsDialog dialog = new StageDetailsDialog(dummyParent, stage);

            try {
                List<StageDetailsDialog.StepCollapsiblePanel> panels = dialog.getStepPanels();
                assertEquals(3, panels.size());

                // 1. Initial State: all steps MUST be collapsed by default
                for (StageDetailsDialog.StepCollapsiblePanel panel : panels) {
                    assertFalse(panel.isExpanded(), "Step should be collapsed initially");
                }

                // 2. Expand step 1 individually
                panels.get(0).setExpanded(true);
                assertTrue(panels.get(0).isExpanded());
                assertFalse(panels.get(1).isExpanded());
                assertFalse(panels.get(2).isExpanded());

                // 3. Expand all
                dialog.setAllExpanded(true);
                for (StageDetailsDialog.StepCollapsiblePanel panel : panels) {
                    assertTrue(panel.isExpanded(), "Step should be expanded after expand all");
                }

                // 4. Collapse all
                dialog.setAllExpanded(false);
                for (StageDetailsDialog.StepCollapsiblePanel panel : panels) {
                    assertFalse(panel.isExpanded(), "Step should be collapsed after collapse all");
                }
            } finally {
                dialog.close(DialogWrapper.OK_EXIT_CODE);
                testFrame.dispose();
            }
        });
    }
    @Test
    public void testStageHoverInteraction() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            PipelineRun run = new PipelineRun("1", "#1", PipelineStatus.IN_PROGRESS);
            // Finished stage (SUCCESS)
            PipelineStage s0 = new PipelineStage("s0", "Checkout", PipelineStatus.SUCCESS, 2000);
            s0.addStep(new PipelineStep("st0", "clone", PipelineStatus.SUCCESS, 2000));
            run.addStage(s0);

            // Active stage (IN_PROGRESS) - NOT finished
            PipelineStage s1 = new PipelineStage("s1", "Build", PipelineStatus.IN_PROGRESS, 5000);
            s1.addStep(new PipelineStep("st1", "mvn clean compile", PipelineStatus.IN_PROGRESS, 5000));
            run.addStage(s1);

            // Not started stage - NOT finished
            PipelineStage s2 = new PipelineStage("s2", "Deploy", PipelineStatus.NOT_STARTED, 0);
            run.addStage(s2);

            canvas.updatePipelineRun(run);

            Rectangle b0 = canvas.getStageBounds(0, s0);
            Rectangle b1 = canvas.getStageBounds(1, s1);

            // 1. Hover over finished stage s0 -> should become hovered with hand cursor & tooltip
            canvas.updateStageHover(new Point(b0.x + 10, b0.y + 10));
            assertEquals(0, canvas.getHoveredStageIndex());
            assertEquals(Cursor.HAND_CURSOR, canvas.getCursor().getType());
            assertNotNull(canvas.getToolTipText());
            assertTrue(canvas.getToolTipText().contains("Checkout"));

            // 2. Hover over unfinished stage s1 -> should NOT be hovered, cursor should reset to default
            canvas.updateStageHover(new Point(b1.x + 10, b1.y + 10));
            assertEquals(-1, canvas.getHoveredStageIndex(), "In-progress stage should not be hover-highlighted");
            assertEquals(Cursor.DEFAULT_CURSOR, canvas.getCursor().getType());
            assertNull(canvas.getToolTipText());

            // 3. Hover over empty space outside cards -> clear hover
            canvas.updateStageHover(new Point(5, 5));
            assertEquals(-1, canvas.getHoveredStageIndex());
            assertEquals(Cursor.DEFAULT_CURSOR, canvas.getCursor().getType());

            // 4. Painting with hover on finished stage
            canvas.updateStageHover(new Point(b0.x + 10, b0.y + 10));
            BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.setSize(800, 400);
            assertDoesNotThrow(() -> canvas.paint(g2));
            g2.dispose();
        } finally {
            canvas.dispose();
        }
    }
}
