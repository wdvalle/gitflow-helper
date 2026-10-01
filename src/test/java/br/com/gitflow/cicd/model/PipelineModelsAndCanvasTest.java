package br.com.gitflow.cicd.model;

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
}
