package org.variantsync.vevos.extraction.analysis;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.vevos.extraction.error.MatchingException;
import org.variantsync.vevos.extraction.gt.FileGT;
import org.variantsync.vevos.extraction.gt.GroundTruth;
import org.variantsync.vevos.extraction.io.Serde;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.variantsync.vevos.extraction.gt.GroundTruth.*;

/**
 * Extracts ground truths for all repositories in a dataset. The ground truth consists of presence
 * conditions for each file, a list of all variables, and commit metadata.
 */
public class FastVariabilityAnalysis implements Analysis.Hooks, VariabilityAnalysis {
    public static int numProcessed = 0;
    private final ConcurrentHashMap<Long, ThreadBatch> threadBatches;
    private final Set<String> failedCommits;
    private final boolean printEnabled;

    private final boolean ignorePCChanges;
    private final Path resultsRoot;
    private final boolean extractCodeMatching;

    public FastVariabilityAnalysis(boolean printEnabled, Path resultsRoot, boolean ignorePCChanges,
            boolean extractCodeMatching) {
        this.printEnabled = printEnabled;
        this.resultsRoot = resultsRoot;
        this.threadBatches = new ConcurrentHashMap<>();
        this.failedCommits = ConcurrentHashMap.newKeySet();
        this.ignorePCChanges = ignorePCChanges;
        this.extractCodeMatching = extractCodeMatching;
        try {
            Files.createDirectories(resultsRoot);
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Prints the given ground truth to console.
     *
     * @param groundTruth GT to print
     * @param commitName The id of the commit for which the GT has been calculated
     */
    private static void print(GroundTruth groundTruth, String commitName) {
        System.out.println();
        System.out.printf("*****************   %s   ******************", commitName);
        System.out.println();
        for (String file : groundTruth.fileGTs().keySet()) {
            System.out.println(groundTruth.get(file));
        }
    }

    @Override
    public void onFailedParse(Analysis analysis) {
        RevCommit commit = analysis.getCurrentCommit();

        extractionFailed(commit);
    }

    private void extractionFailed(RevCommit commit) {
        synchronized (FastVariabilityAnalysis.class) {
            Logger.warn("Was not able to extract ground truth for commit " + commit.getName());
            Serde.appendText(resultsRoot.resolve(ERROR_COMMIT_FILE), commit.getName() + "\n");
            failedCommits.add(commit.getName());
        }
    }

    @Override
    public void endCommit(Analysis analysis) {
        RevCommit commit = analysis.getCurrentCommit();

        synchronized (FastVariabilityAnalysis.class) {
            FastVariabilityAnalysis.numProcessed++;
            if (FastVariabilityAnalysis.numProcessed % 1_000 == 0) {
                Logger.info("End Processing of Commit ({}): {}",
                        FastVariabilityAnalysis.numProcessed, commit.name());
            }
        }

        if (failedCommits.contains(commit.getName())) {
            Logger.warn("Skip writing ground truth for " + commit.getName());
            // Return early, if the entire commit resulted in an error
            return;
        }

        // Retrieve data being processed by the current thread
        var currentBatch = threadBatches.get(Thread.currentThread().getId());
        HashMap<String, GroundTruth> groundTruthMapBefore = currentBatch.groundTruthMapBefore;
        HashMap<String, GroundTruth> groundTruthMapAfter = currentBatch.groundTruthMapAfter;

        // Complete all new or updated file ground truths
        GroundTruth groundTruthBefore = groundTruthMapBefore.getOrDefault(commit.getName(),
                new GroundTruth(new HashMap<>(), new HashSet<>()));
        GroundTruth groundTruthAfter = groundTruthMapAfter.getOrDefault(commit.getName(),
                new GroundTruth(new HashMap<>(), new HashSet<>()));
        if (groundTruthBefore.isEmpty() && groundTruthAfter.isEmpty()) {
            // Return early and do not save any data, if the ground truths are both empty.
            // In this case, no changes have been analyzed, and we are not interested in the
            // commit's
            // data.
            synchronized (FastVariabilityAnalysis.class) {
                Logger.debug("No code changes for " + commit.getName());
                Serde.appendText(resultsRoot.resolve(EMPTY_COMMIT_FILE), commit.getName() + "\n");
                failedCommits.add(commit.getName());
            }
            return;
        }

        VariabilityAnalysis.makeComplete(groundTruthBefore);
        VariabilityAnalysis.makeComplete(groundTruthAfter);

        if (printEnabled) {
            print(groundTruthBefore, commit.getName());
            print(groundTruthAfter, commit.getName());
        }

        // Save the extracted ground truth
        Path commitSaveDir = resultsRoot.resolve("data").resolve(commit.getName());
        try {
            Files.createDirectories(commitSaveDir);
        } catch (IOException e) {
            Logger.error(e);
            throw new UncheckedIOException(e);
        }
        String variablesList = groundTruthBefore.combinedVariablesListAsString(groundTruthAfter);
        Serde.writeToFile(commitSaveDir.resolve(VARIABLES_FILE), variablesList);

        String pcAsCSVBefore = groundTruthBefore.asPcCsvString();
        String pcAsCSVAfter = groundTruthAfter.asPcCsvString();

        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_BEFORE), pcAsCSVBefore);
        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_AFTER), pcAsCSVAfter);

        if (extractCodeMatching) {
            String matchingAsCSVBefore = groundTruthBefore.asMatchingCsvString();
            String matchingAsCSVAfter = groundTruthAfter.asMatchingCsvString();

            Serde.writeToFile(commitSaveDir.resolve(CODE_MATCHING_CSV_BEFORE), matchingAsCSVBefore);
            Serde.writeToFile(commitSaveDir.resolve(CODE_MATCHING_CSV_AFTER), matchingAsCSVAfter);
        }

        Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage());

        Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName)
                .reduce((s, s2) -> s + " " + s2);
        parentIds.ifPresentOrElse(
                s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s),
                () -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), ""));

        synchronized (FastVariabilityAnalysis.class) {
            Serde.appendText(resultsRoot.resolve(SUCCESS_COMMIT_FILE), commit.getName() + "\n");
        }

    }

    @Override
    public void beginBatch(Analysis analysis) {
        // Initialize the data for the current thread
        var id = Thread.currentThread().getId();
        // Logger.info("Starting new batch for thread " + id);
        threadBatches.put(id, new ThreadBatch(new HashMap<>(), new HashMap<>()));
    }

    @Override
    public void endBatch(Analysis analysis) {
        // Clean up the data of the fully-processed batch
        var id = Thread.currentThread().getId();
        // Logger.info("Cleaning up data of batch for thread " + id);
        threadBatches.remove(id);
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount(ProposedEditClasses.Instance));
    }

    @Override
    public boolean analyzeVariationDiff(Analysis analysis) {
        // Retrieve data being processed by the current thread
        var currentBatch = threadBatches.get(Thread.currentThread().getId());
        HashMap<String, GroundTruth> groundTruthMapBefore = currentBatch.groundTruthMapBefore;
        HashMap<String, GroundTruth> groundTruthMapAfter = currentBatch.groundTruthMapAfter;

        GroundTruth groundTruthBefore =
                groundTruthMapBefore.computeIfAbsent(analysis.getCurrentCommit().getName(),
                        commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
        GroundTruth groundTruthAfter =
                groundTruthMapAfter.computeIfAbsent(analysis.getCurrentCommit().getName(),
                        commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
        // Show.diff(analysis.getCurrentVariationDiff()).showAndAwait();
        // Get the ground truth for this file
        String fileNameBefore = analysis.getCurrentPatch().getFileName(Time.BEFORE);
        String fileNameAfter = analysis.getCurrentPatch().getFileName(Time.AFTER);
        // Logger.debug("Name of processed file is %s -> %s".formatted(fileNameBefore,
        // fileNameAfter));

        DiffEntry.ChangeType changeType = analysis.getCurrentPatch().getChangeType();

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGTBefore;
        if (changeType == DiffEntry.ChangeType.ADD) {
            fileGTBefore = null;
        } else {
            fileGTBefore = (FileGT.Mutable) groundTruthBefore.computeIfAbsent(fileNameBefore,
                    k -> new FileGT.Mutable(fileNameBefore));
        }
        final FileGT.Mutable fileGTAfter;
        if (changeType == DiffEntry.ChangeType.DELETE) {
            fileGTAfter = null;
        } else {
            fileGTAfter = (FileGT.Mutable) groundTruthAfter.computeIfAbsent(fileNameAfter,
                    k -> new FileGT.Mutable(fileNameAfter));
        }

        analysis.getCurrentVariationDiff().forAll(node -> {
            try {
                // Logger.debug("Node: {}", node);
                // If the file is not completely new, we consider the before case
                if (!(changeType == DiffEntry.ChangeType.ADD)) {
                    VariabilityAnalysis.analyzeNode(fileGTBefore, node, Time.BEFORE,
                            ignorePCChanges);
                }
                if (!(changeType == DiffEntry.ChangeType.DELETE)) {
                    // If the file has not been deleted, we consider the after case
                    VariabilityAnalysis.analyzeNode(fileGTAfter, node, Time.AFTER, ignorePCChanges);
                }
            } catch (MatchingException e) {
                Logger.error("unhandled exception while analyzing {} -> {} for commit {}.",
                        fileNameBefore, fileNameAfter, analysis.getCurrentCommit().getName());
                Logger.error(e);
                extractionFailed(analysis.getCurrentCommit());
            }
        });

        return true;
    }

    private record ThreadBatch(HashMap<String, GroundTruth> groundTruthMapBefore,
            HashMap<String, GroundTruth> groundTruthMapAfter) {

    }

}
