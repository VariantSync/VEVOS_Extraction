package org.variantsync.vevos.extraction;

import org.prop4j.Node;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
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
    static void analyzeNode(FileGT.Mutable fileGT, DiffNode<DiffLinesLabel> node, Time time) throws MatchingException {
        if (time == Time.BEFORE && node.diffType == DiffType.ADD) {
            return;
        }
        if (time == Time.AFTER && node.diffType == DiffType.REM) {
            return;
        }
        Node featureMapping = node.getFeatureMapping(time).toCNF(false);
        Node presenceCondition = node.getPresenceCondition(time).toCNF(false);
        // The range of line numbers in which the artifact appears
        LineRange currentRange;
        LineRange counterpartRange;
        switch (time) {
            case BEFORE -> {
                currentRange = node.getLinesAtTime(Time.BEFORE);
                counterpartRange = node.getLinesAtTime(Time.AFTER);
            }
            case AFTER -> {
                currentRange = node.getLinesAtTime(Time.AFTER);
                counterpartRange = node.getLinesAtTime(Time.BEFORE);
            }
            default -> {
                // Because Java cannot assess statically that this case will never occur *sigh*
                throw new IllegalStateException();
            }
        }
        int fromLine = currentRange.fromInclusive();
        int toLine = currentRange.toExclusive();

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
        } else {
            // set the matching
            fileGT.setMatching(currentRange, counterpartRange);
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
