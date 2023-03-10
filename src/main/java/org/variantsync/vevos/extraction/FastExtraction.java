package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.analysis.FilterAnalysis;
import org.variantsync.diffdetective.analysis.PreprocessingAnalysis;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.variation.diff.filter.DiffTreeFilter;
import org.variantsync.diffdetective.variation.diff.parse.DiffTreeParseOptions;
import org.variantsync.diffdetective.variation.diff.transform.CutNonEditedSubtrees;
import org.variantsync.vevos.extraction.util.PCAnalysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

public class FastExtraction {

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
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {
        var options = options(args);

        AnalysisRunner.run(options, (repo, repoOutputDir) ->
                Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir))
        );

        System.out.println();
        System.out.println("***************************************");
        System.out.println();
        //
//        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream("results/" + commit.getName() + ".gt"))) {
//            Object loaded = is.readObject();
//            if (loaded instanceof GroundTruth loadedGT) {
//                System.out.printf("loaded a ground truth for %d files%n", loadedGT.size());
//
//                for (String file : loadedGT.fileGTs().keySet()) {
//                    FileGT fileGT = loadedGT.get(file);
//                    System.out.printf("File: %s%n", file);
//
//                    for (LineAnnotation line : fileGT) {
//                        System.out.printf("%s%n", line);
//                    }
//                }
//            }
//        } catch (ClassNotFoundException e) {
//            Logger.error(e);
//            throw new RuntimeException(e);
//        }
    }

    public static AnalysisRunner.Options options(String[] args) {
        AnalysisRunner.Options defaultOptions = AnalysisRunner.Options.DEFAULT(args);

        return new AnalysisRunner.Options(
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
    }
}
