package org.variantsync.vevos.extraction.analysis;

import org.prop4j.Node;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.DiffType;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.vevos.extraction.error.MatchingException;
import org.variantsync.vevos.extraction.gt.*;

import java.util.Map;

public interface VariabilityAnalysis {

    /**
     * Analyzes the given node and applies its annotation to the file's ground truth
     *
     * @param fileGT          The ground truth that is modified by analyzing the node
     * @param node            The node that is to be analyzed
     * @param time            Whether we should handle the node as before or after the edit
     * @param ignorePCChanges Whether changes to only the presence condition should be ignored
     */
    static void analyzeNode(FileGT.Mutable fileGT, DiffNode<DiffLinesLabel> node, Time time, boolean ignorePCChanges) throws MatchingException {
        if (time == Time.BEFORE && node.diffType == DiffType.ADD) {
            return;
        }
        if (time == Time.AFTER && node.diffType == DiffType.REM) {
            return;
        }

        Node featureMapping;
        Node presenceCondition;

        if (node.isArtifact() && ignorePCChanges && node.diffType == DiffType.NON) {
            // If an artifact is unchanged but has a new PC, we ignore the change by assigning it the same PC before and after
            // which is the PC before the change
            featureMapping = node.getFeatureMapping(Time.BEFORE).toCNF(false);
            presenceCondition = node.getPresenceCondition(Time.BEFORE).toCNF(false);
        } else {
            featureMapping = node.getFeatureMapping(time).toCNF(false);
            presenceCondition = node.getPresenceCondition(time).toCNF(false);
        }

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
        // Also consider the #endif in case of an annotation
        toLine = (node.isAnnotation() && !node.isRoot()) ? toLine + 1 : toLine;

        // Grow the root mapping
        fileGT.growIfRequired(toLine);

        // Insert the matching for artifacts
        if (!node.isAnnotation()) {
            fileGT.setMatching(currentRange, counterpartRange);
        }

        for (int lineNumber = fromLine; lineNumber < toLine; lineNumber++) {
            LineAnnotation existingAnnotation = fileGT.get(lineNumber - 1);
            if (existingAnnotation != null && existingAnnotation.nodeType().equals("artifact")) {
                // Never overwrite artifact pcs with annotation pcs
                continue;
            }
            LineAnnotation annotation;
            if (node.isAnnotation() && ignorePCChanges) {
                // We set the PC and Mapping of all macro lines to '0', i.e., 'false
                annotation = new LineAnnotation(lineNumber,
                        new FeatureMapping("0"), new PresenceCondition("0"),
                        node.getNodeType().name, presenceCondition.getUniqueContainedFeatures());
            } else {
                annotation = new LineAnnotation(lineNumber,
                        new FeatureMapping(featureMapping.toString()), new PresenceCondition(presenceCondition.toString()),
                        node.getNodeType().name, presenceCondition.getUniqueContainedFeatures());
            }
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
