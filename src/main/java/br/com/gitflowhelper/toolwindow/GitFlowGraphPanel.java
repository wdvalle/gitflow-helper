package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.actions.InitAction;
import br.com.gitflowhelper.dialog.InitDialog;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GitFlowGraphPanel extends JPanel {
    private final Project project;
    private final GraphCanvas graphCanvas;

    public GitFlowGraphPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        graphCanvas = new GraphCanvas(project);
        JBScrollPane scrollPane = new JBScrollPane(graphCanvas);
        add(scrollPane, BorderLayout.CENTER);

        // Header
        JPanel header = new JPanel(new BorderLayout());

        // Action Toolbar
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new RefreshAction());

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("GitFlowGraphPanel", actionGroup, true);
        actionToolbar.setTargetComponent(this);
        header.add(actionToolbar.getComponent(), BorderLayout.WEST);

        // Help Label
        JLabel helpLabel = new JLabel("Visual representation of your local branches and their relations.");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11));
        helpLabel.setForeground(JBColor.GRAY);
        header.add(helpLabel, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                refresh();
                // Remove listener to avoid multiple refreshes
                removeAncestorListener(this);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }

    private Runnable onNewContent;

    /** Registers a callback invoked whenever graph content is refreshed/updated. */
    public void setOnNewContent(Runnable onNewContent) {
        this.onNewContent = onNewContent;
    }

    public void refresh() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        List<GitRepository> repositories = repoManager.getRepositories();

        if (!repositories.isEmpty()) {
            GitRepository repo = repositories.get(0);
            List<String> localBranches = repo.getBranches().getLocalBranches().stream()
                    .map(b -> b.getName())
                    .collect(Collectors.toList());

            graphCanvas.updateData(
                    settings.getMainBranch(),
                    settings.getDevelopBranch(),
                    localBranches.stream().filter(b -> b.startsWith(settings.getFeaturePrefix())).collect(Collectors.toList()),
                    localBranches.stream().filter(b -> b.startsWith(settings.getReleasePrefix())).collect(Collectors.toList()),
                    localBranches.stream().filter(b -> b.startsWith(settings.getHotfixPrefix())).collect(Collectors.toList()),
                    localBranches
            );
        } else {
            graphCanvas.updateData(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        graphCanvas.updateEmptyText();
        if (onNewContent != null) {
            onNewContent.run();
        }
    }

    private class RefreshAction extends AnAction {
        RefreshAction() {
            super("Refresh Flow", "Refresh the Git Flow graph", AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            refresh();
        }
    }

    private static class GraphCanvas extends JPanel implements ComponentWithEmptyText {
        private final Project project;
        private String mainBranch;
        private String developBranch;
        private List<String> features;
        private List<String> releases;
        private List<String> hotfixes;
        private List<String> allLocal;
        private final StatusText emptyText = new StatusText(this) {
            @Override
            protected boolean isStatusVisible() {
                return mainBranch == null || developBranch == null;
            }
        };

        public GraphCanvas(Project project) {
            this.project = project;
            updateEmptyText();
        }

        public void updateData(String main, String develop, List<String> features, List<String> releases, List<String> hotfixes, List<String> allLocal) {
            this.mainBranch = main;
            this.developBranch = develop;
            this.features = features;
            this.releases = releases;
            this.hotfixes = hotfixes;
            this.allLocal = allLocal;

            int height = 150 + (features.size() + releases.size() + hotfixes.size()) * 40;
            setPreferredSize(new Dimension(500, Math.max(300, height)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (mainBranch != null && developBranch != null) {
                drawGraph(g);
            } else {
                emptyText.paint(this, g);
            }
        }

        private void drawGraph(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int xStart = 50;
            int xEnd = 400;
            int y = 40;

            // Draw Main
            drawBranch(g2, "Production (main)", mainBranch, xStart, xEnd, y, JBColor.BLUE, allLocal.contains(mainBranch));

            // Draw Develop
            y += 60;
            drawBranch(g2, "Integration (develop)", developBranch, xStart, xEnd, y, JBColor.GREEN, allLocal.contains(developBranch));

            // Draw connection Main -> Develop
            g2.setColor(JBColor.GRAY);
            g2.drawLine(xStart + 20, 40, xStart + 40, y);

            // Draw Features, Releases, Hotfixes
            int featY = y + 60;

            // Draw Hotfixes (from Main)
            for (String hot : hotfixes) {
                drawSubBranch(g2, hot, xStart + 40, xEnd, featY, JBColor.RED, 40);
                featY += 40;
            }

            // Draw Releases (from Develop)
            for (String rel : releases) {
                drawSubBranch(g2, rel, xStart + 40, xEnd, featY, JBColor.CYAN, y);
                featY += 40;
            }

            // Draw Features (from Develop)
            for (String feat : features) {
                drawSubBranch(g2, feat, xStart + 40, xEnd, featY, JBColor.ORANGE, y);
                featY += 40;
            }

            // Draw Legend
            featY += 20;
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10));
            g2.setColor(JBColor.BLUE);
            g2.drawString("■ Main", xStart, featY);
            g2.setColor(JBColor.GREEN);
            g2.drawString("■ Develop", xStart + 70, featY);
            g2.setColor(JBColor.ORANGE);
            g2.drawString("■ Feature", xStart + 150, featY);
            g2.setColor(JBColor.CYAN);
            g2.drawString("■ Release", xStart + 230, featY);
            g2.setColor(JBColor.RED);
            g2.drawString("■ Hotfix", xStart + 310, featY);
        }

        private void drawBranch(Graphics2D g2, String label, String name, int x1, int x2, int y, Color color, boolean exists) {
            g2.setColor(exists ? color : JBColor.LIGHT_GRAY);
            g2.setStroke(new BasicStroke(3));
            g2.drawLine(x1, y, x2, y);

            g2.fillOval(x1 + 10, y - 5, 10, 10);

            g2.setColor(JBColor.foreground());
            g2.setFont(g2.getFont().deriveFont(Font.BOLD));
            g2.drawString(label + ": " + name, x1, y - 10);
            if (!exists) {
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10));
                g2.drawString("(not found locally)", x2 + 5, y + 5);
            }
        }

        private void drawSubBranch(Graphics2D g2, String name, int x1, int x2, int y, Color color, int parentY) {
            g2.setColor(JBColor.GRAY);
            g2.setStroke(new BasicStroke(1));
            g2.drawLine(x1, parentY, x1 + 20, y); // Connection from parent

            g2.setColor(color);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(x1 + 20, y, x2, y);

            g2.fillOval(x1 + 25, y - 4, 8, 8);

            g2.setColor(JBColor.foreground());
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11));
            g2.drawString(name, x1 + 40, y + 5);
        }

        @NotNull
        @Override
        public StatusText getEmptyText() {
            return emptyText;
        }

        private void updateEmptyText() {
            emptyText.clear();
            emptyText.setText("Git Flow not initialized or configured.");
            emptyText.appendLine("Configure it in Git Flow Helper settings", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> {
                new InitDialog(new InitAction(""), project).show();
            });
        }
    }
}
