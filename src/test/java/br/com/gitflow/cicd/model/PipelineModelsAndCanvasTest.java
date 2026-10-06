package br.com.gitflow.cicd.model;

import br.com.gitflow.cicd.StepLogProvider;
import br.com.gitflowhelper.dialog.StageDetailsDialog;
import com.intellij.openapi.ui.DialogWrapper;
import java.util.List;
import br.com.gitflowhelper.toolwindow.ci.PipelineDagCanvas;
import br.com.gitflowhelper.toolwindow.ci.PipelineHeaderPanel;
import br.com.gitflowhelper.toolwindow.ci.RepoCiDashboardPanel;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
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
    public void testPipelineStepLogs() {
        PipelineStep step = new PipelineStep("step-1", "Compile Code", PipelineStatus.SUCCESS, 3500);
        assertEquals("", step.getLog());

        step.appendLog("[INFO] Scanning for projects...");
        step.appendLog("[INFO] Compiling 24 source files to target/classes\n");
        step.appendLog("[INFO] BUILD SUCCESS");

        String log = step.getLog();
        assertTrue(log.contains("[INFO] Scanning for projects..."));
        assertTrue(log.contains("[INFO] Compiling 24 source files to target/classes"));
        assertTrue(log.contains("[INFO] BUILD SUCCESS"));

        step.setLog("Reset logs directly");
        assertEquals("Reset logs directly", step.getLog());
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
        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testSmoothAutoScrollWhenRunningRegardlessOfSplitMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
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

                // 1. Single panel mode (splitMode = false): auto-scroll MUST run when pipeline is IN_PROGRESS
                canvas.setSplitMode(false);
                canvas.updatePipelineRun(run);
                canvas.scrollToActiveStage();
                assertTrue(canvas.isSmoothScrolling(), "Should auto-scroll in single panel mode when pipeline is running");
                assertTrue(canvas.getScrollTargetX() > 0, "Target X should be positive to make active stage visible");

                // 2. Single panel mode, but pipeline is completed: auto-scroll must NOT run
                run.setStatus(PipelineStatus.SUCCESS);
                stage2.setStatus(PipelineStatus.SUCCESS);
                canvas.updatePipelineRun(run);
                canvas.scrollToActiveStage();
                assertFalse(canvas.isSmoothScrolling(), "Should not auto-scroll when pipeline is finished");

                // 3. Split mode = true AND pipeline is IN_PROGRESS: smooth auto-scroll MUST also trigger
                run.setStatus(PipelineStatus.IN_PROGRESS);
                stage2.setStatus(PipelineStatus.IN_PROGRESS);
                canvas.setSplitMode(true);
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
        });
    }

    @Test
    public void testRawLogsInMemoryBufferAndConsoleClearing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RepoCiDashboardPanel panel = new RepoCiDashboardPanel(null, "/fake/repo");

            // 1. Initial state
            assertTrue(panel.getRawLogs().isEmpty());

            // 2. Append plugin log and console log
            panel.appendPluginLog("Build starting...");
            panel.appendConsoleLog("<font color='#81C784'>[Pipeline] { (Checkout)</font><br>");
            panel.appendConsoleLog("[Pipeline] echo \"Hello &lt;World&gt;\"<br>");

            String logs = panel.getRawLogs();
            assertTrue(logs.contains("Build starting..."));
            assertTrue(logs.contains("[Pipeline] { (Checkout)"));
            assertTrue(logs.contains("echo \"Hello <World>\""));
            assertFalse(logs.contains("<font"));
            assertFalse(logs.contains("<br>"));

            // 3. Clear console manually
            panel.clearConsole();
            assertTrue(panel.getRawLogs().isEmpty());

            // 4. Starting new execution clears logs
            panel.appendConsoleLog("Old execution log\n");
            assertFalse(panel.getRawLogs().isEmpty());
            panel.startLoading();
            assertTrue(panel.getRawLogs().isEmpty(), "startLoading() for new execution must clear the console logs");

            // 5. Test restoreIdlePipeline with lastRun
            PipelineRun run = new PipelineRun("42", "#42", PipelineStatus.SUCCESS);
            PipelineStage stage1 = new PipelineStage("s1", "Build", PipelineStatus.SUCCESS, 1200);
            run.addStage(stage1);

            panel.updatePipelineRun(run, "Jenkins");
            assertEquals(run, panel.getLastRun());

            panel.appendConsoleLog("Build completed output\n");
            panel.setSplitMode(true);
            assertTrue(panel.isSplitMode());

            // User triggers clear output -> restores idle pipeline showing only stages, without last execution data
            panel.restoreIdlePipeline();
            assertTrue(panel.getRawLogs().isEmpty());
            assertFalse(panel.isSplitMode());
            assertTrue(panel.isIdle());
            assertEquals(run, panel.getLastRun());
            assertFalse(panel.isRunning());
            assertNotNull(panel.getDagCanvas().getPipelineRun());
            assertEquals(1, panel.getDagCanvas().getPipelineRun().getStages().size());
            PipelineStage restoredStage = panel.getDagCanvas().getPipelineRun().getStages().get(0);
            assertEquals("Build", restoredStage.getName());
            assertEquals(PipelineStatus.NOT_STARTED, restoredStage.getStatus(), "Idle stage status must be NOT_STARTED");
            assertEquals(0, restoredStage.getDurationMillis(), "Idle stage duration must be 0");
            assertTrue(restoredStage.getSteps().isEmpty(), "Idle stage must only show the stage itself, without steps");

            // 6. Test complete diagram blueprint retention:
            // Full 3-stage run registered
            PipelineRun fullRun = new PipelineRun("43", "#43", PipelineStatus.SUCCESS);
            fullRun.addStage(new PipelineStage("s1", "Checkout", PipelineStatus.SUCCESS, 1000));
            fullRun.addStage(new PipelineStage("s2", "Build & Test", PipelineStatus.SUCCESS, 3000));
            fullRun.addStage(new PipelineStage("s3", "Deploy", PipelineStatus.SUCCESS, 2000));
            panel.updatePipelineRun(fullRun, "Jenkins");
            assertEquals(3, panel.getBlueprintStages().size());

            // Next run fails or is interrupted at stage 1 (only Checkout executed)
            PipelineRun failedRun = new PipelineRun("44", "#44", PipelineStatus.FAILED);
            failedRun.addStage(new PipelineStage("s1", "Checkout", PipelineStatus.FAILED, 500));
            panel.updatePipelineRun(failedRun, "Jenkins");

            // When restored to idle blueprint mode, the diagram MUST remain complete with all 3 stages
            panel.restoreIdlePipeline();
            assertNotNull(panel.getDagCanvas().getPipelineRun());
            assertEquals(3, panel.getDagCanvas().getPipelineRun().getStages().size(), "Blueprint diagram must remain complete with all stages even if last run failed or was interrupted");
            assertEquals("Checkout", panel.getDagCanvas().getPipelineRun().getStages().get(0).getName());
            assertEquals("Build & Test", panel.getDagCanvas().getPipelineRun().getStages().get(1).getName());
            assertEquals("Deploy", panel.getDagCanvas().getPipelineRun().getStages().get(2).getName());
            for (PipelineStage s : panel.getDagCanvas().getPipelineRun().getStages()) {
                assertEquals(PipelineStatus.NOT_STARTED, s.getStatus(), "All stages in idle blueprint must have NOT_STARTED status");
                assertEquals(0, s.getDurationMillis(), "All stages in idle blueprint must have duration 0");
                assertTrue(s.getSteps().isEmpty(), "All stages in idle blueprint must omit steps");
            }
        });
    }

    @Test
    public void testPipelineHeaderPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
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
        });
    }

    @Test
    public void testStartAndStopButtonStatesAndToggleSplitIndicator() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PipelineHeaderPanel header = new PipelineHeaderPanel();

            // 1. Initially: not running
            assertFalse(header.isRunning());
            assertTrue(header.getStartButton().isEnabled(), "Start button must be enabled when pipeline is not running");
            assertFalse(header.getStopButton().isEnabled(), "Stop button must be disabled when pipeline is not running");

            // Visual identity check: uniform size (28x24) for all action buttons
            assertEquals(new Dimension(28, 24), header.getStartButton().getPreferredSize());
            assertEquals(new Dimension(28, 24), header.getStopButton().getPreferredSize());
            assertEquals(new Dimension(28, 24), header.getClearButton().getPreferredSize());
            assertEquals(new Dimension(28, 24), header.getOpenBrowserButton().getPreferredSize());
            assertEquals(new Dimension(28, 24), header.getToggleSplitBtn().getPreferredSize());

            // Clear button check
            assertNotNull(header.getClearButton());
            assertTrue(header.getClearButton().isEnabled());
            assertEquals("Clear pipeline output", header.getClearButton().getToolTipText());

            boolean[] clearClicked = {false};
            header.setCallbacks(null, null, null, () -> clearClicked[0] = true);
            header.getClearButton().doClick();
            assertTrue(clearClicked[0], "Clear button should trigger onClear callback");

            // 2. Set running = true
            header.setRunning(true);
            assertTrue(header.isRunning());
            assertFalse(header.getStartButton().isEnabled(), "Start button must be disabled when pipeline is running");
            assertTrue(header.getStopButton().isEnabled(), "Stop button must be enabled when pipeline is running");

            // 3. Mark aborted -> transitions to not running
            header.markAborted();
            assertFalse(header.isRunning());
            assertTrue(header.getStartButton().isEnabled(), "Start button must be enabled after abort");
            assertFalse(header.getStopButton().isEnabled(), "Stop button must be disabled after abort");

            // 4. Update with terminal run -> transitions to not running
            PipelineRun finishedRun = new PipelineRun("10", "#10", PipelineStatus.SUCCESS);
            header.updatePipelineRun(finishedRun, "Jenkins");
            assertFalse(header.isRunning());
            assertTrue(header.getStartButton().isEnabled());
            assertFalse(header.getStopButton().isEnabled());

            // 5. Update with in-progress run -> transitions to running
            PipelineRun runningRun = new PipelineRun("11", "#11", PipelineStatus.IN_PROGRESS);
            header.updatePipelineRun(runningRun, "Jenkins");
            assertTrue(header.isRunning());
            assertFalse(header.getStartButton().isEnabled());
            assertTrue(header.getStopButton().isEnabled());

            // 6. Test toggle split button: marked / unmarked
            header.setSplitMode(false);
            assertFalse(header.isSplitMode());
            assertFalse(header.getToggleSplitBtn().isSelected(), "Toggle button should be unselected (desmarcado) when split is false");

            header.setSplitMode(true);
            assertTrue(header.isSplitMode());
            assertTrue(header.getToggleSplitBtn().isSelected(), "Toggle button should be selected (marcado) when split is true");

            // Paint buttons to verify paintComponent execution
            BufferedImage img2 = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gAction = img2.createGraphics();
            header.getStartButton().paint(gAction);
            header.getStopButton().paint(gAction);
            header.getClearButton().paint(gAction);
            header.getOpenBrowserButton().paint(gAction);
            gAction.dispose();

            // Paint toggle button in both states
            BufferedImage img = new BufferedImage(100, 30, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            header.getToggleSplitBtn().setSize(30, 24);
            header.getToggleSplitBtn().setSelected(true);
            header.getToggleSplitBtn().paint(g2);
            header.getToggleSplitBtn().setSelected(false);
            header.getToggleSplitBtn().paint(g2);
            g2.dispose();
        });
    }

    @Test
    public void testRepoCiDashboardPanelConsoleLogDoesNotForceSplitMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RepoCiDashboardPanel panel = new RepoCiDashboardPanel(null, "/fake/path");
            try {
                // Ensure initial mode is hidden / single panel
                panel.setSplitMode(false);
                assertFalse(panel.isSplitMode());
                assertFalse(panel.getHeaderPanel().getToggleSplitBtn().isSelected());

                // Append console log content while hidden
                panel.appendConsoleLog("Chunk 1: Building project...\n");
                panel.appendPluginLog("Build step completed.");

                // Must remain hidden (splitMode == false)
                assertFalse(panel.isSplitMode(), "Incoming logs must NOT force splitMode to true when hidden");
                assertFalse(panel.getHeaderPanel().getToggleSplitBtn().isSelected(), "Toggle button must remain unselected");

                // Manually open split mode
                panel.setSplitMode(true);
                assertTrue(panel.isSplitMode());
                assertTrue(panel.getHeaderPanel().getToggleSplitBtn().isSelected(), "Toggle button must be selected when split");

                // Close it again
                panel.setSplitMode(false);
                assertFalse(panel.isSplitMode());
                assertFalse(panel.getHeaderPanel().getToggleSplitBtn().isSelected());
            } finally {
                panel.dispose();
            }
        });
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
    public void testIdleCanvasNotClickable() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            PipelineStage stageSuccess = new PipelineStage("1", "Checkout", PipelineStatus.SUCCESS, 2000);
            stageSuccess.addStep(new PipelineStep("st1", "git clone", PipelineStatus.SUCCESS, 2000));
            PipelineRun run = new PipelineRun("1", "#1", PipelineStatus.SUCCESS);
            run.addStage(stageSuccess);
            canvas.updatePipelineRun(run);

            // Initially not idle: finished stage is clickable
            assertFalse(canvas.isIdle());
            assertTrue(canvas.isStageClickable(stageSuccess));

            Rectangle bounds = canvas.getStageBounds(0, stageSuccess);
            canvas.updateStageHover(new Point(bounds.x + 10, bounds.y + 10));
            assertEquals(0, canvas.getHoveredStageIndex());
            assertEquals(Cursor.HAND_CURSOR, canvas.getCursor().getType());

            // Set idle = true: finished stage is NOT clickable and only stage header is sized
            canvas.setIdle(true);
            assertTrue(canvas.isIdle());
            assertFalse(canvas.isStageClickable(stageSuccess), "Idle diagram must NOT be clickable");
            Rectangle idleBounds = canvas.getStageBounds(0, stageSuccess);
            assertEquals(PipelineDagCanvas.HEADER_HEIGHT, idleBounds.height, "Idle stage card must only show stage header height");

            // Hover over finished stage in idle mode must not highlight or show hand cursor
            canvas.updateStageHover(new Point(bounds.x + 10, bounds.y + 10));
            assertEquals(-1, canvas.getHoveredStageIndex(), "Idle stage should not have hover effect");
            assertEquals(Cursor.DEFAULT_CURSOR, canvas.getCursor().getType(), "Idle stage should keep default cursor");

            // Painting idle canvas should succeed without exceptions
            BufferedImage img = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.paint(g2);
            g2.dispose();

            // When updated with a running pipeline, idle flag resets
            PipelineRun runningRun = new PipelineRun("2", "#2", PipelineStatus.IN_PROGRESS);
            runningRun.addStage(new PipelineStage("2", "Test", PipelineStatus.IN_PROGRESS, 1000));
            canvas.updatePipelineRun(runningRun);
            assertFalse(canvas.isIdle(), "Running pipeline should clear idle flag");
        } finally {
            canvas.dispose();
        }
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

                // Verify logArea does NOT have a JScrollPane parent (scroll-less, full width)
                assertFalse(panels.get(0).getLogArea().getParent() instanceof JViewport,
                        "Log area must be directly in the panel without scrollpane");
                assertTrue(panels.get(0).getLogArea().getLineWrap(), "Log area should have line wrap enabled");

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
    public void testStageDetailsDialogStepLogsAndProvider() throws Exception {
        PipelineStage stage = new PipelineStage("stage-deploy", "Deploy Stage", PipelineStatus.SUCCESS, 10000);
        PipelineStep step1 = new PipelineStep("step-build", "Build Image", PipelineStatus.SUCCESS, 5000);
        step1.setLog("[docker] Building image gitflow-helper:latest\n[docker] Step 1/5 FROM openjdk:17\n[docker] Success");

        PipelineStep step2 = new PipelineStep("step-push", "Push Image", PipelineStatus.SUCCESS, 5000);
        // step2 has no pre-cached log, will use provider
        stage.addStep(step1);
        stage.addStep(step2);

        StepLogProvider mockProvider = step -> {
            if ("step-push".equals(step.getId())) {
                return "[docker] Pushing image to registry...\n[docker] Pushed successfully";
            }
            return null;
        };

        final StageDetailsDialog[] dialogRef = new StageDetailsDialog[1];
        final JFrame[] frameRef = new JFrame[1];
        final StageDetailsDialog.StepCollapsiblePanel[] panelsRef = new StageDetailsDialog.StepCollapsiblePanel[2];

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            JPanel panel = new JPanel();
            frame.add(panel);
            StageDetailsDialog dialog = new StageDetailsDialog(panel, stage, mockProvider);

            dialogRef[0] = dialog;
            frameRef[0] = frame;

            List<StageDetailsDialog.StepCollapsiblePanel> panels = dialog.getStepPanels();
            assertEquals(2, panels.size());
            panelsRef[0] = panels.get(0);
            panelsRef[1] = panels.get(1);

            // Initial state: collapsed
            assertFalse(panelsRef[0].isExpanded());
            assertFalse(panelsRef[1].isExpanded());

            // Expand step1: log loaded synchronously from step.getLog()
            panelsRef[0].setExpanded(true);
            assertTrue(panelsRef[0].getLogText().contains("Building image gitflow-helper:latest"));
            assertTrue(panelsRef[0].getLogText().contains("[docker] Success"));

            // Test copy log button for step1
            panelsRef[0].getCopyButton().doClick();
            try {
                String clipboardContent = (String) Toolkit.getDefaultToolkit()
                        .getSystemClipboard().getData(DataFlavor.stringFlavor);
                assertEquals(panelsRef[0].getLogText(), clipboardContent);
            } catch (Exception ignored) {
                // Clipboard access may be restricted in headless or certain CI environments
            }

            // Expand step2: triggers async fetch via provider
            panelsRef[1].setExpanded(true);
        });

        // Wait outside EDT so background thread & EDT invokeLater can complete
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && !panelsRef[1].getLogText().contains("Pushing image")) {
            Thread.sleep(50);
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(panelsRef[1].getLogText().contains("Pushing image to registry..."));
                assertTrue(panelsRef[1].getLogText().contains("Pushed successfully"));
            } finally {
                dialogRef[0].close(DialogWrapper.OK_EXIT_CODE);
                frameRef[0].dispose();
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

    @Test
    public void testMarkAbortedHaltsSpinnerAndSetsStatus() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            canvas.setLoading(true);
            assertTrue(canvas.isLoading());

            PipelineRun run = new PipelineRun("1", "#1", PipelineStatus.IN_PROGRESS);
            PipelineStage stage1 = new PipelineStage("s1", "Checkout", PipelineStatus.IN_PROGRESS, 1000);
            PipelineStep step1 = new PipelineStep("step1", "git clone", PipelineStatus.IN_PROGRESS, 1000);
            stage1.addStep(step1);
            run.addStage(stage1);
            canvas.updatePipelineRun(run);

            canvas.markAborted();
            assertFalse(canvas.isLoading(), "Loading must be false after markAborted");
            assertEquals(PipelineStatus.ABORTED, run.getStatus());
            assertEquals(PipelineStatus.ABORTED, stage1.getStatus());
            assertEquals(PipelineStatus.ABORTED, step1.getStatus());
            assertFalse(canvas.hasRunningEntities());

            BufferedImage img = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.paint(g2);
            g2.dispose();
        } finally {
            canvas.dispose();
        }
    }

    @Test
    public void testHtmlStackOverflowError() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        for (int i = 0; i < 5000; i++) {
            sb.append("<div class=\"container-fluid\"><p>Some long error message or text </p></div>");
        }
        sb.append("</body></html>");
        String bigHtml = sb.toString();

        try {
            bigHtml.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        } catch (StackOverflowError e) {
            System.out.println("CAUGHT STACK OVERFLOW IN replaceAll: " + e);
            throw e;
        }
    }

}
