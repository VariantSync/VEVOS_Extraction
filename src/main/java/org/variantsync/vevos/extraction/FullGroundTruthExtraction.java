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
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;
import org.variantsync.vevos.extraction.analysis.FullVariabilityAnalysis;
import org.variantsync.vevos.extraction.gt.GroundTruth;
import org.variantsync.vevos.extraction.io.Serde;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;


public class FullGroundTruthExtraction {
    public static final String PRINT_ENABLED
            = "extraction.print-enabled";
    public static final String IGNORE_PC_CHANGES
            = "extraction.ignore-pc-changes";
    public static final String EXTRACT_CODE_MATCHING
            = "extraction.extract-code-matching";
    public static final String GT_SAVE_DIR
            = "extraction.gt-save-dir";
    public static final String DATASET_FILE
            = "diff-detective.dataset-file";
    public static final String DD_OUTPUT_DIR
            = "diff-detective.output-dir";
    public static final String REPO_SAVE_DIR
            = "diff-detective.repo-storage-dir";
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final String VARIABLES_FILE = "VARIABLES.txt";
    private static final String CODE_VARIABILITY_CSV = "code-variability.spl.csv";
    private final Properties properties;

    public FullGroundTruthExtraction(Properties properties) {
        this.properties = properties;
    }

    /**
     * Main method to start the extraction.
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {

        checkOS();

        // Load the configuration
        Properties properties = getProperties(getPropertiesFile(args));
        var extraction = new FullGroundTruthExtraction(properties);

        var options = diffdetectiveOptions(properties);
        Logger.info("Starting SPL history analysis.");
        extraction.run(options);
    }

    /**
     * Prints the given ground truth to console.
     *
     * @param groundTruth GT to print
     * @param commitName  The id of the commit for which the GT has been calculated
     */
    private static void print(GroundTruth groundTruth, String commitName) {
        System.out.println();
        System.out.printf("*****************   %s   ******************", commitName);
        System.out.println();
        for (String file : groundTruth.fileGTs().keySet()) {
            System.out.println(groundTruth.get(file));
        }
    }

    /**
     * Options for the execution of DiffDetective
     *
     * @param properties The properties loaded by main()
     * @return The options instance
     */
    public static AnalysisRunner.Options diffdetectiveOptions(Properties properties) {

        return new AnalysisRunner.Options(
                Path.of(properties.getProperty(REPO_SAVE_DIR)),
                Path.of(properties.getProperty(DD_OUTPUT_DIR)),
                Path.of(properties.getProperty(DATASET_FILE)),
                repo -> {
                    final PatchDiffParseOptions repoDefault = repo.getParseOptions();
                    return new PatchDiffParseOptions(
                            PatchDiffParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                            new VariationDiffParseOptions(
                                    repoDefault.variationDiffParseOptions().annotationParser(),
                                    false,
                                    false
                            )
                    );
                },
                repo -> new DiffFilter.Builder()
                        .allowMerge(true)
                        // TODO: make configurable
                        .allowedFileExtensions("h", "hpp", "c", "cpp")
                        .build(),
                true,
                false
        );
    }

    /**
     * Parses the file in which the properties are located from the arguments.
     *
     * @param args the arguments to parse
     * @return the properties file
     */
    private static File getPropertiesFile(String[] args) {
        File propertiesFile = null;
        if (args.length > 0) {
            propertiesFile = new File(args[0]);
        }

        if (propertiesFile == null) {
            Logger.error("You must specify a .properties file as first argument");
            quitOnError();
        }

        return propertiesFile;
    }

    /**
     * Loads the properties in the given file.
     *
     * @param propertiesFile The file to load
     * @return The loaded properties
     */
    private static Properties getProperties(File propertiesFile) {
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream(propertiesFile)) {
            props.load(input);
        } catch (IOException e) {
            Logger.error("problem while loading properties");
            Logger.error(e);
            quitOnError();
        }
        return props;
    }

    /**
     * Throws an error if the host OS is Windows.
     */
    private static void checkOS() {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        Logger.info("OS NAME: " + System.getProperty("os.name"));
        if (isWindows) {
            Logger.error("Running the analysis under Windows is not supported as the Linux/BusyBox sources are not" +
                    "checked out correctly.");
            quitOnError();
        }
    }

    /**
     * Logs an error message and quits the extraction with an exception.
     */
    public static void quitOnError() {
        Logger.error("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }

    private BiConsumer<Repository, Path> buildRunner(String diffDetectiveCache) {
        return (repo, repoOutputDir) -> {
            FullVariabilityAnalysis analysis = new FullVariabilityAnalysis(Path.of(diffDetectiveCache), Boolean.parseBoolean(properties.getProperty(IGNORE_PC_CHANGES)), Boolean.parseBoolean(properties.getProperty(EXTRACT_CODE_MATCHING)));
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
     * Starts the extraction.
     *
     * @param options The options for DiffDetective
     * @throws IOException If an IO error occurs in DiffDetective
     */
    public void run(AnalysisRunner.Options options) throws IOException {
        AnalysisRunner.run(options, buildRunner(properties.getProperty(DD_OUTPUT_DIR)));
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
            if (processedCount % 1_000 == 0) {
                Logger.info("Saved ground truth for commit {} of {}", processedCount + 1, commits.size());
            }
            lastCommit = commit;
            processedCount++;
        }
    }
}
