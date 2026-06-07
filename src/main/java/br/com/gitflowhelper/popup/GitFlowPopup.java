package br.com.gitflowhelper.popup;

import br.com.gitflowhelper.actions.*;
import br.com.gitflowhelper.actions.branches.DeleteLocalBranchAction;
import br.com.gitflowhelper.actions.branches.DeleteRemoteBranchAction;
import br.com.gitflowhelper.gittree.GitBranchPopupBuilder;
import br.com.gitflowhelper.util.ActionParamsService;
import br.com.gitflowhelper.actions.branches.CheckoutLocalBranchAction;
import br.com.gitflowhelper.actions.branches.CheckoutRemoteBranchAction;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.ui.awt.RelativePoint;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

public final class GitFlowPopup /*extends PropertyObserver*/ {
    private ListPopup listPopup;
    private Point local = null;

    public GitFlowPopup() {
    }

    public void show(JComponent component) {
        Project project = ActionParamsService.getProject();

        DataManager.getInstance()
            .getDataContextFromFocusAsync()
            .onSuccess(dataContext -> {

                this.listPopup = JBPopupFactory.getInstance().createActionGroupPopup(
                        "Git Flow",
                        createGroup(project),
                        dataContext,
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true
                );
                this.listPopup.setCaptionIcon(PluginIcons.GitFlow);

                this.listPopup.addListener(new JBPopupListener() {
                       @Override
                       public void beforeShown(@NotNull LightweightWindowEvent event) {
                           local = listPopup.getLocationOnScreen();
                           if (component.isShowing()) {
                               int popupHeight = listPopup.getContent().getPreferredSize().height;
                               Point componentLoc = component.getLocationOnScreen();
                               local.y = componentLoc.y - popupHeight;
                               listPopup.setLocation(local);
                           }
                       }
                   }
                );

                this.listPopup.showUnderneathOf(component);
            });
    }

    private DefaultActionGroup createGroup(Project project) {
        DefaultActionGroup group = new DefaultActionGroup();
        Long counter = GitFlowSettingsService.getInstance(project).getCounter();
        if ((counter % BaseAction.COUNTER_RESET >= BaseAction.COUNTER_RESET - 5) && (counter < 1000)) {
            group.add(new LikeAction("Enjoyng? Give a like!"));
            group.addSeparator();
        }
        GitFlowSettingsService.getInstance(project).setCounter(++counter);
        group.add(new InitAction("Init..."));
        group.addSeparator();

        group.add(new BaseAction("Show as tree...", GitFlowDescriptions.SHOW_AS_TREE.getValue(), AllIcons.General.Layout) {
            @Override
            protected void updateImpl(@NotNull AnActionEvent e) {
            }
            @Override
            public void actionPerformedImpl(@NotNull AnActionEvent e) {
                JBPopup tree = GitBranchPopupBuilder.createPopup(project);
                tree.show(new RelativePoint(local));
            }
        });
        group.addSeparator();

        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);
        for (GitRepository repository : repoManager.getRepositories()) {
            group.add(repositoryBranchGroup(repository, project));
        }

        group.addSeparator();
        group.add(flowGroup("Feature", AllIcons.Actions.AddFile, GitFlowDescriptions.FEATURE_GROUP.getValue()));
        group.add(flowGroup("Release", AllIcons.Nodes.UpFolder, GitFlowDescriptions.RELEASE_GROUP.getValue()));
        group.add(flowGroup("Hotfix", AllIcons.General.ExternalTools, GitFlowDescriptions.HOTFIX_GROUP.getValue()));
        group.addSeparator();
        group.add(new ResetAction("Reset"));
        group.add(new ShowAboutAction("About..."));
        return group;
    }

    public ListPopup getPopup() {
        return this.listPopup;
    }

    private DefaultActionGroup flowGroup(String type, Icon icon, String description) {
        DefaultActionGroup group = new DefaultActionGroup(type, description, icon);
        group.setPopup(true);
        group.add(flowAction(type, "Start"));
        group.add(flowAction(type, "Publish"));
        group.add(flowAction(type, "Finish"));
        return group;
    }

    private AnAction flowAction(String type, String action) {
        String actionClassName = type+action+"Action";
        return ActionManager.getInstance().getAction("GitFlowHelper."+actionClassName);
//        BaseAction act = ActionBuilder.createActionInstance(
//                actionClassName,
//                action);
//
//        assert act != null;
//        ActionManager.getInstance().registerAction("GitFlowHelper."+actionClassName, act);
//        if (shortcuts.get(actionClassName) != null) {
//            KeymapManager
//                    .getInstance()
//                    .getActiveKeymap().addShortcut(
//                            "GitFlowHelper."+actionClassName,
//                            shortcuts.get(actionClassName)
//                    );
//        }
//        return act;
    }

    private DefaultActionGroup repositoryBranchGroup(GitRepository repository, Project project) {
        DefaultActionGroup group = new DefaultActionGroup(
                repository.getProject().getName(),
                GitFlowDescriptions.REPO_GROUP.getValue(),
                AllIcons.Actions.ProjectDirectory);
        group.setPopup(true);

        String currentBranch = repository.getCurrentBranchName();

        //--------------------------------------------------------------------------------------------

        group.add(new AnAction("Local Branches", "", AllIcons.Actions.MenuOpen) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

            }
            @Override
            public void update(@NotNull AnActionEvent e) {
                Presentation presentation = e.getPresentation();
                presentation.setEnabled(false);
            }
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        });
        group.addSeparator();

        //--------------------------------------------------------------------------------------------

        java.util.List<GitLocalBranch> orderedLocalBranches = sortBranches(
                repository.getBranches().getLocalBranches(),
                currentBranch,
                GitLocalBranch::getName,
                project
        );

        orderedLocalBranches.forEach(branch -> {
            boolean isCurrent = branch.getName().equals(currentBranch);
            DefaultActionGroup checkoutDeleteGr = new DefaultActionGroup(
                    branch.getName().replaceAll("_", "__"),
                    GitFlowDescriptions.REPO_ITEM.getValue(),
                    (isCurrent ?
                            AllIcons.Gutter.Bookmark :
                            branch.getName().equals(GitFlowSettingsService.getInstance(ActionParamsService.getProject()).getMainBranch()) ?
                                    AllIcons.Nodes.Favorite :
                                    AllIcons.Vcs.BranchNode));
            checkoutDeleteGr.setPopup(true);
            var newCheckoutAction = new CheckoutLocalBranchAction(
                    "Checkout",
                    branch.getName()
            );
            var newDeleteAction = new DeleteLocalBranchAction(
                    "Delete",
                    branch.getName()
            );
            ActionParamsService.addName(newCheckoutAction, branch.getName());
            ActionParamsService.addName(newDeleteAction, branch.getName());
            checkoutDeleteGr.add(newCheckoutAction);
            checkoutDeleteGr.add(newDeleteAction);
            ActionParamsService.addRepo(newCheckoutAction, repository);
            ActionParamsService.addRepo(newDeleteAction, repository);
            group.add(checkoutDeleteGr);
        });

        //--------------------------------------------------------------------------------------------

        group.addSeparator();
        group.add(new AnAction("Remote Branches", "", AllIcons.Actions.MenuOpen) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

            }
            @Override
            public void update(@NotNull AnActionEvent e) {
                Presentation presentation = e.getPresentation();
                presentation.setEnabled(false);
            }
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        });
        group.addSeparator();

        //--------------------------------------------------------------------------------------------

        java.util.List<GitRemoteBranch> orderedRemoteBranches = sortBranches(
                repository.getBranches().getRemoteBranches(),
                BaseAction.REMOTE+"/"+currentBranch,
                GitRemoteBranch::getName,
                project
        );
        orderedRemoteBranches.forEach(branch -> {
            boolean isCurrent = branch.getName().equals(BaseAction.REMOTE+"/"+currentBranch);
            DefaultActionGroup checkoutDeleteGr = new DefaultActionGroup(
                    branch.getName().replaceAll("_", "__"),
                    GitFlowDescriptions.REPO_ITEM.getValue(),
                    (isCurrent ?
                            AllIcons.Gutter.Bookmark :
                            branch.getName().equals(BaseAction.REMOTE+"/"+GitFlowSettingsService.getInstance(ActionParamsService.getProject()).getMainBranch()) ?
                                    AllIcons.Nodes.Favorite :
                                    AllIcons.Vcs.BranchNode
                    ));
            checkoutDeleteGr.setPopup(true);
            var newCheckoutAction =new CheckoutRemoteBranchAction(
                    "Checkout",
                    branch.getName()
            );
            var newDeleteAction =new DeleteRemoteBranchAction(
                    "Delete",
                    branch.getName()
            );
            ActionParamsService.addName(newCheckoutAction, branch.getName());
            ActionParamsService.addName(newDeleteAction, branch.getName());
            checkoutDeleteGr.add(newCheckoutAction);
            checkoutDeleteGr.add(newDeleteAction);
            ActionParamsService.addRepo(newCheckoutAction, repository);
            ActionParamsService.addRepo(newDeleteAction, repository);
            group.add(checkoutDeleteGr);
        });

        //--------------------------------------------------------------------------------------------

        return group;
    }

    private <T> java.util.List<T> sortBranches(
            Collection<T> branches,
            String currentBranchName,
            Function<T, String> branchNameExtractor,
            Project project) {
        GitFlowSettingsService service = GitFlowSettingsService.getInstance(project);
        Map<String, T> byName = new HashMap<>();
        for (T branch : branches) {
            byName.put(branchNameExtractor.apply(branch), branch);
        }

        List<T> result = new ArrayList<>();

        // 1) current branch
//        if (currentBranchName != null && byName.containsKey(currentBranchName)) {
//            result.add(byName.remove(currentBranchName));
//        }

        // 2) main
        if (byName.containsKey(service.getMainBranch())) {
            result.add(byName.remove(service.getMainBranch()));
        }
        if (byName.containsKey(BaseAction.REMOTE + "/" + service.getMainBranch())) {
            result.add(byName.remove(BaseAction.REMOTE + "/" + service.getMainBranch()));
        }

        // 3) develop
        if (byName.containsKey(service.getDevelopBranch())) {
            result.add(byName.remove(service.getDevelopBranch()));
        }
        if (byName.containsKey(BaseAction.REMOTE + "/" + service.getDevelopBranch())) {
            result.add(byName.remove(BaseAction.REMOTE + "/" + service.getDevelopBranch()));
        }


        // 4) everything else
        byName.values().stream()
                .sorted(Comparator.comparing(branchNameExtractor, String.CASE_INSENSITIVE_ORDER))
                .forEach(result::add);

        return result;
    }

}
