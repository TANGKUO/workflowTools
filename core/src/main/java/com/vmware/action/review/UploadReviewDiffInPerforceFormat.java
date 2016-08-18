import com.vmware.scm.Perforce;
import com.vmware.scm.diff.GitDiffToPerforceConverter;
        GitDiffToPerforceConverter diffConverter = new GitDiffToPerforceConverter(perforce, git.lastSubmittedChangelistInfo()[1]);
        diff.path = diffConverter.convert(git.diff(config.parentBranch, "HEAD", supportsDiffWithRenames));
        diff.parent_diff_path = diffConverter.convert(git.diff(mergeBase, config.parentBranch, supportsDiffWithRenames));