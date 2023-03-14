package org.variantsync.vevos.extraction;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.util.TriConsumer;
import org.variantsync.diffdetective.variation.diff.parse.DiffTreeParseOptions;
import org.variantsync.vevos.extraction.util.PCAnalysis;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class FastExtraction {
    private final static Path EXTRACTION_DIR = Path.of("extraction-results");
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private final static String ERROR_COMMIT_FILE = "ERROR_COMMITS.txt";
    private final static String INCOMPLETE_PC_COMMIT_FILE = "PARTIAL_SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final String VARIABLES_FILE = "VARIABLES.txt";
    private static final String CODE_VARIABILITY_CSV = "code-variability.spl.csv";

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

        AnalysisRunner.run(options, runner);
    }

    private static final BiConsumer<Repository, Path> runner = (repo, repoOutputDir) -> {
        boolean print = false;
        Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir));

        ArrayList<RevCommit> commits = new ArrayList<>();
        try (Git gitRepo = repo.getGitRepo().run()) {
            gitRepo.log().call().forEach(commits::add);
            Collections.reverse(commits);
        } catch (GitAPIException e) {
            Logger.error(e);
            throw new RuntimeException(e);
        }

        GroundTruth completedGroundTruth = new GroundTruth(new HashMap<>(), new HashSet<>());
        try(ExecutorService threadPool = Executors.newFixedThreadPool(12)) {
            postprocess(repo, print, commits, completedGroundTruth, threadPool);
            Logger.info("Awaiting termination of threadpool");
            threadPool.shutdown();
        }
    };

    private static void postprocess(Repository repo, boolean print, ArrayList<RevCommit> commits, GroundTruth completedGroundTruth, ExecutorService threadPool) {
        for (RevCommit commit : commits) {
            File file = new File("results/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
            if (Files.exists(file.toPath())) {
                GroundTruth loadedGT = Serde.deserialize(file);
                Logger.info("Completing ground truth for {}", commit.getName());
                loadedGT.complete(completedGroundTruth);
                if (print) {
                    print(loadedGT, commit.getName());
                }
                completedGroundTruth = loadedGT;
            }
            // Save the extracted ground truth
            Path resultsRoot = EXTRACTION_DIR.resolve(repo.getRepositoryName());
            Path commitSaveDir = resultsRoot.resolve("data").resolve(commit.getName());
            try {
                Files.createDirectories(commitSaveDir);
            } catch (IOException e) {
                Logger.error(e);
                throw new UncheckedIOException(e);
            }
            String variablesList = completedGroundTruth.variablesListAsString();
            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(VARIABLES_FILE), variablesList));

            String groundTruthAsCSV = completedGroundTruth.asCSVString();
            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV), groundTruthAsCSV));

            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage()));

            Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
            threadPool.submit(() -> parentIds.ifPresent(s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s)));

            threadPool.submit(() -> Serde.appendText(resultsRoot.resolve(SUCCESS_COMMIT_FILE), commit.getName() + "\n"));
        }
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
