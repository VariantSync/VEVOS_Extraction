package org.variantsync.vevos.extraction;

import org.prop4j.Node;
import org.variantsync.diffdetective.util.Assert;
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
     * @param time   Whether we should handle the node as before or after the edit
     */
    static void analyzeNode(FileGT.Mutable fileGT, DiffNode<DiffLinesLabel> node, Time time) throws MatchingException {
        if (time == Time.BEFORE && node.diffType == DiffType.ADD) {
            return;
        }
        if (time == Time.AFTER && node.diffType == DiffType.REM) {
            return;
        }

        Time beforeTime;
        Time afterTime;
        if (node.diffType == DiffType.NON) {
            beforeTime = Time.BEFORE;
            afterTime = Time.AFTER;
        } else {
            // In case of ADD or REM we can only use either BEFORE or AFTER, because the node only exists at one time
            beforeTime = time;
            afterTime = time;
        }

        Node featureMappingBefore = node.getFeatureMapping(beforeTime).toCNF(false);
        Node presenceConditionBefore = node.getPresenceCondition(beforeTime).toCNF(false);
        Node featureMappingAfter = node.getFeatureMapping(afterTime).toCNF(false);
        Node presenceConditionAfter = node.getPresenceCondition(afterTime).toCNF(false);

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
        if (!node.isAnnotation()) {
            // set the matching
            fileGT.setMatching(currentRange, counterpartRange);
        }

        for (int lineNumber = fromLine; lineNumber < toLine; lineNumber++) {
            // Sanity check: We assume that artifact nodes are processed after annotation nodes
            if (fileGT.get(lineNumber) != null) {
                Assert.assertFalse(fileGT.get(lineNumber).nodeType().equals("Artifact"));
            }
            LineAnnotation annotation = new LineAnnotation(lineNumber,
                    new FeatureMapping(featureMappingBefore.toString()), new PresenceCondition(presenceConditionBefore.toString()),
                    new FeatureMapping(featureMappingAfter.toString()), new PresenceCondition(presenceConditionAfter.toString()),
                    node.getNodeType().name, presenceConditionBefore.getUniqueContainedFeatures());
            fileGT.insert(annotation);
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
