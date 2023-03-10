package org.variantsync.vevos.extraction.util;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.vevos.extraction.FileGT;
import org.variantsync.vevos.extraction.GroundTruth;
import org.variantsync.vevos.extraction.LineAnnotation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.function.Function;

public class PCAnalysis implements Analysis.Hooks {
    private final Hashtable<RevCommit, GroundTruth> groundTruthMap;

    public PCAnalysis() {
        this.groundTruthMap = new Hashtable<>();
    }

    private static void analyzeNode(FileGT.Mutable fileGT, DiffNode node) {
        switch (node.diffType) {
            case ADD -> {
                // Add artifact to the ground truth
                applyAnnotation(node, fileGT::insert);
            }
            case REM -> {
                // Mark artifact as removed from the ground truth
                LineRange rangeInFile = node.getLinesAtTime(Time.BEFORE);
                Logger.debug("REM: Line Range: %s".formatted(rangeInFile));
                for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
                    fileGT.markRemoved(i);
                }
                if (node.isIf()) {
                    int endIfLocation = findEndIf(node, Time.AFTER);
                    fileGT.markRemoved(endIfLocation);
                }
            }
            case NON -> {
                // The feature mapping and presence condition might have changed - we have to update the entry
                applyAnnotation(node, fileGT::update);
            }
        }
    }

    private static void applyAnnotation(DiffNode node, Function<LineAnnotation, FileGT.Mutable> function) {
        Node featureMapping = node.getFeatureMapping(Time.AFTER).toCNF(true);
        Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);
        // The range of line numbers in which the artifact appears
        LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
        Logger.debug("%s: Line Range: %s, Presence Condition: %s".formatted(node.diffType, rangeInFile, presenceCondition));

        for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
            LineAnnotation annotation = new LineAnnotation(i, featureMapping.toString(), presenceCondition.toString(), node.nodeType.name);
            function.apply(annotation);
        }

        if (node.isIf()) {
            int endIfLocation = findEndIf(node, Time.AFTER);
            if (endIfLocation > 0) {
                LineAnnotation endIfAnnotation = new LineAnnotation(endIfLocation, "", "", "endif");
                function.apply(endIfAnnotation);
            }
        }
    }

    private static int findEndIf(DiffNode node, Time time) {
        for (DiffNode child : node.getAllChildren()) {
            if (child.isAnnotation()) {
                return findEndIf(child, time);
            }
        }
        return node.getToLine().atTime(time);
    }

    @Override
    public void endCommit(Analysis analysis) throws Exception {
        RevCommit commit = analysis.getCurrentCommit();
        var repo = analysis.getRepository();
        Path resultFile = Path.of("results/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
        Files.createDirectories(resultFile.getParent());

        Logger.info("Finished Commit: {}%n", commit.name());

        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(resultFile.toFile()))) {
            os.writeObject(this.groundTruthMap.get(commit));
        } catch (IOException e) {
            Logger.error(e);
            throw e;
        }

        this.groundTruthMap.remove(commit);
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        GroundTruth groundTruth = this.groundTruthMap.computeIfAbsent(analysis.getCurrentCommit(), commit -> new GroundTruth(new HashMap<>()));
//        Show.diff(analysis.getCurrentDiffTree()).showAndAwait();
        // Get the ground truth for this file
        String fileName = analysis.getCurrentPatch().getFileName();
        Logger.debug("Name of processed file is %s".formatted(fileName));

        if (analysis.getCurrentPatch().getChangeType() == DiffEntry.ChangeType.DELETE) {
            // We set the entry to null to mark it as removed
            groundTruth.fileGTs().put(fileName, new FileGT.Removed());
            // We return early, if the file has been completely deleted
            return true;
        }

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGT = (FileGT.Mutable) groundTruth.computeIfAbsent(fileName, k -> new FileGT.Mutable());

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
            analyzeNode(fileGT, node);
        });

        return true;
    }

}
