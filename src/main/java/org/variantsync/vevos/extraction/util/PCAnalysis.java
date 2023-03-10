package org.variantsync.vevos.extraction.util;

import org.apache.commons.lang3.function.TriFunction;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.analysis.FilterAnalysis;
import org.variantsync.diffdetective.analysis.PreprocessingAnalysis;
import org.variantsync.diffdetective.analysis.StatisticsAnalysis;
import org.variantsync.diffdetective.datasets.ParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.show.Show;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.validation.EditClassValidation;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.diffdetective.variation.diff.filter.DiffTreeFilter;
import org.variantsync.diffdetective.variation.diff.transform.CutNonEditedSubtrees;
import org.variantsync.vevos.extraction.FileGT;
import org.variantsync.vevos.extraction.GroundTruth;
import org.variantsync.vevos.extraction.LineAnnotation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.List;

public class PCAnalysis implements Analysis.Hooks {
    public static final TriFunction<Repository, Path, Analysis.Hooks, Analysis> AnalysisFactory = (repo, repoOutputDir, pcAnalysis) -> new Analysis(
            "PCAnalysis",
            List.of(
//                    new PreprocessingAnalysis(new CutNonEditedSubtrees()),
//                    new FilterAnalysis(DiffTreeFilter.notEmpty()), // filters unwanted trees
                    pcAnalysis
            ),
            repo,
            repoOutputDir
    );
    private final Hashtable<RevCommit, GroundTruth> groundTruthMap;

    public PCAnalysis() {
        this.groundTruthMap = new Hashtable<>();
    }

    /**
     * Main method to start the analysis.
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {
        PCAnalysis analysis = new PCAnalysis();
        AnalysisRunner.Options defaultOptions = AnalysisRunner.Options.DEFAULT(args);
        var options = new AnalysisRunner.Options(
                defaultOptions.repositoriesDirectory(),
                defaultOptions.outputDirectory(),
                defaultOptions.datasetsFile(),
                ParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                repo -> new DiffFilter.Builder()
                        .allowMerge(false)
                        .allowAllChangeTypes()
                        .allowAllFileExtensions()
                        .build(),
                true,
                false
        );

        AnalysisRunner.run(options, (repo, repoOutputDir) ->
                Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir, analysis))
        );

        for (RevCommit commit : analysis.groundTruthMap.keySet()) {
            System.out.println("------------------");
            System.out.println();
            System.out.printf("next commit: %s%n", commit);

            GroundTruth gt = analysis.groundTruthMap.get(commit);
            System.out.printf("found a ground truth for %d files%n", gt.size());

            for (String file : gt.fileGTs().keySet()) {
                FileGT fileGT = gt.get(file);
                System.out.printf("File: %s%n", file);

                for (LineAnnotation line : fileGT) {
                    System.out.printf("%s%n", line);
                }
            }
        }
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        GroundTruth currentGT = this.groundTruthMap.computeIfAbsent(analysis.getCurrentCommit(), k -> new GroundTruth(new Hashtable<>()));
        // TODO: Do not ignore empty lines
        // TODO: Handle #endif
//        Show.diff(analysis.getCurrentDiffTree()).showAndAwait();
        // Get the ground truth for this file
        String fileName = analysis.getCurrentPatch().getFileName();
        Logger.debug("Name of processed file is %s".formatted(fileName));

        if (analysis.getCurrentPatch().getChangeType() == DiffEntry.ChangeType.DELETE) {
            // We return early, if the file has been completely deleted
            return true;
        }

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGT = (FileGT.Mutable) currentGT.computeIfAbsent(fileName, k -> new FileGT.Mutable());

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
            analyzeNode(fileGT, node);
        });

        return true;
    }

    private static void analyzeNode(FileGT.Mutable fileGT, DiffNode node) {
        switch (node.diffType) {
            case ADD -> {
                // Add artifact to the ground truth
                Node featureMapping = node.getFeatureMapping(Time.AFTER).toCNF(true);
                Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);
                // The range of line numbers in which the artifact appears
                LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
                Logger.debug("ADD: Line Range: %s, Presence Condition: %s".formatted(rangeInFile, presenceCondition));
                for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
                    LineAnnotation annotation = new LineAnnotation(i, featureMapping, presenceCondition);
                    fileGT.insert(annotation);
                }
            }
            case REM -> {
                // Mark artifact as removed from the ground truth
                LineRange rangeInFile = node.getLinesAtTime(Time.BEFORE);
                Logger.debug("REM: Line Range: %s".formatted(rangeInFile));
                for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
                    fileGT.markRemoved(i);
                }
            }
            case NON -> {
                // The feature mapping and presence condition might have changed - we have to update the entry
                Node featureMapping = node.getFeatureMapping(Time.AFTER).toCNF(true);
                Node presenceCondition = node.getPresenceCondition(Time.AFTER).toCNF(true);

                // The range of line numbers in which the artifact appears
                LineRange rangeInFile = node.getLinesAtTime(Time.AFTER);
                Logger.debug("NON: Line Range: %s, Presence Condition: %s".formatted(rangeInFile, presenceCondition));
                for (int i = rangeInFile.getFromInclusive(); i < rangeInFile.getToExclusive(); i++) {
                    LineAnnotation annotation = new LineAnnotation(i, featureMapping, presenceCondition);
                    fileGT.update(annotation);
                }
            }
        }
    }

}
