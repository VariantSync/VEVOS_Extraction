package org.variantsync.vevos.extraction;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.vevos.extraction.analysis.FullVariabilityAnalysis;
import org.variantsync.vevos.extraction.gt.GroundTruth;
import org.variantsync.vevos.extraction.io.Serde;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.variantsync.vevos.extraction.ConfigProperties.*;
import static org.variantsync.vevos.extraction.gt.GroundTruth.*;


public class FullGroundTruthExtraction extends GroundTruthExtraction {

    public FullGroundTruthExtraction(Properties properties) {
        super(properties);
        Logger.info("Starting full ground truth extraction that extracts a ground truth for all files of each commit.");
    }

    protected BiConsumer<Repository, Path> extractionRunner() {
        return (repo, repoOutputDir) -> {
            FullVariabilityAnalysis analysis =
                    new FullVariabilityAnalysis(Path.of(properties.getProperty(DD_OUTPUT_DIR)),
                            Boolean.parseBoolean(properties.getProperty(IGNORE_PC_CHANGES)));
            final BiFunction<Repository, Path, Analysis> AnalysisFactory = (r, out) -> new Analysis(
                    "PCAnalysis",
                    List.of(
                            analysis
                    ),
                    r,
                    out
            );
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            final int commitsToProcessPerThread = 256;

            Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir), commitsToProcessPerThread, availableProcessors);

            ArrayList<RevCommit> commits = new ArrayList<>();
            try (Git gitRepo = repo.getGitRepo().run()) {
                gitRepo.log().call().forEach(commits::add);
                Collections.reverse(commits);
            } catch (GitAPIException e) {
                Logger.error(e);
                throw new RuntimeException(e);
            }

            ExecutorService threadPool = null;
            try {
                threadPool = Executors.newFixedThreadPool(availableProcessors);
                postprocess(repo, commits, threadPool);
            } finally {
                if (threadPool != null) {
                    Logger.info("Awaiting termination of threadpool");
                    threadPool.shutdown();
                }
            }
            FullVariabilityAnalysis.numProcessed = 0;
        };
    }


    /**
     * Incrementally combines the ground truths from the first to the last commit. The ground truth for unmodified files
     * are reused. New file ground truths are added for created files, and old ground truths are updated for modified files.
     *
     * @param repo       The repo that has been analyzed
     * @param commits    A list of commits in the repo
     * @param threadPool A thread pool for multithreading of IO operations
     */
    private void postprocess(Repository repo, ArrayList<RevCommit> commits, ExecutorService threadPool) {
        boolean print = Boolean.parseBoolean(this.properties.getProperty(PRINT_ENABLED));
        int processedCount = 0;
        RevCommit lastCommit = null;
        GroundTruth completedGroundTruth = new GroundTruth(new HashMap<>(), new HashSet<>());
        final String diffDetectiveCache = properties.getProperty(DD_OUTPUT_DIR);
        for (RevCommit commit : commits) {
            if (lastCommit != null) {
                // Check whether the last commit is the first parent of this commit.
                // If this is the case, we can continue with the existing ground truth.
                // If this is not the case, we have to load the completed ground truth of the parent.
                RevCommit firstParent = Arrays.stream(commit.getParents()).findFirst().orElse(null);
                if (firstParent == null) {
                    completedGroundTruth = new GroundTruth(new HashMap<>(), new HashSet<>());
                } else if (!firstParent.equals(lastCommit)) {
                    File parentGT = new File(diffDetectiveCache + "/pc/" + repo.getRepositoryName() + "/" + firstParent.getName() + ".gt");
                    completedGroundTruth = Serde.deserialize(parentGT);
                }
            }
            File currentGTFile = new File(diffDetectiveCache + "/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
            if (Files.exists(currentGTFile.toPath())) {
                GroundTruth loadedGT = Serde.deserialize(currentGTFile);
                if (processedCount % 1_000 == 0) {
                    Logger.info("Completing ground truth for {}", commit.getName());
                }
                completedGroundTruth.updateWith(loadedGT);
                if (print) {
                    print(completedGroundTruth, commit.getName());
                }
            }
            // Save the extracted ground truth
            Serde.serialize(currentGTFile, completedGroundTruth);
            Path extractionDir = Path.of(this.properties.getProperty(GT_SAVE_DIR));
            Path resultsRoot = extractionDir.resolve(repo.getRepositoryName());
            Path commitSaveDir = resultsRoot.resolve("data").resolve(commit.getName());
            try {
                Files.createDirectories(commitSaveDir);
            } catch (IOException e) {
                Logger.error(e);
                throw new UncheckedIOException(e);
            }
            String variablesList = completedGroundTruth.variablesListAsString();
            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(VARIABLES_FILE), variablesList));

            String groundTruthAsCSV = completedGroundTruth.asPcCsvString();
            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV), groundTruthAsCSV));

            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage()));

            Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
            threadPool.submit(() -> parentIds.ifPresentOrElse(
                    s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s),
                    () -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), "")));

            threadPool.submit(() -> Serde.appendText(resultsRoot.resolve(SUCCESS_COMMIT_FILE), commit.getName() + "\n"));

            if (Boolean.parseBoolean(properties.getProperty(EXTRACT_CODE_MATCHING))) {
                String matchingAsCSV = completedGroundTruth.asMatchingCsvString();

                threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(CODE_MATCHING_CSV),
                        matchingAsCSV));
            }

            if (processedCount % 1_000 == 0) {
                Logger.info("Saved ground truth for commit {} of {}", processedCount + 1, commits.size());
            }
            lastCommit = commit;
            processedCount++;
        }
    }
}
