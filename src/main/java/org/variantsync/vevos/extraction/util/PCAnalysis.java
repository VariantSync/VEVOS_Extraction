package org.variantsync.vevos.extraction.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.analysis.FilterAnalysis;
import org.variantsync.diffdetective.analysis.PreprocessingAnalysis;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.validation.Validation;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.diffdetective.variation.diff.filter.DiffTreeFilter;
import org.variantsync.diffdetective.variation.diff.transform.CutNonEditedSubtrees;

public class PCAnalysis implements Analysis.Hooks {
    // This is only needed for the `MarlinDebug` test.
    public static final BiFunction<Repository, Path, Analysis> AnalysisFactory = (repo, repoOutputDir) -> new Analysis(
            "PCAnalysis",
            List.of(
                    new PreprocessingAnalysis(new CutNonEditedSubtrees()),
                    new FilterAnalysis(DiffTreeFilter.notEmpty()), // filters unwanted trees
                    new PCAnalysis()
            ),
            repo,
            repoOutputDir
    );

    /**
     * Main method to start the analysis.
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {
//        setupLogger(Level.INFO);
//        setupLogger(Level.DEBUG);

        Validation.run(args, (repo, repoOutputDir) ->
                Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir))
        );
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        analysis.getCurrentDiffTree().forAll(node -> {
            if (node.isArtifact()) {

                // TODO: How to map artifact-centered traces to line number-centered traces?
                // We have to somehow track blocks of presence conditions

                switch (node.diffType) {
                    case ADD -> {
                        // Add artifact to the ground truth

                        // We are only concerned with the effect of a change, as the BEFORE state should already have been tracked
                        Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);
                        // The range of line numbers in which the artifact appears
                        LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
                        Logger.info("ADD: Line Range: %s, Presence Condition: %s".formatted(rangeInFile, presenceCondition));
                    }
                    case REM -> {
                        // Remove artifact from the ground truth

                        // We are only concerned with the effect of a change, as the BEFORE state should already have been tracked
                        Node presenceCondition = node.getPresenceCondition(Time.BEFORE).toCNF(true);

                        // The range of line numbers in which the artifact appears
                        LineRange rangeInFile = node.getLinesAtTime(Time.BEFORE);
                        Logger.info("REM: Line Range: %s, Presence Condition: %s".formatted(rangeInFile, presenceCondition));
                    }
                    case NON -> {
                        // The presence condition might have changed, we have to update it

                        // We are only concerned with the effect of a change, as the BEFORE state should already have been tracked
                        Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);

                        // The range of line numbers in which the artifact appears
                        LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
                        Logger.info("NON: Line Range: %s, Presence Condition: %s".formatted(rangeInFile, presenceCondition));
                    }
                }
//                analysis.get(EditClassCount.KEY).reportOccurrenceFor(
//                        ProposedEditClasses.Instance.match(node),
//                        analysis.getCurrentCommitDiff()
//                );
            }
        });

        return true;
    }
}
