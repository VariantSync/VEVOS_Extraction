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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.*;
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

                    GroundTruth completedGroundTruth = new GroundTruth(new Hashtable<>());
                    for (RevCommit commit : commits) {
                        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream("results/pc/" + commit.getName() + ".gt"))) {
                            Object loaded = is.readObject();
                            if (loaded instanceof GroundTruth loadedGT) {
                                loadedGT.complete(completedGroundTruth);
                                System.out.println();
                                System.out.printf("*****************   %s   ******************", commit.getName());
                                System.out.println();
                                print(loadedGT);
                                completedGroundTruth = loadedGT;
                            } else {
                                Logger.error("Was not able to load ground truth");
                                throw new RuntimeException();
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            Logger.error(e);
                            throw new RuntimeException(e);
                    }}
                }
        );

    }

    private static void print(GroundTruth groundTruth) {
        for (String file : groundTruth.fileGTs().keySet()) {
            FileGT fileGT = groundTruth.get(file);
            System.out.printf("File: %s%n", file);

            for (LineAnnotation line : fileGT) {
                System.out.printf("%s%n", line);
            }
            System.out.println("+++");
            System.out.println();
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
