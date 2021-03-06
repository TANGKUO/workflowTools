package com.vmware.action.bugzilla;

import com.vmware.action.base.BaseBatchBugzillaAction;
import com.vmware.bugzilla.domain.Bug;
import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.jira.domain.Issue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.List;

@ActionDescription("Adds tracking issues for bugs in a Bugzilla named query. Bugs that already have a tracking issue are skipped.")
public class AddTrackingIssuesForQuery extends BaseBatchBugzillaAction {

    public AddTrackingIssuesForQuery(WorkflowConfig config) {
        super(config);
    }

    @Override
    public String cannotRunAction() {
        List<Bug> bugList = multiActionData.getBugsForProcessing();
        if (bugList.isEmpty()) {
            return " no bugs found for named query " + config.bugzillaQuery;
        }
        return super.cannotRunAction();
    }

    @Override
    public void process() {
        List<Bug> bugList = multiActionData.getBugsForProcessing();
        for (Bug bug : bugList) {
            String trackingIssueKey = bug.getTrackingIssueKey();
            if (trackingIssueKey != null) {
                log.info("Bug {} is already being tracked by issue {}, ignoring", bug.getKey(), trackingIssueKey);
                continue;
            }
            Issue trackingIssue = createIssueFromBug(bug);
            multiActionData.add(trackingIssue);
            log.info("\nA Jira Issue will be created in Jira Project {} to track bug {}\n{}", config.defaultJiraProject,
                    trackingIssue.matchingBugzillaNumber(config.bugzillaUrl), bug.getSummary());
        }

        if (multiActionData.noIssuesAdded()) {
            log.info("No issues added", config.bugzillaQuery);
        }
    }
}
