package org.variantsync.vevos.extraction;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.variation.diff.Time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts ground truths for all repositories in a dataset. The ground truth consists of presence
 * conditions for each file, a list of all variables, and commit metadata.
 */
public class FastPCAnalysis implements Analysis.Hooks, PCAnalysis {
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final String VARIABLES_FILE = "VARIABLES.txt";
    private static final String CODE_VARIABILITY_CSV_BEFORE = "code-variability.before.spl.csv";
    private static final String CODE_VARIABILITY_CSV_AFTER = "code-variability.after.spl.csv";
    public static int numProcessed = 0;
    private final ConcurrentHashMap<Long, ThreadBatch> threadBatches;
    private final boolean printEnabled;
    private final Path resultsRoot;

    public FastPCAnalysis(boolean printEnabled, Path resultsRoot) {
        this.printEnabled = printEnabled;
        this.resultsRoot = resultsRoot;
        this.threadBatches = new ConcurrentHashMap<>();
    }

    private record ThreadBatch(HashMap<String, GroundTruth> groundTruthMapBefore,
                    HashMap<String, GroundTruth> groundTruthMapAfter) {

    }

    @Override
    public void endCommit(Analysis analysis) {
        RevCommit commit = analysis.getCurrentCommit();

        synchronized (FastPCAnalysis.class) {
            FastPCAnalysis.numProcessed++;
            if (FastPCAnalysis.numProcessed % 1_000 == 0) {
                Logger.info("Finishing Commit ({}): {}", FastPCAnalysis.numProcessed,
                                commit.name());
            }
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
            // In this case, no changes have been analyzed and we are not interested in the commit's
            // data.
            return;
        }

        PCAnalysis.makeComplete(groundTruthBefore);
        PCAnalysis.makeComplete(groundTruthAfter);

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

        String groundTruthAsCSVBefore = groundTruthBefore.asCSVString();
        String groundTruthAsCSVAfter = groundTruthAfter.asCSVString();
        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_BEFORE),
                        groundTruthAsCSVBefore);
        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_AFTER), groundTruthAsCSVAfter);

        Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage());

        Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName)
                        .reduce((s, s2) -> s + " " + s2);
        parentIds.ifPresentOrElse(
                        s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s),
                        () -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), ""));

        synchronized (FastPCAnalysis.class) {
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

        GroundTruth groundTruthBefore = groundTruthMapBefore.computeIfAbsent(
                        analysis.getCurrentCommit().getName(),
                        commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
        GroundTruth groundTruthAfter = groundTruthMapAfter.computeIfAbsent(
                        analysis.getCurrentCommit().getName(),
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
            // Logger.debug("Node: {}", node);
            // If the file is not completely new, we consider the before case
            if (!(changeType == DiffEntry.ChangeType.ADD)) {
                PCAnalysis.analyzeNode(fileGTBefore, node, Time.BEFORE);
            }
            if (!(changeType == DiffEntry.ChangeType.DELETE)) {
                // If the file has not been deleted, we consider the after case
                PCAnalysis.analyzeNode(fileGTAfter, node, Time.AFTER);
            }
        });

        return true;
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

}
