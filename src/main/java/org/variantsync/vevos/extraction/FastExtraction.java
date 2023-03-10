package org.variantsync.vevos.extraction;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

public class FastExtraction {

    public static final BiFunction<Repository, Path, Analysis> AnalysisFactory = (repo, repoOutputDir) -> new Analysis(
            "PCAnalysis",
            List.of(
//                    new PreprocessingAnalysis(new CutNonEditedSubtrees()),
//                    new FilterAnalysis(DiffTreeFilter.notEmpty()), // filters unwanted trees
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

        boolean print = false;
        AnalysisRunner.run(options, (repo, repoOutputDir) -> {
                    Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir));

                    ArrayList<RevCommit> commits = new ArrayList<>();
                    try (Git gitRepo = repo.getGitRepo().run()) {
                        gitRepo.log().call().forEach(commits::add);
                        Collections.reverse(commits);
                    } catch (GitAPIException e) {
                        Logger.error(e);
                        throw new RuntimeException(e);
                    }

                    GroundTruth completedGroundTruth = new GroundTruth(new HashMap<>());
                    for (RevCommit commit : commits) {
                        File file = new File("results/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
                        GroundTruth loadedGT = Serde.deserialize(file);
                        Logger.info("Completing ground truth for {}", commit.getName());
                        loadedGT.complete(completedGroundTruth);
                        if (print) {
                            print(loadedGT, commit.getName());
                        }
                        // TODO: Save completed ground truth
                        completedGroundTruth = loadedGT;
                    }
                }
        );

    }

    private static void print(GroundTruth groundTruth, String commitName) {
        System.out.println();
        System.out.printf("*****************   %s   ******************", commitName);
        System.out.println();
        for (String file : groundTruth.fileGTs().keySet()) {
            System.out.println(groundTruth.get(file));
        }
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
                                    false,
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
