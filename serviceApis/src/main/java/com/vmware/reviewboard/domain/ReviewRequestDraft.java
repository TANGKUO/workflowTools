package com.vmware.reviewboard.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.vmware.BuildResult;
import com.vmware.IssueInfo;
import com.vmware.JobBuild;
import com.vmware.jenkins.domain.Job;
import com.vmware.jira.domain.Issue;
import com.vmware.util.CommitConfiguration;
import com.vmware.util.StringUtils;
import com.vmware.util.logging.DynamicLogger;
import com.vmware.util.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vmware.util.StringUtils.appendCsvValue;
import static com.vmware.util.StringUtils.isBlank;
import static com.vmware.util.StringUtils.isNotBlank;
import static com.vmware.util.UrlUtils.addTrailingSlash;
import static com.vmware.util.logging.LogLevel.DEBUG;

public class ReviewRequestDraft extends BaseEntity {
    @Expose(serialize = false, deserialize = false)
    private Logger log = LoggerFactory.getLogger(this.getClass());
    @Expose(serialize = false, deserialize = false)
    private DynamicLogger dynamicLog = new DynamicLogger(log);

    @Expose(serialize = false)
    public String id;
    @Expose(serialize = false, deserialize = false)
    public ReviewRequest reviewRequest;
    @Expose(serialize = false, deserialize = false)
    public String reviewRepoType;
    public String summary = "";
    public String description = "";
    @SerializedName("testing_done")
    public String testingDone = "";
    @Expose
    @SerializedName("bugs_closed")
    @JsonAdapter(StringArrayDeserializer.class)
    public String bugNumbers = "";
    @Expose
    @SerializedName("target_people")
    @JsonAdapter(LinkArrayDeserializer.class)
    public String reviewedBy = "";
    @Expose(serialize = false, deserialize = false)
    public String shipItReviewers = "";
    @JsonAdapter(LinkArrayDeserializer.class)
    public String target_groups;
    @Expose(serialize = false, deserialize = false)
    public List<JobBuild> jobBuilds = new ArrayList<>();
    @Expose(serialize = false, deserialize = false)
    public List<IssueInfo> issues = new ArrayList<>();
    public String branch = "";
    @Expose(serialize = false, deserialize = false)
    public String[] mergeToValues = new String[0];
    @Expose(serialize = false, deserialize = false)
    public String approvedBy;

    /**
     * Boolean object as review board 1.7 treats any value for isPublic as true.
     * To keep review request private, send null for isPublic
     */
    @SerializedName("public")
    public Boolean isPublic;

    @Expose(serialize = false, deserialize = false)
    public Boolean jenkinsBuildsAreSuccessful;

    @Expose(serialize = false, deserialize = false)
    public Boolean buildwebBuildsAreSuccessful;

    @Expose(serialize = false, deserialize = false)
    public String perforceChangelistId;

    public ReviewRequestDraft() {}

    public ReviewRequestDraft(final String summary, final String description, final String testingDone,
                              final String bugNumbers, final String reviewedBy, String target_groups, String branch) {
        this.summary = summary;
        this.description = description;
        this.testingDone = testingDone;
        this.bugNumbers = bugNumbers;
        this.reviewedBy = reviewedBy;
        this.target_groups = target_groups;
        this.branch = branch;
    }

    public ReviewRequestDraft(final String commitText, CommitConfiguration commitConfiguration) {
        fillValuesFromCommitText(commitText, commitConfiguration);
    }

    public static ReviewRequestDraft anEmptyDraftForPublishingAReview() {
        ReviewRequestDraft draft = new ReviewRequestDraft(null,null,null,null,null,null,null);
        draft.isPublic = true;
        return draft;
    }

    public void fillValuesFromCommitText(String commitText, CommitConfiguration commitConfiguration) {
        if (isBlank(commitText)) {
            log.warn("Text is blank, can't extract commit values!");
            return;
        }
        this.perforceChangelistId = parseSingleLineFromText(commitText, "^Change\\s+(\\d+)", "Perforce Changelist Id", DEBUG);
        if (isNotBlank(perforceChangelistId)) {
            log.debug("Matched first line of perforce changelist, id was {}", perforceChangelistId);
            commitText = commitText.substring(commitText.indexOf('\n')).trim();
        } else {
            this.perforceChangelistId = parseSingleLineFromText(commitText, "\\[git-p4:\\s+depot-paths.+?change\\s+=\\s+(\\d+)\\]", "Git P4 Changelist Id", DEBUG);
            if (isBlank(this.perforceChangelistId)) {
                this.perforceChangelistId = parseSingleLineFromText(commitText, "^\\s*Change:\\s*(\\d+)", "Git Fusion Changelist Id", DEBUG);
            }
        }
        String description = parseMultilineFromText(commitText, commitConfiguration.generateDescriptionPattern(), "Description");
        int summaryIndex = commitText.contains("\n") ? commitText.indexOf("\n") : commitText.length() - 1;
        String summary = commitText.substring(0, summaryIndex);
        description = description.length() < summary.length() + 1 ? "" : description.substring(summary.length() + 1);
        if (description.length() > 0 && description.charAt(0) == '\n') {
            description = description.substring(1);
        }
        String testingDoneSection = parseMultilineFromText(commitText, commitConfiguration.generateTestingDonePattern(), "Testing Done");

        this.id = parseSingleLineFromText(commitText, commitConfiguration.generateReviewUrlPattern(), "Review number");
        this.description = description;
        this.summary = summary;
        this.testingDone = stripJobBuildsFromTestingDone(testingDoneSection, commitConfiguration.getJenkinsJobNames());
        this.jobBuilds.clear();
        this.jobBuilds.addAll(generateJobBuildsList(testingDoneSection, buildWithResultPattern(commitConfiguration.getJenkinsJobNames())));
        this.jobBuilds.addAll(generateJobBuildsList(testingDoneSection, buildPattern(commitConfiguration.getJenkinsJobNames())));
        this.bugNumbers = parseSingleLineFromText(commitText, commitConfiguration.generateBugNumberPattern(), "Bug Number");
        this.reviewedBy = parseSingleLineFromText(commitText, commitConfiguration.generateReviewedByPattern(), "Reviewers");
        this.approvedBy = parseSingleLineFromText(commitText, commitConfiguration.generateApprovedByPattern(), "Approved by");
        this.mergeToValues = parseRepeatingSingleLineFromText(commitText, commitConfiguration.generateMergeToPattern(), "Merge To");
    }

    public boolean hasReviewNumber() {
        return StringUtils.isInteger(id);
    }

    public boolean hasBugNumber(String noBugNumberLabel) {
        return isNotBlank(bugNumbers) && !bugNumbers.equals(noBugNumberLabel);
    }

    public boolean isTrivialCommit(String trivialReviewerLabel) {
        return reviewedBy.equals(trivialReviewerLabel);
    }

    public String fullTestingDoneSectionWithoutJobResults() {
        return fullTestingDoneSection(false);
    }

    private String fullTestingDoneSection(boolean includeResults) {
        String sectionText = this.testingDone;
        for (JobBuild build : jobBuilds) {
            sectionText += "\n" + build.details(includeResults);
        }
        return sectionText;
    }

    public JobBuild getMatchingJobBuild(Job job) {
        for (JobBuild build : jobBuilds) {
            if (StringUtils.equals(job.jobDisplayName, build.buildDisplayName) && build.url.startsWith(job.url)) {
                return build;
            }
        }
        return null;
    }

    private String stripJobBuildsFromTestingDone(String testingDone, Set<String> jenkinsJobNames) {
        String updatedSection = testingDone.replaceAll(buildWithResultPattern(jenkinsJobNames), "").trim();
        updatedSection = updatedSection.replaceAll(buildPattern(jenkinsJobNames), "").trim();
        return updatedSection;
    }

    private String buildPattern(Set<String> jenkinsJobNames) {
        Set<String> namesToCheckFor = new HashSet<>(jenkinsJobNames);
        namesToCheckFor.addAll(Arrays.asList("Build", "Sandbox"));
        String namePattenText = "(" + StringUtils.join(namesToCheckFor, "|") + ")";
        return namePattenText + "\\s+(http\\S+)";
    }

    private String buildWithResultPattern(Set<String> jenkinsJobNames) {
        return buildPattern(jenkinsJobNames) + "\\s+" + BuildResult.generateResultPattern();
    }

    private List<JobBuild> generateJobBuildsList(String text, String jobUrlPattern) {
        Matcher buildMatcher = Pattern.compile(jobUrlPattern + "\\s*$", Pattern.MULTILINE).matcher(text);
        List<JobBuild> jobBuilds = new ArrayList<>();
        while (buildMatcher.find()) {
            String buildDisplayName = buildMatcher.group(1);
            String buildUrl = buildMatcher.group(2);
            BuildResult buildResult = buildMatcher.groupCount() > 2 ? BuildResult.valueOf(buildMatcher.group(3)) : null;
            jobBuilds.add(new JobBuild(buildDisplayName, buildUrl, buildResult));
        }
        return jobBuilds;
    }

    public void setIssues(List<IssueInfo> issues, String noBugNumberLabel) {
        this.issues.clear();
        this.issues.addAll(issues);

        this.bugNumbers = "";
        for (IssueInfo issue : issues) {
            if (issue == Issue.noBugNumber) {
                bugNumbers = appendCsvValue(bugNumbers, noBugNumberLabel);
            } else {
                bugNumbers = appendCsvValue(bugNumbers, issue.getKey());
            }
        }
    }

    public IssueInfo getIssueForBugNumber(String bugNumber) {
        for (IssueInfo issue : issues) {
            if (issue.getKey().equals(bugNumber)) {
                return issue;
            }
        }
        return null;
    }

    public String[] bugNumbersAsArray() {
        return bugNumbers != null ? bugNumbers.split(",") : new String[0];
    }

    public List<JobBuild> jobBuildsMatchingUrl(String url) {
        List<JobBuild> builds = new ArrayList<>();
        for (JobBuild buildToCheck : jobBuilds) {
            if (buildToCheck.containsUrl(url)) {
                builds.add(buildToCheck);
            }
        }
        return builds;
    }

    public boolean allJobBuildsMatchingUrlAreComplete(String url) {
        List<JobBuild> builds = jobBuildsMatchingUrl(url);
        for (JobBuild buildToCheck : builds) {
            if (buildToCheck.result == BuildResult.BUILDING) {
                return false;
            }
        }
        return true;
    }

    public void updateTargetGroupsIfNeeded(String[] targetGroupsArray) {
        if (this.target_groups != null) { // added from draft
            return;
        }

        Set<String> targetGroups = new HashSet<>();
        for (Link group : reviewRequest.targetGroups) {
            log.debug("Adding review board group {}", group.getTitle());
            targetGroups.add(group.getTitle());
        }

        // only add target groups array if review is not public
        if (!reviewRequest.isPublic && targetGroupsArray != null) {
            targetGroups.addAll(Arrays.asList(targetGroupsArray));
        }

        this.target_groups = StringUtils.join(targetGroups);
    }

    public void updateTestingDoneWithJobBuild(Job job, JobBuild expectedNewBuild) {
        JobBuild existingBuild = getMatchingJobBuild(job);
        if (existingBuild == null ) {
            log.debug("Appending {} to testing done", expectedNewBuild.url);
            jobBuilds.add(expectedNewBuild);
        } else {
            log.debug("Replacing existing build url {} in testing done ", existingBuild.url);
            log.debug("New build url {}", expectedNewBuild.url);
            existingBuild.url = expectedNewBuild.url;
            existingBuild.result = expectedNewBuild.result;
        }
    }

    public boolean hasReviewers() {
        return isNotBlank(reviewedBy);
    }

    /**
     * @return Whether the review request draft contains any actual data.
     */
    public boolean hasData() {
        boolean hasData = false;
        hasData = isNotBlank(summary);
        hasData = isNotBlank(description) || hasData;
        hasData = isNotBlank(testingDone) || hasData;
        hasData = !jobBuilds.isEmpty() || hasData;
        hasData = isNotBlank(bugNumbers) || hasData;
        hasData = hasReviewers() || hasData;
        hasData = hasReviewNumber() || hasData;
        return hasData;
    }

    public String toText(CommitConfiguration commitConfig) {
        return toText(commitConfig, true);
    }

    public String toText(CommitConfiguration commitConfig, boolean includeJobResults) {
        StringBuilder builder = new StringBuilder();
        builder.append(summary).append("\n\n");

        if (isNotBlank(description)) {
            builder.append(description).append("\n");
        }
        String testingDoneSection = fullTestingDoneSection(includeJobResults);
        if (isNotBlank(testingDoneSection)) {
            builder.append("\n").append(commitConfig.getTestingDoneLabel()).append(testingDoneSection);
        }
        if (isNotBlank(bugNumbers)) {
            builder.append("\n").append(commitConfig.getBugNumberLabel()).append(bugNumbers);
        }
        if (isNotBlank(reviewedBy)) {
            builder.append("\n").append(commitConfig.getReviewedByLabel()).append(reviewedBy);
        }
        if (hasReviewNumber()) {
            builder.append("\n").append(commitConfig.getReviewUrlLabel())
                    .append(addTrailingSlash(commitConfig.getReviewboardUrl())).append("r/").append(id);
        } else if (isNotBlank(id)) {
            builder.append("\n").append(commitConfig.getReviewUrlLabel()).append(id);
        }
        if (mergeToValues.length == 0 && commitConfig.getMergeToValues() != null) {
            mergeToValues = commitConfig.getMergeToValues();
        }
        for (String mergeToValue : mergeToValues) {
            builder.append("\n").append(commitConfig.getMergeToLabel()).append(mergeToValue);
        }
        if (isNotBlank(approvedBy)) {
            builder.append("\n").append(commitConfig.getApprovedByLabel()).append(approvedBy);
        }
        return builder.toString();
    }

    private String[] parseRepeatingSingleLineFromText(String text, String pattern, String description) {
        pattern = "^\\s*" + pattern; // ensure the first non whitespace on a line matches the pattern
        Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(text);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (matches.isEmpty()) {
            dynamicLog.log(DEBUG, "{} not found in commit", description);
            log.debug("Using pattern {} against text\n[{}]", pattern, text);
            return new String[0];
        }
        return matches.toArray(new String[matches.size()]);
    }

    private String parseSingleLineFromText(String text, String pattern, String description) {
        return parseSingleLineFromText(text, pattern, description, DEBUG);
    }

    private String parseSingleLineFromText(String text, String pattern, String description, LogLevel logLevel) {
        pattern = "^\\s*" + pattern; // ensure the first non whitespace on a line matches the pattern
        return parseStringFromText(text, pattern, Pattern.MULTILINE, description, logLevel);
    }

    private String parseMultilineFromText(String text, String pattern, String description) {
        return parseStringFromText(text, pattern, Pattern.DOTALL, description);
    }

    private String parseStringFromText(String text, String pattern, int patternFlags, String description) {
        return parseStringFromText(text, pattern, patternFlags, description, DEBUG);
    }

    private String parseStringFromText(String text, String pattern, int patternFlags, String description, LogLevel logLevel) {
        Matcher matcher = Pattern.compile(pattern, patternFlags).matcher(text);
        if (!matcher.find()) {
            dynamicLog.log(logLevel, "{} not found in commit", description);
            log.debug("Using pattern {} against text\n[{}]", pattern, text);
            return "";
        }
        // get first non null group match
        for (int i = 1; i <= matcher.groupCount(); i ++) {
            String groupValue = matcher.group(i);
            if (groupValue != null) {
                return groupValue.trim();
            }
        }
        throw new RuntimeException("No non null group value for text [" + text + "] with pattern [" + pattern + "]");
    }
}
