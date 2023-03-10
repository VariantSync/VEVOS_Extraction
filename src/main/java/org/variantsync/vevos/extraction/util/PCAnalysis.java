package org.variantsync.vevos.extraction.util;

import org.apache.commons.lang3.function.TriFunction;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.diffdetective.variation.diff.parse.DiffTreeParseOptions;
import org.variantsync.vevos.extraction.FileGT;
import org.variantsync.vevos.extraction.GroundTruth;
import org.variantsync.vevos.extraction.LineAnnotation;

import java.io.*;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Function;

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
                repo -> {
                    final PatchDiffParseOptions repoDefault = repo.getParseOptions();
                    return new PatchDiffParseOptions(
                            PatchDiffParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                            new DiffTreeParseOptions(
                                    repoDefault.diffTreeParseOptions().annotationParser(),
                                    true,
                                    false
                            )
                    );
                },
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
            try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("results/" + commit.getName() + ".gt"))) {
               os.writeObject(gt);
            } catch (IOException e) {
                Logger.error(e);
                throw e;
            }
//            System.out.printf("found a ground truth for %d files%n", gt.size());
//
//            for (String file : gt.fileGTs().keySet()) {
//                FileGT fileGT = gt.get(file);
//                System.out.printf("File: %s%n", file);
//
//                for (LineAnnotation line : fileGT) {
//                    System.out.printf("%s%n", line);
//                }
//            }
        }

        System.out.println();
        System.out.println("***************************************");
        System.out.println();
        // Try deserialization
        for (RevCommit commit : analysis.groundTruthMap.keySet()) {
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream("results/" + commit.getName() + ".gt"))) {
                Object loaded = is.readObject();
                if (loaded instanceof GroundTruth loadedGT) {
                    System.out.printf("loaded a ground truth for %d files%n", loadedGT.size());

                    for (String file : loadedGT.fileGTs().keySet()) {
                        FileGT fileGT = loadedGT.get(file);
                        System.out.printf("File: %s%n", file);

                        for (LineAnnotation line : fileGT) {
                            System.out.printf("%s%n", line);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                Logger.error(e);
                throw new RuntimeException(e);
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

    private static void handleEndIf(DiffNode node, Function<LineAnnotation, FileGT.Mutable> function) {

    }

    private static int findEndIf(DiffNode node, Time time) {
        for (DiffNode child : node.getAllChildren()) {
            if (child.isAnnotation()) {
                return findEndIf(child, time);
            }
        }
        return node.getToLine().atTime(time);
    }

}
