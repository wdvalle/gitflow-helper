package br.com.gitflowhelper.util;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ActionParamsService /*implements PropertyChangeListener*/ {

    private static final ActionParamsService INSTANCE = new ActionParamsService();
    private static final Map<Project, ActionParams> paramsMap = new HashMap<Project, ActionParams>();

    private static class ActionParams {
        String branchName;
        Map<AnAction, GitRepository> repos;
        Map<AnAction, String> names = new IdentityHashMap<AnAction, String>();
    }

    private ActionParamsService() { }

    private static ActionParams getOrCreateParams(Project key) {
        ActionParams params = paramsMap.get(key);
        if (params == null) {
            params = new ActionParams();
            paramsMap.put(key, params);
        }
        return params;
    }

    public static ActionParamsService getInstance() {
        return INSTANCE;
    }

    public static void setBranchName(Project key, String branchName) {
        getOrCreateParams(key).branchName = branchName;
    }

    public static String getBranchName(Project key) {
        return getOrCreateParams(key).branchName;
    }

    public static void addRepo(Project key, AnAction action, GitRepository repo) {
        ActionParams params = getOrCreateParams(key);
        if (params.repos == null) {
            params.repos = new IdentityHashMap<AnAction, GitRepository>();
        }
        params.repos.put(action, repo);
    }
    public static GitRepository getRepo(Project key, AnAction action) {
        ActionParams params = getOrCreateParams(key);
        if (params.repos == null) {
            params.repos = new IdentityHashMap<AnAction, GitRepository>();
        }
        return params.repos.get(action);
    }

    public static void addName(Project key, AnAction action, String name) {
        getOrCreateParams(key).names.put(action, name);
    }
    public static String getName(Project key, AnAction action) {
        return getOrCreateParams(key).names.get(action);
    }

    public static void clear(Project key) {
        paramsMap.remove(key);
    }
}
