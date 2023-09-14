package org.variantsync.vevos.extraction;

import org.prop4j.Node;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.DiffType;
import org.variantsync.diffdetective.variation.diff.Time;

import java.util.Map;

public interface PCAnalysis {

    /**
     * Analyzes the given node and applies its annotation to the file's ground truth
     *
     * @param fileGT The ground truth that is modified by analyzing the node
     * @param node   The node that is to be analyzed
     * @param time Whether we should handle the node as before or after the edit
     */
    static void analyzeNode(FileGT.Mutable fileGT, DiffNode node, Time time) {
        if (time == Time.BEFORE && node.diffType == DiffType.ADD) {
            return;
        }
        if (time == Time.AFTER && node.diffType == DiffType.REM) {
            return;
        }
        Node featureMapping = node.getFeatureMapping(time).toCNF(false);
        Node presenceCondition = node.getPresenceCondition(time).toCNF(false);
        // The range of line numbers in which the artifact appears
        LineRange rangeInFile = node.getLinesAtTime(time);
        int fromLine = rangeInFile.fromInclusive();
        int toLine = rangeInFile.toExclusive();
//        Logger.debug(() -> "%s: Line Range: %s, Presence Condition: %s".formatted(node.diffType, rangeInFile, presenceCondition));

        if (node.isAnnotation()) {
            // Mark the start and end of this annotation block
            fileGT.markBlockStart(fromLine);
            fileGT.markBlockEnd(toLine);
            // Grow the root mapping
            fileGT.growIfRequired(toLine);
        } else {
            // Grow the root mapping
            fileGT.growIfRequired(toLine);
        }

        // Insert the annotations
        if (node.isAnnotation()) {
            for (int lineNumber = fromLine; lineNumber < toLine; lineNumber++) {
                LineAnnotation annotation = new LineAnnotation(lineNumber, featureMapping.toString(), presenceCondition.toString(), node.getNodeType().name, presenceCondition.getUniqueContainedFeatures());
                fileGT.insert(annotation);
            }
        }
    }

    static void makeComplete(GroundTruth groundTruth) {
        for (Map.Entry<String, FileGT> entry : groundTruth.fileGTs().entrySet()) {
            if (entry.getValue() instanceof FileGT.Mutable mutable) {
                FileGT.Complete complete = mutable.finishMutation();
                groundTruth.variables().addAll(complete.getVariables());
                groundTruth.fileGTs().put(entry.getKey(), complete);
            }
        }
    }
}
