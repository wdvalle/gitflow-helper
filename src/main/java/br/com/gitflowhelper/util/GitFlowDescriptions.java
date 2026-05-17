package br.com.gitflowhelper.util;

public enum GitFlowDescriptions {

    INIT("Initializes current repo to use Git Flow (git flow init)."),
    RESET("Resets plugin configuration."),
    ABOUT("About this plugin."),
    SHOW_AS_TREE("Show all branches as a tree."),
    REPO_GROUP("Manage local and remote branches in this project."),
    REPO_ITEM("Checkout or delete this branch."),

    CHECKOUT_LOCAL("Checkout local branch "),
    CHECKOUT_REMOTE("Checkout remote branch "),

    DELETE_LOCAL("Delete local branch "),
    DELETE_REMOTE("Delete remote branch "),

    FEATURE_GROUP("Feature related commands."),
    RELEASE_GROUP("Release related commands."),
    HOTFIX_GROUP("Bugs and hotfix related commands.");

    private final String value;

    GitFlowDescriptions(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static GitFlowDescriptions fromValue(String value) {
        for (GitFlowDescriptions desc : values()) {
            if (desc.value.equalsIgnoreCase(value)) {
                return desc;
            }
        }
        throw new IllegalArgumentException("Unknown description: " + value);
    }
}
