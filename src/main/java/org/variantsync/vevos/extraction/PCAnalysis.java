package org.variantsync.vevos.extraction;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.Time;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PCAnalysis implements Analysis.Hooks {
    private final Hashtable<RevCommit, GroundTruth> groundTruthMap;
    private final Set<String> observedVariables;
    private static int numProcessed = 0;

    public PCAnalysis() {
        this.groundTruthMap = new Hashtable<>();
        this.observedVariables = new HashSet<>();
    }

    private void analyzeNode(FileGT.Mutable fileGT, DiffNode node) {
        switch (node.diffType) {
            case ADD, NON -> {
                // Add artifact to the ground truth
                applyAnnotation(node, fileGT);
            }
            case REM -> {
                // Do nothing, we recalculate the PCs completely for an updated file. Thus, removed lines do not appear
                // in the updated version in any case
            }
        }
    }

    private void applyAnnotation(DiffNode node, FileGT.Mutable fileGT) {
        Node featureMapping = node.getFeatureMapping(Time.AFTER).toCNF(true);
        Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);
        // The range of line numbers in which the artifact appears
        LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
        Logger.debug("%s: Line Range: %s, Presence Condition: %s".formatted(node.diffType, rangeInFile, presenceCondition));

        observedVariables.addAll(presenceCondition.getUniqueContainedFeatures());

        for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
            if (node.isAnnotation()) {
                LineAnnotation annotation = new LineAnnotation(i, featureMapping.toString(), presenceCondition.toString(), node.nodeType.name);
                fileGT.insert(annotation);
            } else {
                // For artifacts, we grow the root mapping
                fileGT.growIfRequired(i);
            }
        }
    }

    @Override
    public void endCommit(Analysis analysis) throws Exception {
        RevCommit commit = analysis.getCurrentCommit();
        var repo = analysis.getRepository();
        Path resultFile = Path.of("results/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
        Files.createDirectories(resultFile.getParent());

        GroundTruth groundTruth = this.groundTruthMap.getOrDefault(commit, new GroundTruth(new HashMap<>(), new HashSet<>()));
        groundTruth.variables().addAll(this.observedVariables);
        // Complete all new or updated file ground truths
        for (Map.Entry<String, FileGT> entry : groundTruth.fileGTs().entrySet()) {
            if (entry.getValue() instanceof FileGT.Mutable mutable) {
                groundTruth.fileGTs().put(entry.getKey(), mutable.finishMutation());
            }
        }
        Serde.serialize(resultFile.toFile(), groundTruth);
        this.groundTruthMap.remove(commit);
        this.observedVariables.clear();
        synchronized(PCAnalysis.class) {
            PCAnalysis.numProcessed++;
            Logger.info("Finished Commit ({}): {}", PCAnalysis.numProcessed, commit.name());
        }
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        GroundTruth groundTruth = this.groundTruthMap.computeIfAbsent(analysis.getCurrentCommit(), commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
//        Show.diff(analysis.getCurrentDiffTree()).showAndAwait();
        // Get the ground truth for this file
        String fileName = analysis.getCurrentPatch().getFileName();
        Logger.debug("Name of processed file is %s".formatted(fileName));
        if (analysis.getCurrentPatch().getChangeType() == DiffEntry.ChangeType.DELETE) {
            // We set the entry to null to mark it as removed
            groundTruth.fileGTs().put(fileName, new FileGT.Removed(fileName));
            // We return early, if the file has been completely deleted
            return true;
        }

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGT = (FileGT.Mutable) groundTruth.computeIfAbsent(fileName, k -> new FileGT.Mutable(fileName));

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
            analyzeNode(fileGT, node);
        });

        return true;
    }

}
