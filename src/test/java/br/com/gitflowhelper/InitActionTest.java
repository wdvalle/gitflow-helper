package br.com.gitflowhelper;

import br.com.gitflowhelper.actions.InitAction;
import git4idea.repo.GitRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitActionTest {

    @Test
    public void testBuildMissingBranchesMessageSingleBranch() {
        InitAction action = new InitAction("Init");
        Map<GitRepository, List<String>> missing = new LinkedHashMap<>();
        missing.put(null, Collections.singletonList("main"));

        String msg = action.buildMissingBranchesMessage(missing, 1, 1);

        assertTrue(msg.contains("The following branch does not exist locally:"));
        assertTrue(msg.contains("• main"));
        assertTrue(msg.contains("Do you want to download it from the remote repository and proceed with Init?"));
        assertTrue(msg.contains("Warning: If you choose 'No', nothing will be done and the procedure will be cancelled."));
    }

    @Test
    public void testBuildMissingBranchesMessageMultipleBranches() {
        InitAction action = new InitAction("Init");
        Map<GitRepository, List<String>> missing = new LinkedHashMap<>();
        missing.put(null, List.of("main", "develop"));

        String msg = action.buildMissingBranchesMessage(missing, 2, 1);

        assertTrue(msg.contains("The following branches do not exist locally:"));
        assertTrue(msg.contains("• main"));
        assertTrue(msg.contains("• develop"));
        assertTrue(msg.contains("Do you want to download them from the remote repository and proceed with Init?"));
        assertTrue(msg.contains("Warning: If you choose 'No', nothing will be done and the procedure will be cancelled."));
    }

    @Test
    public void testFindRemoteBranchWithNullReturnsNull() {
        InitAction action = new InitAction("Init");
        assertNull(action.findRemoteBranch(null, "main"));
        assertNull(action.findRemoteBranch(null, null));
    }
}
