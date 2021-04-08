package de.variantsync.subjects.extraction.util;

import net.ssehub.kernel_haven.util.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GitUtil {
    private static final Logger LOGGER = Logger.get();

    public static List<RevCommit> getCommits(File repoDir, String firstCommitId, String lastCommitId) throws IOException, GitAPIException {
        Git gitRepo = initGitForRepo(repoDir);
        LOGGER.logInfo("Retrieving commits in repo...");
        List<RevCommit> commits = new LinkedList<>();
        if (firstCommitId != null && lastCommitId != null) {
            LOGGER.logInfo("Commit range specified...filtering commits.");
            Iterable<RevCommit> commitIterable = gitRepo.log().call();
            // Filter all commits not in the specified range of commits
            boolean inRange = false;
            boolean justChanged = false;
            for (RevCommit commit : commitIterable) {
                if (commit.getName().equals(firstCommitId) || commit.getName().equals(lastCommitId)) {
                    // Invert the value upon reaching one of the boundaries
                    inRange = !inRange;
                    justChanged = true;
                }
                if (inRange || justChanged) {
                    commits.add(commit);
                }
                justChanged = false;
            }
        } else {
            LOGGER.logInfo("Considering all commits...");
            Iterable<RevCommit> commitIterable = gitRepo.log().all().call();
            // Add all commits, if not commit range was specified
            commitIterable.forEach(commits::add);
        }
        LOGGER.logInfo("" + commits.size() + " selected for analysis.");
        Collections.reverse(commits);
        return commits;
    }

    public static Git initGitForRepo(File directory) throws IOException {
        LOGGER.logInfo("Initializing git repo...");
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(directory, ".git"))
                .build();
        Git git = new Git(repository);
        LOGGER.logInfo("...done.");
        return git;
    }

    public static List<List<RevCommit>> splitCommitsIntoSubsets(List<RevCommit> commits, int numberOfThreads) {
        List<List<RevCommit>> commitSubsets = new ArrayList<>(numberOfThreads);
        if (numberOfThreads == 1) {
            commitSubsets.add(commits);
            return commitSubsets;
        }
        for (int i = 0; i < numberOfThreads; i++) {
            commitSubsets.add(new LinkedList<>());
        }
        for (int i = 0; i < commits.size(); ) {
            for (int k = 0; k < numberOfThreads; k++) {
                RevCommit nextCommit = commits.get(i);
                commitSubsets.get(k).add(nextCommit);
                i++;
                if (i >= commits.size()) {
                    break;
                }
            }
        }
        return commitSubsets;
    }
}
