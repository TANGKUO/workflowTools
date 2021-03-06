package com.vmware;

/**
 * Common Interface for Jira issues and Bugzilla Issues
 */
public interface IssueInfo {

    public String getKey();

    public String getWebUrl();

    public String getSummary();

    public String getDescription();

    public boolean isNotFound();

    public boolean isReal();
}
