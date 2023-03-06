package org.variantsync.vevos.extraction.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class GitUtil {

    public static List<RevCommit> getCommits(File repoDir, String firstCommitId, String lastCommitId) throws IOException, GitAPIException {
        Git gitRepo = initGitForRepo(repoDir);
        Logger.info("Retrieving commits in repo...");
        List<RevCommit> commits = new LinkedList<>();
        if (firstCommitId != null) {
            ObjectId firstCommitObjId = gitRepo.getRepository().resolve(firstCommitId);
            RevWalk revWalk = new RevWalk(gitRepo.getRepository());

            // Check whether the given ids were revision tags and retrieve the associated commit if so
            try {
                firstCommitObjId = revWalk.parseTag(firstCommitObjId).getObject().getId();
                Logger.info("The first commit id " + firstCommitId + " is a revision tag. Retrieved commit id: "+ firstCommitObjId.name() +".");
            } catch (MissingObjectException | IncorrectObjectTypeException e) {
                Logger.info("First commit id: " + firstCommitId + ".");
            }

            if (lastCommitId != null) {
                ObjectId lastCommitObjId = gitRepo.getRepository().resolve(lastCommitId);
                try {
                    lastCommitObjId = revWalk.parseTag(lastCommitObjId).getObject().getId();
                    Logger.info("The second commit id " + lastCommitId + " is a revision tag. Retrieved commit id: " + lastCommitObjId.name() + ".");
                } catch (MissingObjectException | IncorrectObjectTypeException e) {
                    Logger.info("Second commit id " + lastCommitId + ".");
                }
                if (firstCommitObjId == null || lastCommitObjId == null) {
                    Logger.error("Repository does not contain the specified ids " + firstCommitId + " " + lastCommitId);
                    gitRepo.close();
                    throw new RuntimeException();
                }

                // Filter all commits not in the specified range of commits and add them to the list of commits
                Logger.info("Commit range specified...filtering commits.");
                gitRepo.log().addRange(firstCommitObjId, lastCommitObjId).call().forEach(commits::add);
            } else {
                Logger.info("Only one commit id specified, only processing one commit.");
            }

            commits.add(revWalk.parseCommit(firstCommitObjId));
        } else {
            Logger.info("Considering all commits...");
            Iterable<RevCommit> commitIterable = gitRepo.log().all().call();
            // Add all commits, if not commit range was specified
            commitIterable.forEach(commits::add);
        }
        gitRepo.close();
        Logger.info("" + commits.size() + " selected for analysis.");
        // TODO: Reconsider the order in which commits are to be processed. Without reversal, the commits are processed from newest to oldest.
        // Collections.reverse(commits);
        return commits;
    }

    public static Git initGitForRepo(File directory) throws IOException {
        Logger.info("Initializing git repo...");
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(directory, ".git"))
                .build();
        Git git = new Git(repository);
        Logger.info("...done.");
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
