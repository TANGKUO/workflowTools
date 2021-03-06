package com.vmware.action.git;

import com.vmware.action.base.BaseCommitAction;
import com.vmware.action.base.BaseCommitAmendAction;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.util.logging.LogLevel;

@ActionDescription("Performs a git commit --amend --all without modifying any part of the commit message. Uses the existing commit message.")
public class AmendCommitFilesOnly extends BaseCommitAmendAction {

    public AmendCommitFilesOnly(WorkflowConfig config) {
        super(config, true, false);
    }

    @Override // always run
    public String cannotRunAction() {
        return null;
    }

    @Override
    protected void commitUsingGit(String description) {
        String existingHeadRef = git.revParse("head");
        git.amendCommitWithAllFileChanges(git.lastCommitText(true));
        git.updateGitChangesetTagsMatchingRevision(existingHeadRef, LogLevel.INFO);
    }

}
