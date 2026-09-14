package br.com.gitflowhelper.statusbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.GotItTooltip;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GitFlowGuideManager {

    public static final String GOT_IT_STATUS_BAR_ID = "gitflow.statusbar.widget.guide.v2.9.0";
    public static final String GOT_IT_TOOL_WINDOW_STRIPE_ID = "gitflow.toolwindow.stripe.guide.v2.9.0";

    private GitFlowGuideManager() {
    }

    /**
     * Shows the first GotItTooltip on the status bar widget.
     * When closed/dismissed, proceeds to show the second tooltip on the tool window stripe button.
     */
    public static void checkAndShowStatusBarGotIt(@NotNull Project project, @Nullable JComponent widgetComponent) {
        if (project.isDisposed() || widgetComponent == null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            GotItTooltip tooltip = new GotItTooltip(
                    GOT_IT_STATUS_BAR_ID,
                    "This status bar widget is your starting point for Git Flow! Click here to quickly initialize Git Flow, " +
                    "create or finish Features, Releases, and Hotfixes, publish branches, or track branch sync status.",
                    project
            )
            .withHeader("Git Flow Starts Here")
            .withPosition(Balloon.Position.above)
            .withButtonLabel("Next");

            // If the status bar guide was already dismissed in an earlier session, check the stripe guide
            if (!tooltip.canShow()) {
                showToolWindowStripeGotIt(project);
                return;
            }

            AtomicBoolean nextTriggered = new AtomicBoolean(false);
            Runnable triggerNext = () -> {
                if (nextTriggered.compareAndSet(false, true)) {
                    ApplicationManager.getApplication().invokeLater(() -> showToolWindowStripeGotIt(project));
                }
            };

            tooltip.withGotItButtonAction(() -> {
                triggerNext.run();
                return kotlin.Unit.INSTANCE;
            });

            tooltip.setOnBalloonCreated(balloon -> {
                balloon.addListener(new JBPopupListener() {
                    @Override
                    public void onClosed(@NotNull LightweightWindowEvent event) {
                        triggerNext.run();
                    }
                });
                return kotlin.Unit.INSTANCE;
            });

            if (widgetComponent.isShowing() && widgetComponent.getWidth() > 0) {
                tooltip.show(widgetComponent, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
            } else {
                widgetComponent.addHierarchyListener(new HierarchyListener() {
                    @Override
                    public void hierarchyChanged(HierarchyEvent e) {
                        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && widgetComponent.isShowing()) {
                            widgetComponent.removeHierarchyListener(this);
                            if (tooltip.canShow()) {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    if (widgetComponent.isShowing() && widgetComponent.getWidth() > 0 && tooltip.canShow()) {
                                        tooltip.show(widgetComponent, (comp, balloon) -> new Point(comp.getWidth() / 2, 0));
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Shows the second GotItTooltip on the tool window stripe button / icon,
     * explaining what each tab in the tool window does.
     */
    public static void showToolWindowStripeGotIt(@NotNull Project project) {
        if (project.isDisposed()) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GitFlow");
            Disposable parentDisposable = (toolWindow != null && !toolWindow.isDisposed())
                    ? toolWindow.getDisposable() : project;

            GotItTooltip tooltip = new GotItTooltip(
                    GOT_IT_TOOL_WINDOW_STRIPE_ID,
                    "Click here to open the Git Flow tool window with all workspace tools:<br>" +
                    "• <b>Logs</b>: Real-time terminal output of all Git commands.<br>" +
                    "• <b>Issues</b>: Connects with issue trackers (GitHub, GitLab, Jira, Redmine) to branch from tasks.<br>" +
                    "• <b>Flow</b>: Visual graph of branch workflows and commits.<br>" +
                    "• <b>CI/CD</b>: Live monitoring and control of build pipelines.",
                    parentDisposable
            )
            .withHeader("Git Flow Tool Window")
            .withButtonLabel("Got It")
            .withLink("Open Tool Window", () -> {
                if (toolWindow != null && !toolWindow.isDisposed()) {
                    toolWindow.show(null);
                }
            });

            if (!tooltip.canShow()) {
                return;
            }

            JComponent stripeButton = findToolWindowStripeButton(project, "GitFlow");
            if (stripeButton != null && stripeButton.isShowing() && stripeButton.getWidth() > 0) {
                Balloon.Position pos = getBestBalloonPosition(stripeButton);
                tooltip.withPosition(pos);
                tooltip.show(stripeButton, (comp, balloon) -> getBalloonTargetPoint((JComponent) comp, pos));
            } else if (stripeButton != null) {
                stripeButton.addHierarchyListener(new HierarchyListener() {
                    @Override
                    public void hierarchyChanged(HierarchyEvent e) {
                        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && stripeButton.isShowing()) {
                            stripeButton.removeHierarchyListener(this);
                            if (tooltip.canShow()) {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    if (stripeButton.isShowing() && stripeButton.getWidth() > 0 && tooltip.canShow()) {
                                        Balloon.Position pos = getBestBalloonPosition(stripeButton);
                                        tooltip.withPosition(pos);
                                        tooltip.show(stripeButton, (comp, balloon) -> getBalloonTargetPoint((JComponent) comp, pos));
                                    }
                                });
                            }
                        }
                    }
                });
            } else {
                // Wait briefly for IDE frame / stripe bar to finish rendering
                AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                    if (!project.isDisposed()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (!project.isDisposed() && tooltip.canShow()) {
                                JComponent retryButton = findToolWindowStripeButton(project, "GitFlow");
                                if (retryButton != null && retryButton.isShowing() && retryButton.getWidth() > 0) {
                                    Balloon.Position pos = getBestBalloonPosition(retryButton);
                                    tooltip.withPosition(pos);
                                    tooltip.show(retryButton, (comp, balloon) -> getBalloonTargetPoint((JComponent) comp, pos));
                                }
                            }
                        });
                    }
                }, 800, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Recursively traverses the main IDE frame to locate the stripe button / icon for the given tool window.
     */
    public static @Nullable JComponent findToolWindowStripeButton(@NotNull Project project, @NotNull String toolWindowId) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null) return null;
        return findStripeButton(frame, toolWindowId);
    }

    private static @Nullable JComponent findStripeButton(@Nullable Container container, @NotNull String toolWindowId) {
        if (container == null) return null;

        for (Component comp : container.getComponents()) {
            if (comp == null) continue;
            String className = comp.getClass().getName();

            // 1. Classic UI Stripe / AbstractDroppableStripe
            if (className.contains("Stripe")) {
                try {
                    Method getButtonFor = comp.getClass().getMethod("getButtonFor", String.class);
                    Object buttonManager = getButtonFor.invoke(comp, toolWindowId);
                    if (buttonManager != null) {
                        Method getComponent = buttonManager.getClass().getMethod("getComponent");
                        Object btn = getComponent.invoke(buttonManager);
                        if (btn instanceof JComponent jComp) {
                            return jComp;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. SquareStripeButton or StripeButton
            if (className.contains("StripeButton")) {
                try {
                    Method getTw = comp.getClass().getMethod("getToolWindow");
                    Object tw = getTw.invoke(comp);
                    if (tw instanceof ToolWindow toolWindow && toolWindowId.equals(toolWindow.getId())) {
                        return (JComponent) comp;
                    }
                } catch (Exception ignored) {
                }

                try {
                    Field f = comp.getClass().getDeclaredField("toolWindow");
                    f.setAccessible(true);
                    Object tw = f.get(comp);
                    if (tw instanceof ToolWindow toolWindow && toolWindowId.equals(toolWindow.getId())) {
                        return (JComponent) comp;
                    }
                } catch (Exception ignored) {
                }
            }

            // 3. ActionButton (New UI left/right toolbars)
            if (comp instanceof ActionButton actionButton) {
                AnAction action = actionButton.getAction();
                if (action != null) {
                    String actionText = action.getTemplatePresentation().getText();
                    if (toolWindowId.equalsIgnoreCase(actionText)) {
                        return actionButton;
                    }
                }
                String tooltip = actionButton.getToolTipText();
                if (tooltip != null && tooltip.contains(toolWindowId)) {
                    return actionButton;
                }
            }

            // 4. AbstractButton or JComponent with matching text/tooltip
            if (comp instanceof AbstractButton btn && toolWindowId.equalsIgnoreCase(btn.getText())) {
                return btn;
            }
            if (comp instanceof JComponent jComp && className.contains("Stripe")) {
                String tooltip = jComp.getToolTipText();
                if (tooltip != null && tooltip.contains(toolWindowId)) {
                    return jComp;
                }
            }

            // Recurse into children
            if (comp instanceof Container childContainer) {
                JComponent found = findStripeButton(childContainer, toolWindowId);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Determines the best balloon position depending on where the stripe button sits on screen.
     */
    public static @NotNull Balloon.Position getBestBalloonPosition(@NotNull JComponent component) {
        if (!component.isShowing()) {
            return Balloon.Position.above;
        }
        try {
            Point loc = component.getLocationOnScreen();
            Window window = SwingUtilities.getWindowAncestor(component);
            if (window != null) {
                Point winLoc = window.getLocationOnScreen();
                int relX = loc.x - winLoc.x;
                int relY = loc.y - winLoc.y;
                int winWidth = window.getWidth();
                int winHeight = window.getHeight();

                if (relX < 150) {
                    return Balloon.Position.atRight;
                } else if (relX > winWidth - 150) {
                    return Balloon.Position.atLeft;
                } else if (relY > winHeight - 150) {
                    return Balloon.Position.above;
                }
            }
        } catch (Exception ignored) {
        }
        return Balloon.Position.above;
    }

    public static @NotNull Point getBalloonTargetPoint(@NotNull JComponent comp, @NotNull Balloon.Position position) {
        return switch (position) {
            case atRight -> new Point(comp.getWidth(), comp.getHeight() / 2);
            case atLeft -> new Point(0, comp.getHeight() / 2);
            case above -> new Point(comp.getWidth() / 2, 0);
            case below -> new Point(comp.getWidth() / 2, comp.getHeight());
        };
    }
}
