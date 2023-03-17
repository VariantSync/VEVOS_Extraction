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

/**
 * Extracts ground truths for all repositories in a dataset. The ground truth consists of presence conditions for each file,
 * a list of all variables, and commit metadata.
 */
public class PCAnalysis implements Analysis.Hooks {
    public static int numProcessed = 0;
    private final Hashtable<RevCommit, GroundTruth> groundTruthMap;
    private final Set<String> observedVariables;

    public PCAnalysis() {
        this.groundTruthMap = new Hashtable<>();
        this.observedVariables = new HashSet<>();
    }

    /**
     * Analyzes the given node and applies its annotation to the file's ground truth
     *
     * @param fileGT The ground truth that is modified by analyzing the node
     * @param node   The node that is to be analyzed
     */
    private void analyzeNode(FileGT.Mutable fileGT, DiffNode node) {
        switch (node.diffType) {
            case ADD, NON -> {
                Node featureMapping = node.getFeatureMapping(Time.AFTER).toCNF(false);
                Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(false);
                // The range of line numbers in which the artifact appears
                LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
                Logger.debug("%s: Line Range: %s, Presence Condition: %s".formatted(node.diffType, rangeInFile, presenceCondition));
                observedVariables.addAll(presenceCondition.getUniqueContainedFeatures());

                // Grow the root mapping
                List<DiffNode.Label.Line> diffLines = node.getLabel().diffLines();
                if (diffLines.size() > 0) {
                    DiffNode.Label.Line lastLine = diffLines.get(diffLines.size() - 1);
                    fileGT.growIfRequired(lastLine.lineNumber().afterEdit());
                }

                // Insert the annotations
                if (node.isAnnotation()) {
                    for (int lineNumber = rangeInFile.getFromInclusive(); lineNumber < rangeInFile.getToExclusive(); lineNumber++) {
                        LineAnnotation annotation = new LineAnnotation(lineNumber, featureMapping.toString(), presenceCondition.toString(), node.nodeType.name);
                        fileGT.insert(annotation);
                    }
                }
            }
            case REM -> {
                // Do nothing, we recalculate the PCs completely for an updated file. Thus, removed lines do not appear
                // in the updated version in any case
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
        synchronized (PCAnalysis.class) {
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
        String fileNameBefore = analysis.getCurrentPatch().getFileName(Time.BEFORE);
        String fileNameAfter = analysis.getCurrentPatch().getFileName(Time.AFTER);
        Logger.debug("Name of processed file is %s -> %s".formatted(fileNameBefore, fileNameAfter));
        DiffEntry.ChangeType changeType = analysis.getCurrentPatch().getChangeType();
        if (changeType == DiffEntry.ChangeType.DELETE || (changeType != DiffEntry.ChangeType.ADD && !fileNameBefore.equals(fileNameAfter))) {
            // We set the entry of the old name as removed if the file was deleted or the name changed without the file being added as new
            groundTruth.fileGTs().put(fileNameBefore, new FileGT.Removed(fileNameBefore));
        }

        if (analysis.getCurrentPatch().getChangeType() == DiffEntry.ChangeType.DELETE) {
            // We return early, if the file has been completely deleted
            return true;
        }

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGT = (FileGT.Mutable) groundTruth.computeIfAbsent(fileNameAfter, k -> new FileGT.Mutable(fileNameAfter));

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
            analyzeNode(fileGT, node);
        });

        return true;
    }

}
