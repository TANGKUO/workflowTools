    private String perforceChangelistId;
    public String getPerforceChangelistId() {
        return perforceChangelistId;
    }

    public void setPerforceChangelistId(String perforceChangelistId) {
        this.perforceChangelistId = perforceChangelistId;
    }

    public boolean matchesOneOf(FileChangeType... changeTypes) {
        for (FileChangeType changeType : changeTypes) {
            if (this.changeType == changeType) {
                return true;
            }
        }
        return false;
    }

            case "change":
                setPerforceChangelistId(value);
                break;
    public String  diffGitLine() {
                    throw new RuntimeException("Expected to find file mode for new file " + bFile);
                }