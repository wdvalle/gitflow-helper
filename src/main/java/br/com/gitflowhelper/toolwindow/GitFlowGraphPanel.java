package br.com.gitflowhelper.toolwindow;

import br.com.gitflowhelper.actions.InitAction;
import br.com.gitflowhelper.dialog.InitDialog;
import br.com.gitflowhelper.events.GitFlowSettingsListener;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitFlowGraphPanel extends JPanel {
    private static final Pattern TAG_PATTERN = Pattern.compile("tag:\\s*([^,\\)]+)");
    private static final String CURRENT_BRANCH_ARROW = "\u279C ";

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
            MessageBusConnection conn = project.getMessageBus().connect(project);
            conn.subscribe(GitFlowSettingsListener.TOPIC, this::refresh);
            conn.subscribe(GitRepository.GIT_REPO_CHANGE, (GitRepositoryChangeListener) repository -> {
                if (!project.isDisposed()) {
                    ApplicationManager.getApplication().invokeLater(this::updateGraphForSelectedRepo);
                }
            });
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

            String currentBranch = repo.getCurrentBranchName();
            String main = settings.getMainBranch();
            String develop = settings.getDevelopBranch();
            String featPrefix = settings.getFeaturePrefix();
            String relPrefix = settings.getReleasePrefix();
            String hotPrefix = settings.getHotfixPrefix();

            List<String> features = localBranches.stream().filter(b -> b.startsWith(featPrefix)).collect(Collectors.toList());
            List<String> releases = localBranches.stream().filter(b -> b.startsWith(relPrefix)).collect(Collectors.toList());
            List<String> hotfixes = localBranches.stream().filter(b -> b.startsWith(hotPrefix)).collect(Collectors.toList());

            VirtualFile root = repo.getRoot();

            graphCanvas.updateData(
                    repo.getRoot().getName(),
                    currentBranch,
                    main,
                    develop,
                    features,
                    releases,
                    hotfixes,
                    localBranches,
                    Collections.emptyMap()
            );

            // Fetch tags and divergence/sync status asynchronously in background thread
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                if (project.isDisposed()) return;
                Map<String, String> tags = new HashMap<>();

                // 1. Main: exact tag or latest reachable release tag
                String mainTag = null;
                if (main != null && !main.isEmpty()) {
                    mainTag = resolveTag(root, main, null);
                    if (mainTag != null) {
                        tags.put(main, mainTag);
                    }
                }

                // 2. Develop:
                // Show tag ONLY if develop is at the exact same point as main.
                // If develop is ahead of main, show number of commits ahead (+X commits).
                // If develop is behind main, show number of commits behind (-X commits).
                if (develop != null && !develop.isEmpty()) {
                    if (main != null && !main.isEmpty()) {
                        int aheadOfMain = countCommitsBetween(root, main, develop);
                        int behindMain = countCommitsBetween(root, develop, main);

                        if (aheadOfMain == 0 && behindMain == 0) {
                            String tag = (mainTag != null) ? mainTag : resolveTag(root, develop, null);
                            if (tag != null) {
                                tags.put(develop, tag);
                            }
                        } else if (aheadOfMain > 0) {
                            tags.put(develop, String.format("+%d commit%s", aheadOfMain, aheadOfMain > 1 ? "s" : ""));
                        } else {
                            tags.put(develop, String.format("-%d commit%s", behindMain, behindMain > 1 ? "s" : ""));
                        }
                    } else {
                        String tag = resolveTag(root, develop, null);
                        if (tag != null) {
                            tags.put(develop, tag);
                        }
                    }
                }

                // 3. Hotfixes: unchanged (release/hotfix tag/version)
                for (String hot : hotfixes) {
                    String tag = resolveTag(root, hot, hotPrefix);
                    if (tag != null) tags.put(hot, tag);
                }

                // 4. Features: show how many commits feature is behind develop (indicates sync needed)
                for (String feat : features) {
                    if (develop != null && !develop.isEmpty()) {
                        int behindDev = countCommitsBetween(root, feat, develop);
                        if (behindDev > 0) {
                            tags.put(feat, String.format("-%d commit%s", behindDev, behindDev > 1 ? "s" : ""));
                        } else {
                            tags.put(feat, "synced");
                        }
                    }
                }

                // 5. Releases: unchanged (release tag/version)
                for (String rel : releases) {
                    String tag = resolveTag(root, rel, relPrefix);
                    if (tag != null) tags.put(rel, tag);
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) {
                        graphCanvas.updateTags(tags);
                    }
                });
            });
        } else {
            graphCanvas.updateData(null, null, null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
        }
        graphCanvas.updateEmptyText();
        if (onNewContent != null) {
            onNewContent.run();
        }
    }

    private int countCommitsBetween(@NotNull VirtualFile root, @NotNull String fromBranch, @NotNull String toBranch) {
        try {
            GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REV_LIST);
            handler.addParameters("--count", fromBranch + ".." + toBranch);
            GitCommandResult result = Git.getInstance().runCommand(handler);
            if (result.success() && !result.getOutput().isEmpty()) {
                String line = result.getOutput().get(0).trim();
                return Integer.parseInt(line);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @Nullable
    private String resolveTag(@NotNull VirtualFile root, @NotNull String branchName, @Nullable String versionPrefix) {
        // 1. Exact tag on branch HEAD
        try {
            GitLineHandler handler = new GitLineHandler(project, root, GitCommand.TAG);
            handler.addParameters("--points-at", branchName);
            GitCommandResult result = Git.getInstance().runCommand(handler);
            if (result.success()) {
                for (String line : result.getOutput()) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. For release and hotfix: extract version from branch name
        if (versionPrefix != null && !versionPrefix.isEmpty() && branchName.startsWith(versionPrefix)) {
            String candidate = branchName.substring(versionPrefix.length()).trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        // 3. Fallback: most recent reachable tag in branch history
        try {
            GitLineHandler logHandler = new GitLineHandler(project, root, GitCommand.LOG);
            logHandler.addParameters("--simplify-by-decoration", "--pretty=format:%d", "-n", "15", branchName);
            GitCommandResult logResult = Git.getInstance().runCommand(logHandler);
            if (logResult.success()) {
                for (String line : logResult.getOutput()) {
                    Matcher matcher = TAG_PATTERN.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1).trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
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
        private String currentBranch;
        private String mainBranch;
        private String developBranch;
        private List<String> features = Collections.emptyList();
        private List<String> releases = Collections.emptyList();
        private List<String> hotfixes = Collections.emptyList();
        private List<String> allLocal = Collections.emptyList();
        private Map<String, String> branchTags = Collections.emptyMap();

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

        public void updateData(String repoName, String currentBranch, String main, String develop, List<String> features, List<String> releases, List<String> hotfixes, List<String> allLocal, Map<String, String> tags) {
            this.repoName = repoName;
            this.currentBranch = currentBranch;
            this.mainBranch = main;
            this.developBranch = develop;
            this.features = features != null ? features : Collections.emptyList();
            this.releases = releases != null ? releases : Collections.emptyList();
            this.hotfixes = hotfixes != null ? hotfixes : Collections.emptyList();
            this.allLocal = allLocal != null ? allLocal : Collections.emptyList();
            this.branchTags = tags != null ? tags : Collections.emptyMap();

            int height = 180 + (this.features.size() + this.releases.size() + this.hotfixes.size()) * 40;
            setPreferredSize(new Dimension(500, Math.max(300, height)));
            revalidate();
            repaint();
        }

        public void updateTags(Map<String, String> tags) {
            this.branchTags = tags != null ? tags : Collections.emptyMap();
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

            // Draw Repository Header if available
            if (repoName != null && !repoName.isEmpty()) {
                g2.setColor(JBColor.GRAY);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12));
                g2.drawString("Repository: " + repoName, xStart, 22);
            }

            // 1. Draw Main (Production)
            String mainTag = branchTags.get(mainBranch);
            boolean isMainCurrent = mainBranch != null && mainBranch.equals(currentBranch);
            drawBranch(g2, "Production (main)", mainBranch, mainTag, xStart, xEnd, mainY, JBColor.BLUE, allLocal.contains(mainBranch), isMainCurrent);

            // 2. Draw Hotfixes (from Main) - positioned below Main and above Develop
            int currentY = mainY;
            int hotfixOriginX = xStart + 60;
            for (String hot : hotfixes) {
                currentY += 40;
                String hotTag = branchTags.get(hot);
                boolean isHotCurrent = hot.equals(currentBranch);
                drawSubBranch(g2, hot, hotTag, hotfixOriginX, xEnd, currentY, JBColor.RED, mainY, JBColor.BLUE, isHotCurrent);
            }

            // 3. Draw Develop (Integration) - below Hotfixes (or 60px below Main if no hotfixes)
            int developY = hotfixes.isEmpty() ? currentY + 60 : currentY + 50;
            String devTag = branchTags.get(developBranch);
            boolean isDevCurrent = developBranch != null && developBranch.equals(currentBranch);
            drawBranch(g2, "Integration (develop)", developBranch, devTag, xStart, xEnd, developY, JBColor.GREEN, allLocal.contains(developBranch), isDevCurrent);

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

            // 4. Draw Features and Releases below Develop
            int subBranchY = developY + 20;

            // Draw Features (from Develop)
            int featureOriginX = xStart + 70;
            for (String feat : features) {
                subBranchY += 40;
                String featTag = branchTags.get(feat);
                boolean isFeatCurrent = feat.equals(currentBranch);
                drawSubBranch(g2, feat, featTag, featureOriginX, xEnd, subBranchY, JBColor.ORANGE, developY, JBColor.GREEN, isFeatCurrent);
            }

            // Draw Releases (from Develop)
            int releaseOriginX = (features.isEmpty() ? xStart + 75 : xStart + 95);
            for (String rel : releases) {
                subBranchY += 40;
                String relTag = branchTags.get(rel);
                boolean isRelCurrent = rel.equals(currentBranch);
                drawSubBranch(g2, rel, relTag, releaseOriginX, xEnd, subBranchY, JBColor.CYAN, developY, JBColor.GREEN, isRelCurrent);
            }

            // 5. Draw Legend
            int legendY = Math.max(developY + 60, subBranchY + 40);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10));
            g2.setColor(JBColor.BLUE);
            g2.drawString("■ Main", xStart, legendY);
            g2.setColor(JBColor.RED);
            g2.drawString("■ Hotfix", xStart + 70, legendY);
            g2.setColor(JBColor.GREEN);
            g2.drawString("■ Develop", xStart + 145, legendY);
            g2.setColor(JBColor.ORANGE);
            g2.drawString("■ Feature", xStart + 230, legendY);
            g2.setColor(JBColor.CYAN);
            g2.drawString("■ Release", xStart + 310, legendY);
        }

        private void drawBranch(Graphics2D g2, String label, String name, @Nullable String tag, int x1, int x2, int y, Color color, boolean exists, boolean isCurrent) {
            g2.setColor(exists ? color : JBColor.LIGHT_GRAY);
            g2.setStroke(new BasicStroke(isCurrent ? 4 : 3));
            g2.drawLine(x1, y, x2, y);

            g2.fillOval(x1 + 10, y - (isCurrent ? 6 : 5), isCurrent ? 12 : 10, isCurrent ? 12 : 10);

            // Label
            Font boldFont = g2.getFont().deriveFont(Font.BOLD);
            g2.setFont(boldFont);

            int currentX = x1;
            if (isCurrent) {
                g2.setColor(new JBColor(new Color(46, 139, 87), new Color(98, 181, 67)));
                g2.drawString(CURRENT_BRANCH_ARROW, currentX, y - 10);
                currentX += g2.getFontMetrics(boldFont).stringWidth(CURRENT_BRANCH_ARROW);
            }

            g2.setColor(JBColor.foreground());
            String title = label + ": " + name;
            g2.drawString(title, currentX, y - 10);

            if (tag != null && !tag.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(boldFont);
                int titleWidth = fm.stringWidth(title);
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11));
                if (tag.startsWith("+")) {
                    g2.setColor(new JBColor(new Color(0, 120, 215), new Color(88, 157, 246)));
                } else if (tag.startsWith("-")) {
                    g2.setColor(new JBColor(new Color(210, 60, 60), new Color(230, 80, 80)));
                } else {
                    g2.setColor(JBColor.GRAY);
                }
                g2.drawString(" (" + tag + ")", currentX + titleWidth + 4, y - 10);
            }

            if (!exists) {
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10));
                g2.setColor(JBColor.GRAY);
                g2.drawString("(not found locally)", x2 + 5, y + 5);
            }
        }

        private void drawSubBranch(Graphics2D g2, String name, @Nullable String tag, int originX, int x2, int y, Color color, int parentY, Color parentColor, boolean isCurrent) {
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
            g2.setStroke(new BasicStroke(isCurrent ? 3 : 2));
            g2.drawLine(subBranchStartX, y, x2, y);

            // Node on sub-branch
            g2.fillOval(subBranchStartX + (isCurrent ? 3 : 4), y - (isCurrent ? 5 : 4), isCurrent ? 10 : 8, isCurrent ? 10 : 8);

            // Branch name and tag/badge above the line
            Font nameFont = g2.getFont().deriveFont(isCurrent ? Font.BOLD : Font.PLAIN, 11);
            g2.setFont(nameFont);

            int textX = subBranchStartX + 18;
            if (isCurrent) {
                g2.setColor(new JBColor(new Color(46, 139, 87), new Color(98, 181, 67)));
                g2.drawString(CURRENT_BRANCH_ARROW, textX, y - 6);
                textX += g2.getFontMetrics(nameFont).stringWidth(CURRENT_BRANCH_ARROW);
            }

            g2.setColor(JBColor.foreground());
            g2.drawString(name, textX, y - 6);

            if (tag != null && !tag.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(nameFont);
                int nameWidth = fm.stringWidth(name);

                if (tag.startsWith("-")) {
                    g2.setColor(new JBColor(new Color(210, 60, 60), new Color(230, 80, 80)));
                } else if ("synced".equalsIgnoreCase(tag)) {
                    g2.setColor(new JBColor(new Color(46, 139, 87), new Color(98, 181, 67)));
                } else {
                    g2.setColor(JBColor.GRAY);
                }
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11));
                g2.drawString(" (" + tag + ")", textX + nameWidth, y - 6);
            }
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
