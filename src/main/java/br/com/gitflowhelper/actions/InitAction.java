package br.com.gitflowhelper.actions;

import br.com.gitflowhelper.dialog.InitDialog;
import br.com.gitflowhelper.git.GitException;
import br.com.gitflowhelper.git.GitExecutor;
import br.com.gitflowhelper.git.GitResult;
import br.com.gitflowhelper.settings.GitFlowSettingsService;
import br.com.gitflowhelper.util.GitFlowDescriptions;
import br.com.gitflowhelper.util.NotificationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.commands.GitCommand;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class InitAction extends BaseAction {

    public InitAction(String actionTitle) {
        super(actionTitle, GitFlowDescriptions.INIT.getValue(), AllIcons.Scope.Production);
    }

    @Override
    public void actionPerformedImpl(@NotNull AnActionEvent e) {
        new InitDialog(this, e.getProject()).show();
    }

    @Override
    public void updateImpl(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(StringUtil.isEmpty(getMainBranch(e.getProject())));
    }

    //invoked by InitDialog
    public void doOKAction(Project project, String mainField, String developField, String featureField,
                           String releaseField, String hotfixField) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            setLoading(true, project);
            try {
                init(true, project);
                NotificationUtil.showGitFlowSuccessNotification(project, "Success", "Git Flow Initialization Successful");
            } catch (GitException ex) {
                NotificationUtil.showGitFlowErrorNotification(project, "Error", ex.getGitResult().getProcessMessage());
                GitFlowSettingsService.getInstance(project).resetAndDeleteStorage();
            }
            setLoading(false, project);
        });
    }

    public List<GitResult> init(boolean pushOnFinish, Project project) {

        setProgress(0, project);

        List<GitResult> results = new ArrayList<>();
        GitExecutor executor = new GitExecutor(project);
        GitRepositoryManager repoManager = GitRepositoryManager.getInstance(project);

        String mainBranch = getMainBranch(project);
        String developBranch = getDevelopBranch(project);
        String featurePrefix = normalizePrefix(getFeaturePrefix(project));
        String releasePrefix = normalizePrefix(getReleasePrefix(project));
        String hotfixPrefix  = normalizePrefix(getHotfixPrefix(project));

        for (GitRepository repository : repoManager.getRepositories()) {

            VirtualFile root = repository.getRoot();

            // does local branches exist
            Map<String, GitLocalBranch> localBranches =
                    repository.getBranches()
                            .getLocalBranches()
                            .stream()
                            .collect(Collectors.toMap(
                                    GitLocalBranch::getName,
                                    Function.identity()
                            ));

            setProgress(1, project);

            // 1 main branch
            if (!localBranches.containsKey(mainBranch)) {
                throw new GitException(
                        "Main branch '" + mainBranch + "' does not exist in this repo." +
                                root.getPath()
                );
            }

            setProgress(2, project);

            // 2 create develop if needed
            if (!localBranches.containsKey(developBranch)) {

                results.add(
                        executor.execute(
                                root,
                                GitCommand.CHECKOUT,
                                mainBranch
                        )
                );

                results.add(
                        executor.execute(
                                root,
                                GitCommand.CHECKOUT,
                                "-b",
                                developBranch
                        )
                );
            }

            setProgress(3, project);

            // 3 go to develop
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CHECKOUT,
                            developBranch
                    )
            );

            setProgress(4, project);

            // 4 initial push  (opcional)
            if (pushOnFinish) {
                results.add(
                        executor.execute(
                                root,
                                GitCommand.PUSH,
                                "-u",
                                "origin",
                                developBranch
                        )
                );
            }

            setProgress(5, project);

            // 5 git config - gitflow settings
            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.branch.master",
                            mainBranch
                    )
            );

            setProgress(6, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.branch.develop",
                            developBranch
                    )
            );

            setProgress(7, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.feature",
                            featurePrefix
                    )
            );

            setProgress(8, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.release",
                            releasePrefix
                    )
            );

            setProgress(9, project);

            results.add(
                    executor.execute(
                            root,
                            GitCommand.CONFIG,
                            "gitflow.prefix.hotfix",
                            hotfixPrefix
                    )
            );

            // update repo state in intellij
            repository.update();
            setProgress(10, project);
        }

        return results;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
