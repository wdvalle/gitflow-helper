package br.com.gitflowhelper.service;

import org.jetbrains.annotations.Nullable;

public class DivergenceInfo {
    public static final DivergenceInfo EMPTY = new DivergenceInfo(false, null, null, 0);

    private final boolean isFlowBranch;
    private final String branchName;
    private final String baseBranch;
    private final int commitsBehind;

    public DivergenceInfo(boolean isFlowBranch, @Nullable String branchName, @Nullable String baseBranch, int commitsBehind) {
        this.isFlowBranch = isFlowBranch;
        this.branchName = branchName;
        this.baseBranch = baseBranch;
        this.commitsBehind = Math.max(0, commitsBehind);
    }

    public boolean isFlowBranch() {
        return isFlowBranch;
    }

    @Nullable
    public String getBranchName() {
        return branchName;
    }

    @Nullable
    public String getBaseBranch() {
        return baseBranch;
    }

    public int getCommitsBehind() {
        return commitsBehind;
    }

    public boolean hasDivergence() {
        return isFlowBranch && commitsBehind > 0;
    }

    @Override
    public String toString() {
        return "DivergenceInfo{" +
                "isFlowBranch=" + isFlowBranch +
                ", branchName='" + branchName + '\'' +
                ", baseBranch='" + baseBranch + '\'' +
                ", commitsBehind=" + commitsBehind +
                '}';
    }
}
