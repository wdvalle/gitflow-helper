package br.com.gitflow.cicd.model;

import br.com.gitflowhelper.toolwindow.ci.PipelineDagCanvas;
import br.com.gitflowhelper.toolwindow.ci.PipelineHeaderPanel;
import org.junit.jupiter.api.Test;

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
    public void testPipelineDagCanvasPainting() {
        PipelineDagCanvas canvas = new PipelineDagCanvas();
        try {
            // 1. Paint empty canvas
            BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            canvas.setSize(800, 400);
            canvas.paint(g2);

            // 2. Populate with stages and steps: Stage 1 (Success) -> Stage 2 (In Progress) -> Stage 3 (Not started)
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
}
