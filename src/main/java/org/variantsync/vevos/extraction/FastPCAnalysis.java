package org.variantsync.vevos.extraction;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.variation.diff.Time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts ground truths for all repositories in a dataset. The ground truth consists of presence conditions for each file,
 * a list of all variables, and commit metadata.
 */
public class FastPCAnalysis implements Analysis.Hooks, PCAnalysis {
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final String VARIABLES_FILE = "VARIABLES.txt";
    private static final String CODE_VARIABILITY_CSV_BEFORE = "code-variability.before.spl.csv";
    private static final String CODE_VARIABILITY_CSV_AFTER = "code-variability.after.spl.csv";
    public static int numProcessed = 0;
    private final Hashtable<String, GroundTruth> groundTruthMapBefore;
    private final Hashtable<String, GroundTruth> groundTruthMapAfter;
    private final boolean printEnabled;
    private final Path resultsRoot;

    public FastPCAnalysis(boolean printEnabled, Path resultsRoot) {
        this.groundTruthMapBefore = new Hashtable<>();
        this.groundTruthMapAfter = new Hashtable<>();
        this.printEnabled = printEnabled;
        this.resultsRoot = resultsRoot;
    }

    @Override
    public void endCommit(Analysis analysis) {
        RevCommit commit = analysis.getCurrentCommit();

        // Complete all new or updated file ground truths
        GroundTruth groundTruthBefore = this.groundTruthMapBefore.getOrDefault(commit.getName(), new GroundTruth(new HashMap<>(), new HashSet<>()));
        GroundTruth groundTruthAfter = this.groundTruthMapAfter.getOrDefault(commit.getName(), new GroundTruth(new HashMap<>(), new HashSet<>()));
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
        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_BEFORE), groundTruthAsCSVBefore);
        Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV_AFTER), groundTruthAsCSVAfter);

        Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage());

        Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
        parentIds.ifPresentOrElse(
                s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s),
                () -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), ""));

        synchronized (FastPCAnalysis.class) {
            Serde.appendText(resultsRoot.resolve(SUCCESS_COMMIT_FILE), commit.getName() + "\n");
        }

        this.groundTruthMapBefore.remove(commit.getName());
        this.groundTruthMapAfter.remove(commit.getName());

        synchronized (FastPCAnalysis.class) {
            FastPCAnalysis.numProcessed++;
            if (FastPCAnalysis.numProcessed % 1_000 == 0) {
                Logger.info("Finished Commit ({}): {}", FastPCAnalysis.numProcessed, commit.name());
            }
        }
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) {
        GroundTruth groundTruthBefore = this.groundTruthMapBefore.computeIfAbsent(analysis.getCurrentCommit().getName(), commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
        GroundTruth groundTruthAfter = this.groundTruthMapAfter.computeIfAbsent(analysis.getCurrentCommit().getName(), commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
//        Show.diff(analysis.getCurrentDiffTree()).showAndAwait();
        // Get the ground truth for this file
        String fileNameBefore = analysis.getCurrentPatch().getFileName(Time.BEFORE);
        String fileNameAfter = analysis.getCurrentPatch().getFileName(Time.AFTER);
        Logger.debug("Name of processed file is %s -> %s".formatted(fileNameBefore, fileNameAfter));

        DiffEntry.ChangeType changeType = analysis.getCurrentPatch().getChangeType();

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGTBefore;
        if (changeType == DiffEntry.ChangeType.ADD) {
            fileGTBefore = null;
        } else {
            fileGTBefore = (FileGT.Mutable) groundTruthBefore.computeIfAbsent(fileNameBefore, k -> new FileGT.Mutable(fileNameBefore));
        }
        final FileGT.Mutable fileGTAfter;
        if (changeType == DiffEntry.ChangeType.DELETE) {
            fileGTAfter = null;
        } else {
            fileGTAfter = (FileGT.Mutable) groundTruthAfter.computeIfAbsent(fileNameAfter, k -> new FileGT.Mutable(fileNameAfter));
        }

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
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
     * @param commitName  The id of the commit for which the GT has been calculated
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
