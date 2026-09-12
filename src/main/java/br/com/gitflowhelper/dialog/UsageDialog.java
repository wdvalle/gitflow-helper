package br.com.gitflowhelper.dialog;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

/**
 * Modern, comprehensive usage and feature guide dialog for GitFlow Helper.
 */
public class UsageDialog extends DialogWrapper {

    private static final String GITHUB_URL = "https://github.com/wdvalle/gitflow-helper";
    private static final String MARKETPLACE_URL = "https://plugins.jetbrains.com/plugin/30207-git-flow-helper";

    public UsageDialog(@Nullable Project project) {
        super(project);
        setTitle("GitFlow Helper — Documentation & Usage Guide");
        setResizable(true);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(JBUI.Borders.empty(14, 16, 12, 16));
        root.setPreferredSize(new Dimension(820, 600));

        // Header
        root.add(createHeaderPanel(), BorderLayout.NORTH);

        // Center tabs
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("Overview & Init", AllIcons.General.Information, createTabComponent(getOverviewHtml()));
        tabbedPane.addTab("Branch Workflows", AllIcons.Vcs.BranchNode, createTabComponent(getWorkflowsHtml()));
        tabbedPane.addTab("Safety & Guards", AllIcons.Actions.Checked, createTabComponent(getSafetyHtml()));
        tabbedPane.addTab("Tasks & Trackers", AllIcons.General.Web, createTabComponent(getTasksHtml()));
        tabbedPane.addTab("CI/CD & Tool Window", AllIcons.Toolwindows.ToolWindowRun, createTabComponent(getCicdHtml()));
        tabbedPane.addTab("Navigation & Keys", AllIcons.General.Layout, createTabComponent(getShortcutsHtml()));

        root.add(tabbedPane, BorderLayout.CENTER);

        // Footer note
        root.add(createFooterPanel(), BorderLayout.SOUTH);

        return root;
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(6));

        // Left Icon
        JLabel iconLabel = new JLabel(PluginIcons.GitFlowBig);
        iconLabel.setBorder(JBUI.Borders.emptyTop(2));
        header.add(iconLabel, BorderLayout.WEST);

        // Title and Subtitle
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        JBLabel title = new JBLabel("GitFlow Helper");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        titleRow.add(title);

        JLabel versionBadge = new JLabel(" v" + AboutDialog.PLUGIN_VERSION + " ");
        versionBadge.setFont(versionBadge.getFont().deriveFont(Font.BOLD, 11f));
        versionBadge.setForeground(JBColor.namedColor("Label.infoForeground", new JBColor(0x0969DA, 0x58A6FF)));
        versionBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(0xD0, 0xD7, 0xDE), new Color(0x3E, 0x43, 0x4A)), 1, true),
                JBUI.Borders.empty(1, 4)
        ));
        titleRow.add(versionBadge);

        titleBox.add(titleRow);
        titleBox.add(Box.createVerticalStrut(3));

        JBLabel subtitle = new JBLabel("Complete branching model automation, safety guards & task server integration");
        subtitle.setForeground(JBColor.namedColor("Label.disabledForeground", JBColor.GRAY));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        titleBox.add(subtitle);

        header.add(titleBox, BorderLayout.CENTER);

        // Right quick links
        JPanel linksPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        linksPanel.setOpaque(false);

        JButton githubBtn = new JButton("GitHub", AllIcons.Vcs.Vendors.Github);
        githubBtn.putClientProperty("JButton.buttonType", "toolBarButton");
        githubBtn.setToolTipText("Open repository in browser");
        githubBtn.addActionListener(e -> BrowserUtil.browse(GITHUB_URL));
        linksPanel.add(githubBtn);

        JButton marketplaceBtn = new JButton("Marketplace", AllIcons.Nodes.Toolbox);
        marketplaceBtn.putClientProperty("JButton.buttonType", "toolBarButton");
        marketplaceBtn.setToolTipText("Open JetBrains Marketplace page");
        marketplaceBtn.addActionListener(e -> BrowserUtil.browse(MARKETPLACE_URL));
        linksPanel.add(marketplaceBtn);

        header.add(linksPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(JBUI.Borders.emptyTop(4));

        String shortcutKeyText = SystemInfo.isMac ? "⌃⌥1..9" : "Ctrl+Alt+1..9";
        JBLabel tipLabel = new JBLabel("Tip: All actions are available from the Status Bar widget, VCS menu, or via keyboard shortcuts (" + shortcutKeyText + ").");
        tipLabel.setIcon(AllIcons.General.ContextHelp);
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.PLAIN, 11f));
        tipLabel.setForeground(JBColor.namedColor("Label.disabledForeground", JBColor.GRAY));

        footer.add(tipLabel, BorderLayout.WEST);
        return footer;
    }

    private JComponent createTabComponent(String html) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");

        HTMLEditorKit kit = HTMLEditorKitBuilder.simple();
        editorPane.setEditorKit(kit);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        editorPane.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                if (e.getURL() != null) {
                    BrowserUtil.browse(e.getURL());
                } else if (e.getDescription() != null) {
                    BrowserUtil.browse(e.getDescription());
                }
            }
        });

        editorPane.setText(wrapHtml(html));
        editorPane.setCaretPosition(0);
        editorPane.setBackground(UIUtil.getPanelBackground());

        JBScrollPane scrollPane = new JBScrollPane(editorPane);
        scrollPane.setBorder(JBUI.Borders.empty(4, 2));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private String wrapHtml(String bodyContent) {
        Color fg = UIUtil.getLabelForeground();
        Color bg = UIUtil.getPanelBackground();
        Color muted = JBColor.namedColor("Label.disabledForeground", new JBColor(0x6E7781, 0x8B949E));

        return "<html><head><style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; " +
                "  color: " + toHex(fg) + "; background-color: " + toHex(bg) + "; margin: 0; padding: 10px; font-size: 12px; line-height: 1.5; }" +
                "h2 { font-size: 15px; margin: 0 0 8px 0; padding-bottom: 4px; border-bottom: 1px solid " + getBorderHex() + "; }" +
                "h3 { font-size: 13px; margin: 6px 0 4px 0; }" +
                "p { margin: 4px 0 8px 0; }" +
                "ul { margin: 4px 0 8px 18px; padding: 0; }" +
                "li { margin-bottom: 4px; }" +
                "code { font-family: monospace; font-size: 11px; background-color: " + getCodeBgHex() + "; color: " + toHex(fg) + "; " +
                "  border: 1px solid " + getBorderHex() + "; padding: 1px 4px; }" +
                ".muted { color: " + toHex(muted) + "; }" +
                "</style></head><body>" + bodyContent + "</body></html>";
    }

    // ---------------------------------------------------------------------------------------------
    // Tab HTML Contents
    // ---------------------------------------------------------------------------------------------

    private String getOverviewHtml() {
        String defaultShortcutHint = SystemInfo.isMac ? "<code>⌃ ⌥ 1..9</code> (Control + Option)" : "<code>Ctrl + Alt + 1..9</code>";

        return card("Welcome to GitFlow Helper", getBlueHex(),
                "<p>GitFlow Helper seamlessly implements Vincent Driessen's classic <b>Git Flow</b> branching strategy " +
                        "directly within your IDE without requiring external CLI tools. All operations run through safe, " +
                        "asynchronous IntelliJ Platform Git APIs with background execution, progress tracking, and full transparency.</p>") +

                card("1. Repository Initialization (Init...)", getGreenHex(),
                        "<p>If your project or repository is not yet initialized for Git Flow, click the <b>Init...</b> action in the GitFlow menu. " +
                                "You will be prompted to configure:</p>" +
                                "<ul>" +
                                "  <li><b>Production branch:</b> Default <code>main</code> or <code>master</code>. Contains production-ready, release-tested code.</li>" +
                                "  <li><b>Development branch:</b> Default <code>develop</code>. The central integration branch for ongoing features.</li>" +
                                "  <li><b>Branch prefixes:</b> Standard prefixes for <code>feature/</code>, <code>release/</code>, and <code>hotfix/</code>.</li>" +
                                "  <li><b>Version tag prefix:</b> Prefix prepended to automated release and hotfix tags (e.g., <code>v</code> for <code>v1.0.0</code>).</li>" +
                                "</ul>" +
                                "<p class='muted'>Configuration is persisted in your project settings and automatically loaded across all Git Flow operations.</p>") +

                card("2. Multi-Repository Workspace Support", getPurpleHex(),
                        "<p>GitFlow Helper natively supports projects with multiple Git repositories:</p>" +
                                "<ul>" +
                                "  <li>Every repository in the current project is automatically detected and listed in the GitFlow popup.</li>" +
                                "  <li>You can <b>selectively enable or disable</b> Git Flow per repository with a single click on its checkbox.</li>" +
                                "  <li>Perform branch operations on any active repository independently without cross-repository interference.</li>" +
                                "</ul>") +

                card("3. Resetting Configuration (Reset)", getOrangeHex(),
                        "<p>Need to adjust your branch conventions, change prefix patterns, or re-run initialization from scratch? " +
                                "Click <b>Reset</b> in the GitFlow popup. This safely clears the stored GitFlow configuration for the project, " +
                                "allowing you to trigger <b>Init...</b> again.</p>") +

                callout("Pro-Tip", getBlueHex(),
                        "You can access all GitFlow commands via the <b>Status Bar Widget</b> (bottom right), the <b>VCS / Git menu</b>, " +
                                "or by pressing default keyboard shortcuts (" + defaultShortcutHint + ").");
    }

    private String getWorkflowsHtml() {
        return card("Feature Branches &mdash; <code>feature/*</code>", getOrangeHex(),
                "<p>Features are used to develop new enhancements, user stories, or experimental work. " +
                        "They branch off <code>develop</code> and merge back into <code>develop</code> upon completion.</p>" +
                        "<ul>" +
                        "  <li><b>Start:</b> Prompts for feature name (or links to a task server issue) and creates a new branch off <code>develop</code>.</li>" +
                        "  <li><b>Publish:</b> Pushes the local feature branch to the remote origin and sets up upstream tracking.</li>" +
                        "  <li><b>Sync (Beta):</b> Pulls and integrates the latest changes from <code>develop</code> into your working feature branch without switching branches.</li>" +
                        "  <li><b>Finish:</b> Verifies pre-finish safety checks, merges back into <code>develop</code>, squashes commits (optional), and deletes local/remote branches.</li>" +
                        "</ul>") +

                card("Release Branches &mdash; <code>release/*</code>", getBlueHex(),
                        "<p>Releases are used to stabilize, polish, test, and prepare a new production release. " +
                        "They fork from <code>develop</code> once all planned features are merged.</p>" +
                        "<ul>" +
                        "  <li><b>Start:</b> Creates a release branch from <code>develop</code> (e.g., <code>release/1.2.0</code>). Feature freeze begins.</li>" +
                        "  <li><b>Publish:</b> Pushes the release branch to remote for CI automated pipelines, documentation, and QA team testing.</li>" +
                        "  <li><b>Sync (Beta):</b> Pulls critical incoming updates from <code>develop</code> into the release branch if required.</li>" +
                        "  <li><b>Finish:</b> Merges the release into <b>both</b> <code>main</code> and <code>develop</code>, and automatically creates an annotated version tag (e.g. <code>v1.2.0</code>).</li>" +
                        "</ul>") +

                card("Hotfix Branches &mdash; <code>hotfix/*</code>", getRedHex(),
                        "<p>Hotfixes are urgent fixes for critical bugs detected in production. " +
                        "Unlike features, they branch directly off <code>main</code> to avoid including untested development code.</p>" +
                        "<ul>" +
                        "  <li><b>Start:</b> Creates a hotfix branch branched from <code>main</code> (e.g., <code>hotfix/1.2.1</code>).</li>" +
                        "  <li><b>Publish:</b> Pushes to remote origin for quick code review and CI verification.</li>" +
                        "  <li><b>Sync (Beta):</b> Keeps the hotfix branch synchronized with the latest commits on <code>main</code>.</li>" +
                        "  <li><b>Finish:</b> Merges into <b>both</b> <code>main</code> and <code>develop</code>, and automatically creates an updated patch tag (e.g. <code>v1.2.1</code>).</li>" +
                        "</ul>") +

                callout("Workflow Summary", getGreenHex(),
                        "<b>Features:</b> <code>develop</code> &rarr; <code>feature/*</code> &rarr; <code>develop</code><br>" +
                        "<b>Releases:</b> <code>develop</code> &rarr; <code>release/*</code> &rarr; <code>main</code> + <code>develop</code> + Tag<br>" +
                        "<b>Hotfixes:</b> <code>main</code> &rarr; <code>hotfix/*</code> &rarr; <code>main</code> + <code>develop</code> + Tag");
    }

    private String getSafetyHtml() {
        return card("Pre-Finish Safety Validations", getRedHex(),
                "<p>Before any branch is finished, GitFlow Helper performs rigorous automated safety checks to prevent broken states:</p>" +
                        "<ul>" +
                        "  <li><b>Uncommitted Changes Detection:</b> Scans for uncommitted files or unstaged changes. Finishing is interrupted, prompting you to commit or stash work first.</li>" +
                        "  <li><b>Unpushed Commits Warning:</b> Identifies local commits not yet pushed to the remote repository, preventing unintended history loss.</li>" +
                        "  <li><b>Out-of-Date / Behind Check (<code>isBehind</code>):</b> Checks if your working branch is behind its target base (<code>develop</code> or <code>main</code>). " +
                        "      If behind, finishing is <b>blocked</b> until you execute <b>Sync</b> to merge the base branch and resolve conflicts locally.</li>" +
                        "</ul>") +

                card("Finish Approval Workflows & Options", getBlueHex(),
                        "<p>When finishing a feature, the <b>Finish Feature</b> dialog presents three flexible approval workflows:</p>" +
                                "<ul>" +
                                "  <li><b>Integrate immediately:</b> Merges directly into the target branch locally and pushes immediately to the remote repository.</li>" +
                                "  <li><b>Create merge request (GitLab only):</b> Automatically communicates with the GitLab API to create a ready-to-review Merge Request.</li>" +
                                "  <li><b>I will create a merge/pull request:</b> Keeps the remote branch alive, allowing you to open and review a Pull/Merge Request via GitHub, Bitbucket, Azure DevOps, etc.</li>" +
                                "</ul>" +
                                "<p><b>Additional Finish Options:</b></p>" +
                                "<ul>" +
                                "  <li><b>Squash Commits:</b> Combines all individual feature commits into a single clean commit with a custom commit message or full git log summary.</li>" +
                                "  <li><b>Keep Local / Remote Branch:</b> Choose whether to preserve or automatically delete branches after finishing.</li>" +
                                "  <li><b>Close Associated Task:</b> Automatically marks the linked issue tracker task as closed/done upon finishing.</li>" +
                                "</ul>") +

                card("Protected Branch Commit Guard", getOrangeHex(),
                        "<p>GitFlow Helper includes an active <b>Commit Interceptor (CheckinHandler)</b>:</p>" +
                                "<ul>" +
                                "  <li>If you attempt to commit changes directly to protected branches (<code>main</code> or <code>develop</code>), the IDE intercepts the action.</li>" +
                                "  <li>A safety alert dialog asks for confirmation, reminding you to use a dedicated feature or hotfix branch instead.</li>" +
                                "</ul>");
    }

    private String getTasksHtml() {
        return card("Supported Issue Trackers", getPurpleHex(),
                "<p>GitFlow Helper integrates deeply with IntelliJ's <b>Task Management</b> plugin (<code>com.intellij.tasks</code>). " +
                        "It natively connects to the following task servers:</p>" +
                        "<ul>" +
                        "  <li><b>GitHub Issues</b></li>" +
                        "  <li><b>GitLab Issues</b></li>" +
                        "  <li><b>Jira Software</b></li>" +
                        "  <li><b>Redmine</b></li>" +
                        "  <li><b>JetBrains YouTrack</b></li>" +
                        "</ul>") +

                card("Task-Driven Branching", getGreenHex(),
                        "<p>Connect your issue management directly into your everyday Git Flow routine:</p>" +
                                "<ul>" +
                                "  <li><b>Show Project Tasks (Dialog):</b> Browse open tasks from configured servers with real-time search, filtering, and rich HTML detail view.</li>" +
                                "  <li><b>Issues Tool Window Tab:</b> Dedicated panel displaying assigned issues, description markdown, and comments side-by-side.</li>" +
                                "  <li><b>Direct Branch Start:</b> Click <i>Start Feature</i> or <i>Start Hotfix</i> directly from any task card. " +
                                "      Branch names are auto-formatted and sanitized from task IDs and summaries (e.g., <code>feature/JIRA-123-login-oauth</code>).</li>" +
                                "  <li><b>Automated Workflow:</b> When starting a task, GitFlow Helper can automatically assign the issue to you and transition its status to <i>In Progress</i>.</li>" +
                                "  <li><b>Open Current Task:</b> Instantly open the active task's web page in your default browser (via <code>ViewTaskAction</code>).</li>" +
                                "  <li><b>Automated Task Closing:</b> Close the associated task on the server when finishing the branch.</li>" +
                                "</ul>") +

                callout("How to Configure Tasks", getBlueHex(),
                        "Go to <b>Settings &rarr; Tools &rarr; Tasks &rarr; Servers</b> in IntelliJ to add your Jira, GitHub, GitLab, Redmine, or YouTrack credentials. " +
                                "GitFlow Helper will automatically discover and use them.");
    }

    private String getCicdHtml() {
        return card("GitFlow Tool Window (4 Interactive Tabs)", getBlueHex(),
                "<p>GitFlow Helper provides a dedicated Tool Window (accessible at the bottom or via the tool window bar) containing 4 tabs:</p>" +
                        "<ul>" +
                        "  <li><b>Logs Tab:</b> Real-time transparency log displaying all Git commands executed by the plugin, including command line parameters, stdout, and stderr. Essential for debugging and auditing.</li>" +
                        "  <li><b>Issues Tab:</b> Interactive project task browser with issue search, detailed inspector, and direct branch creation buttons.</li>" +
                        "  <li><b>Flow Tab:</b> Visual graphical diagram rendering your local branch structure (<code>main</code>, <code>develop</code>, features, releases, hotfixes) and their checkout status with color-coded nodes and legend.</li>" +
                        "  <li><b>CI/CD Tab:</b> Real-time CI/CD pipeline monitoring, live build statuses, and direct controls (Stop build, Clear history).</li>" +
                        "</ul>") +

                card("CI/CD Pipeline Monitoring & Configuration", getOrangeHex(),
                        "<p>Monitor your Continuous Integration builds without leaving the editor:</p>" +
                                "<ul>" +
                                "  <li><b>Config CI/CD... Action:</b> Configure CI/CD parameters per repository in your project.</li>" +
                                "  <li><b>Supported Platforms:</b> Currently integrates with <b>Jenkins</b> (with GitLab CI and GitHub Actions support upcoming).</li>" +
                                "  <li><b>Credentials Security:</b> Tokens and credentials are stored securely via IntelliJ's <code>PasswordSafe</code>.</li>" +
                                "  <li><b>Visual Indicator:</b> Repositories with active CI/CD configurations are highlighted in green in configuration menus.</li>" +
                                "</ul>");
    }

    private String getShortcutsHtml() {
        String introText = SystemInfo.isMac
                ? "<p>Boost your productivity with default keyboard shortcuts for all major Git Flow operations on <b>macOS</b>:</p>"
                : "<p>Boost your productivity with default keyboard shortcuts for all major Git Flow operations:</p>";

        String keymapTip = SystemInfo.isMac
                ? callout("macOS Keymap Reference", getBlueHex(),
                        "On macOS, default shortcuts use <b>Control (⌃)</b> and <b>Option (⌥)</b> modifiers.<br>" +
                        "To customize shortcuts (e.g., using <b>⌘ Command</b>), go to:<br>" +
                        "<b>Settings (Preferences) &rarr; Keymap &rarr; Plugins &rarr; GitFlow Helper</b>.")
                : callout("Keymap Customization", getBlueHex(),
                        "You can customize any shortcut to your preference at:<br>" +
                        "<b>Settings &rarr; Keymap &rarr; Plugins &rarr; GitFlow Helper</b>.");

        return card("Keyboard Shortcuts", getBlueHex(),
                introText +
                        "<table width='100%' cellpadding='6' cellspacing='0' style='border-collapse: collapse; margin-top: 8px;'>" +
                        "<tr style='background-color: " + getCardHeaderBgHex() + ";'>" +
                        "  <th align='left' style='border: 1px solid " + getBorderHex() + ";'>Action</th>" +
                        "  <th align='left' style='border: 1px solid " + getBorderHex() + ";'>Shortcut</th>" +
                        "  <th align='left' style='border: 1px solid " + getBorderHex() + ";'>Target Base</th>" +
                        "</tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Feature Start</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.FeatureStartAction", "1") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>develop</code></td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Feature Publish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.FeaturePublishAction", "2") + "</td><td style='border: 1px solid " + getBorderHex() + ";'>remote origin</td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Feature Finish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.FeatureFinishAction", "3") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>develop</code></td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Release Start</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.ReleaseStartAction", "4") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>develop</code></td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Release Publish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.ReleasePublishAction", "5") + "</td><td style='border: 1px solid " + getBorderHex() + ";'>remote origin</td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Release Finish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.ReleaseFinishAction", "6") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>main</code> + <code>develop</code></td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Hotfix Start</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.HotfixStartAction", "7") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>main</code></td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Hotfix Publish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.HotfixPublishAction", "8") + "</td><td style='border: 1px solid " + getBorderHex() + ";'>remote origin</td></tr>" +
                        "<tr><td style='border: 1px solid " + getBorderHex() + ";'><b>Hotfix Finish</b></td><td style='border: 1px solid " + getBorderHex() + ";'>" + formatShortcut("GitFlowHelper.HotfixFinishAction", "9") + "</td><td style='border: 1px solid " + getBorderHex() + ";'><code>main</code> + <code>develop</code></td></tr>" +
                        "</table>" +
                        keymapTip) +

                card("Interactive Branch Tree Explorer", getGreenHex(),
                        "<p>Explore and manage all repository branches via the <b>Show as tree...</b> action:</p>" +
                                "<ul>" +
                                "  <li><b>Categorized Tree:</b> Local and Remote branches are categorized into structured folders (<code>feature</code>, <code>release</code>, <code>hotfix</code>, base branches).</li>" +
                                "  <li><b>Speed Search:</b> Simply begin typing anywhere while the tree is visible to instantly highlight matching branch names.</li>" +
                                "  <li><b>One-Click Branch Actions:</b> Click on any branch node to quickly <b>Checkout</b> or <b>Delete</b> local and remote branches.</li>" +
                                "  <li><b>Visual Indicators:</b> Current active branch is marked with a bookmark icon; default production branches are starred.</li>" +
                                "</ul>") +

                card("Status Bar Widget & Progress Indicator", getPurpleHex(),
                        "<p>The GitFlow Status Bar widget sits on the bottom right of the IDE window:</p>" +
                                "<ul>" +
                                "  <li>Shows active branch information and GitFlow status at a glance.</li>" +
                                "  <li>Features an animated spinner and progress bar during Git operations.</li>" +
                                "  <li>Single click opens the full GitFlow action popup with speed search aid.</li>" +
                                "</ul>");
    }

    private String formatShortcut(String actionId, String keyNumber) {
        String activeShortcut = null;
        try {
            AnAction action = ActionManager.getInstance().getAction(actionId);
            if (action != null) {
                String text = KeymapUtil.getFirstKeyboardShortcutText(action);
                if (!text.isEmpty()) {
                    activeShortcut = text;
                }
            }
        } catch (Throwable ignored) {
        }

        if (SystemInfo.isMac) {
            if (activeShortcut != null) {
                if (activeShortcut.equals("⌃⌥" + keyNumber) || activeShortcut.equals("⌃ ⌥ " + keyNumber)) {
                    return "<code>" + activeShortcut + "</code> <span class='muted' style='font-size: 11px;'>(Control + Option + " + keyNumber + ")</span>";
                }
                return "<code>" + activeShortcut + "</code>";
            }
            return "<code>⌃ ⌥ " + keyNumber + "</code> <span class='muted' style='font-size: 11px;'>(Control + Option + " + keyNumber + ")</span>";
        } else {
            if (activeShortcut != null) {
                return "<code>" + activeShortcut + "</code>";
            }
            return "<code>Ctrl + Alt + " + keyNumber + "</code>";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Card & Layout Helpers
    // ---------------------------------------------------------------------------------------------

    private String card(String title, String accentHex, String content) {
        return "<table width='100%' cellpadding='8' cellspacing='0' style='margin-bottom: 10px; background-color: " + getCardBgHex() + "; border: 1px solid " + getBorderHex() + ";'>" +
                "<tr><td style='border-left: 4px solid " + accentHex + "; padding: 8px 12px;'>" +
                "<div style='font-size: 13px; font-weight: bold; color: " + accentHex + "; margin-bottom: 5px;'>" + title + "</div>" +
                content +
                "</td></tr></table>";
    }

    private String callout(String label, String accentHex, String content) {
        return "<table width='100%' cellpadding='8' cellspacing='0' style='margin-top: 8px; margin-bottom: 10px; background-color: " + getCalloutBgHex() + "; border: 1px solid " + accentHex + ";'>" +
                "<tr><td style='padding: 8px 12px;'>" +
                "<div style='font-weight: bold; color: " + accentHex + "; margin-bottom: 3px;'>&#128161; " + label + "</div>" +
                content +
                "</td></tr></table>";
    }

    // ---------------------------------------------------------------------------------------------
    // Theme Colors
    // ---------------------------------------------------------------------------------------------

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String getBorderHex() {
        return toHex(JBColor.namedColor("Component.borderColor", new JBColor(new Color(0xD0, 0xD7, 0xDE), new Color(0x3E, 0x43, 0x4A))));
    }

    private String getCardBgHex() {
        return toHex(new JBColor(new Color(0xFA, 0xFA, 0xFA), new Color(0x2B, 0x2D, 0x30)));
    }

    private String getCardHeaderBgHex() {
        return toHex(new JBColor(new Color(0xF0, 0xF2, 0xF5), new Color(0x23, 0x25, 0x27)));
    }

    private String getCodeBgHex() {
        return toHex(new JBColor(new Color(0xEA, 0xEE, 0xF2), new Color(0x1E, 0x20, 0x22)));
    }

    private String getCalloutBgHex() {
        return toHex(new JBColor(new Color(0xF0, 0xF7, 0xFF), new Color(0x1F, 0x2A, 0x37)));
    }

    private String getBlueHex() {
        return toHex(new JBColor(new Color(0x09, 0x69, 0xDA), new Color(0x58, 0xA6, 0xFF)));
    }

    private String getGreenHex() {
        return toHex(new JBColor(new Color(0x1A, 0x7F, 0x37), new Color(0x3F, 0xB9, 0x50)));
    }

    private String getOrangeHex() {
        return toHex(new JBColor(new Color(0xD9, 0x73, 0x0D), new Color(0xF0, 0xF8, 0x3E)));
    }

    private String getRedHex() {
        return toHex(new JBColor(new Color(0xCF, 0x22, 0x2E), new Color(0xF8, 0x51, 0x49)));
    }

    private String getPurpleHex() {
        return toHex(new JBColor(new Color(0x82, 0x50, 0xDF), new Color(0xBC, 0x8C, 0xFF)));
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }
}
