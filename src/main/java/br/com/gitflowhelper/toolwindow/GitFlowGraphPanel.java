package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.actions.InitAction;
import br.com.gitflowhelper.dialog.InitDialog;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final ComboBox<String> repoCombo = new ComboBox<>();
    private List<GitRepository> repositories = Collections.emptyList();
    private String selectedRepoPath = null;
    private boolean isPopulatingCombo = false;
    private Runnable onNewContent;

    public GitFlowGraphPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        graphCanvas = new GraphCanvas(project);
        JBScrollPane scrollPane = new JBScrollPane(graphCanvas);
        add(scrollPane, BorderLayout.CENTER);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

        // Action Toolbar
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new RefreshAction());

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("GitFlowGraphPanel", actionGroup, true);
        actionToolbar.setTargetComponent(this);

        // Left controls: Refresh toolbar + Repo selector
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leftPanel.add(actionToolbar.getComponent());

        JLabel repoLabel = new JLabel("Repo:");
        repoLabel.setFont(repoLabel.getFont().deriveFont(Font.BOLD));
        leftPanel.add(repoLabel);

        repoCombo.setPreferredSize(new Dimension(180, Math.max(26, repoCombo.getPreferredSize().height)));
        repoCombo.addActionListener(e -> {
            if (isPopulatingCombo) return;
            int idx = repoCombo.getSelectedIndex();
            if (idx >= 0 && idx < repositories.size()) {
                selectedRepoPath = repositories.get(idx).getRoot().getPath();
                updateGraphForSelectedRepo();
            }
        });
        leftPanel.add(repoCombo);

        header.add(leftPanel, BorderLayout.WEST);

        // Help Label
        JLabel helpLabel = new JLabel("Visual representation of your local branches and their relations.");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11));
        helpLabel.setForeground(JBColor.GRAY);
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        rightPanel.add(helpLabel);
        header.add(rightPanel, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        if (project != null && !project.isDisposed()) {
            project.getMessageBus().connect().subscribe(
                    GitFlowSettingsListener.TOPIC,
                    this::refresh
            );
        }

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

    /** Registers a callback invoked whenever graph content is refreshed/updated. */
    public void setOnNewContent(Runnable onNewContent) {
        this.onNewContent = onNewContent;
    }

    public void refresh() {
        populateRepoCombo();
        updateGraphForSelectedRepo();
    }

    private void populateRepoCombo() {
        isPopulatingCombo = true;
        try {
            GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
            repositories = repoManager.getRepositories();

            repoCombo.removeAllItems();
            if (repositories.isEmpty()) {
                repoCombo.addItem("(no repositories)");
                repoCombo.setEnabled(false);
                selectedRepoPath = null;
                return;
            }

            repoCombo.setEnabled(repositories.size() > 1);

            int selectedIdx = 0;
            for (int i = 0; i < repositories.size(); i++) {
                GitRepository repo = repositories.get(i);
                repoCombo.addItem(repo.getRoot().getName());
                if (selectedRepoPath != null && selectedRepoPath.equals(repo.getRoot().getPath())) {
                    selectedIdx = i;
                }
            }

            if (selectedIdx < repositories.size()) {
                repoCombo.setSelectedIndex(selectedIdx);
                selectedRepoPath = repositories.get(selectedIdx).getRoot().getPath();
            }
        } finally {
            isPopulatingCombo = false;
        }
    }

    private void updateGraphForSelectedRepo() {
        GitFlowSettingsService settings = GitFlowSettingsService.getInstance(project);
        GitRepository repo = getSelectedRepository();

        if (repo != null) {
            List<String> localBranches = repo.getBranches().getLocalBranches().stream()
                    .map(b -> b.getName())
                    .collect(Collectors.toList());

            graphCanvas.updateData(
                    repo.getRoot().getName(),
                    settings.getMainBranch(),
                    settings.getDevelopBranch(),
                    localBranches.stream().filter(b -> b.startsWith(settings.getFeaturePrefix())).collect(Collectors.toList()),
                    localBranches.stream().filter(b -> b.startsWith(settings.getReleasePrefix())).collect(Collectors.toList()),
                    localBranches.stream().filter(b -> b.startsWith(settings.getHotfixPrefix())).collect(Collectors.toList()),
                    localBranches
            );
        } else {
            graphCanvas.updateData(null, null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        graphCanvas.updateEmptyText();
        if (onNewContent != null) {
            onNewContent.run();
        }
    }

    @Nullable
    private GitRepository getSelectedRepository() {
        if (repositories.isEmpty()) {
            return null;
        }
        if (selectedRepoPath != null) {
            for (GitRepository repo : repositories) {
                if (repo.getRoot().getPath().equals(selectedRepoPath)) {
                    return repo;
                }
            }
        }
        return repositories.get(0);
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
        private String repoName;
        private String mainBranch;
        private String developBranch;
        private List<String> features = Collections.emptyList();
        private List<String> releases = Collections.emptyList();
        private List<String> hotfixes = Collections.emptyList();
        private List<String> allLocal = Collections.emptyList();
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

        public void updateData(String repoName, String main, String develop, List<String> features, List<String> releases, List<String> hotfixes, List<String> allLocal) {
            this.repoName = repoName;
            this.mainBranch = main;
            this.developBranch = develop;
            this.features = features != null ? features : Collections.emptyList();
            this.releases = releases != null ? releases : Collections.emptyList();
            this.hotfixes = hotfixes != null ? hotfixes : Collections.emptyList();
            this.allLocal = allLocal != null ? allLocal : Collections.emptyList();

            int height = 180 + (this.features.size() + this.releases.size() + this.hotfixes.size()) * 40;
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
            int xEnd = Math.max(400, getWidth() - 50);
            int mainY = 50;
            int developY = mainY + 60;

            // Draw Repository Header if available
            if (repoName != null && !repoName.isEmpty()) {
                g2.setColor(JBColor.GRAY);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12));
                g2.drawString("Repository: " + repoName, xStart, 22);
            }

            // Draw Main (Production)
            drawBranch(g2, "Production (main)", mainBranch, xStart, xEnd, mainY, JBColor.BLUE, allLocal.contains(mainBranch));

            // Draw Develop (Integration)
            drawBranch(g2, "Integration (develop)", developBranch, xStart, xEnd, developY, JBColor.GREEN, allLocal.contains(developBranch));

            // Draw connection Main -> Develop (Develop was created from Main)
            int mainDevStartX = xStart + 15;
            int mainDevEndX = xStart + 35;

            g2.setColor(JBColor.BLUE);
            g2.fillOval(mainDevStartX - 3, mainY - 3, 6, 6);

            g2.setColor(JBColor.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mainDevStartX, mainY, mainDevEndX, developY);

            g2.setColor(JBColor.GREEN);
            g2.fillOval(mainDevEndX - 3, developY - 3, 6, 6);

            // Draw Features, Releases, Hotfixes
            int featY = developY + 60;

            // Draw Hotfixes (from Main)
            int hotfixOriginX = xStart + 55;
            for (String hot : hotfixes) {
                drawSubBranch(g2, hot, hotfixOriginX, xEnd, featY, JBColor.RED, mainY, JBColor.BLUE);
                featY += 40;
            }

            // Draw Releases (from Develop)
            int releaseOriginX = xStart + 70;
            for (String rel : releases) {
                drawSubBranch(g2, rel, releaseOriginX, xEnd, featY, JBColor.CYAN, developY, JBColor.GREEN);
                featY += 40;
            }

            // Draw Features (from Develop) - clearly separated origin on develop branch
            int featureOriginX = (releases.isEmpty() ? xStart + 75 : xStart + 95);
            for (String feat : features) {
                drawSubBranch(g2, feat, featureOriginX, xEnd, featY, JBColor.ORANGE, developY, JBColor.GREEN);
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

        private void drawSubBranch(Graphics2D g2, String name, int originX, int x2, int y, Color color, int parentY, Color parentColor) {
            // Origin node on parent branch
            g2.setColor(parentColor);
            g2.fillOval(originX - 3, parentY - 3, 6, 6);

            // Connection line from parent to sub-branch
            g2.setColor(JBColor.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
            int subBranchStartX = originX + 20;
            g2.drawLine(originX, parentY, subBranchStartX, y);

            // Sub-branch horizontal line
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(subBranchStartX, y, x2, y);

            // Node on sub-branch
            g2.fillOval(subBranchStartX + 4, y - 4, 8, 8);

            // Branch name above the line
            g2.setColor(JBColor.foreground());
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11));
            g2.drawString(name, subBranchStartX + 18, y - 6);
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
