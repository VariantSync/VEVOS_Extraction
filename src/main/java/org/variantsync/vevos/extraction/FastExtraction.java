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
import org.variantsync.diffdetective.variation.diff.parse.DiffTreeParseOptions;

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

public class FastExtraction {
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final String VARIABLES_FILE = "VARIABLES.txt";
    private static final String CODE_VARIABILITY_CSV = "code-variability.spl.csv";
    private final Properties properties;

    public static final String PRINT_ENABLED
            = "extraction.print-enabled";

    public static final String GT_SAVE_DIR
            = "extraction.gt-save-dir";

    public static final String DATASET_FILE
            = "diff-detective.dataset-file";

    public static final String DD_OUTPUT_DIR
            = "diff-detective.output-dir";

    public static final String REPO_SAVE_DIR
            = "diff-detective.repo-storage-dir";

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

    public FastExtraction(Properties properties) {
        this.properties = properties;
    }

    public void run(AnalysisRunner.Options options) throws IOException {
        AnalysisRunner.run(options, runner);
    }

    /**
     * Main method to start the analysis.
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {

        checkOS();

        // Load the configuration
        Properties properties = getProperties(getPropertiesFile(args));
        var extraction = new FastExtraction(properties);

        var options = diffdetectiveOptions(properties);
        Logger.info("Starting SPL history analysis.");
        extraction.run(options);
    }

    private final BiConsumer<Repository, Path> runner = (repo, repoOutputDir) -> {
        Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir));

        ArrayList<RevCommit> commits = new ArrayList<>();
        try (Git gitRepo = repo.getGitRepo().run()) {
            gitRepo.log().call().forEach(commits::add);
            Collections.reverse(commits);
        } catch (GitAPIException e) {
            Logger.error(e);
            throw new RuntimeException(e);
        }

        try(ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            postprocess(repo, commits, threadPool);
            Logger.info("Awaiting termination of threadpool");
            threadPool.shutdown();
        }
        PCAnalysis.numProcessed = 0;
    };

    private void postprocess(Repository repo, ArrayList<RevCommit> commits, ExecutorService threadPool) {
        boolean print = Boolean.parseBoolean(this.properties.getProperty(PRINT_ENABLED));
        int processedCount = 0;
        GroundTruth completedGroundTruth = new GroundTruth(new HashMap<>(), new HashSet<>());

        for (RevCommit commit : commits) {
            File file = new File("results/pc/" + repo.getRepositoryName() + "/" + commit.getName() + ".gt");
            if (Files.exists(file.toPath())) {
                GroundTruth loadedGT = Serde.deserialize(file);
                Logger.info("Completing ground truth for {}", commit.getName());
                completedGroundTruth.updateWith(loadedGT);
                if (print) {
                    print(completedGroundTruth, commit.getName());
                }
            }
            // Save the extracted ground truth
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

            String groundTruthAsCSV = completedGroundTruth.asCSVString();
            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(CODE_VARIABILITY_CSV), groundTruthAsCSV));

            threadPool.submit(() -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_MESSAGE_FILE), commit.getFullMessage()));

            Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
            threadPool.submit(() -> parentIds.ifPresent(s -> Serde.writeToFile(commitSaveDir.resolve(COMMIT_PARENTS_FILE), s)));

            threadPool.submit(() -> Serde.appendText(resultsRoot.resolve(SUCCESS_COMMIT_FILE), commit.getName() + "\n"));
            processedCount++;
            Logger.info("Saved ground truth for commit {} of {}", processedCount, commits.size());
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

    public static AnalysisRunner.Options diffdetectiveOptions(Properties properties) {
//        AnalysisRunner.Options defaultOptions = AnalysisRunner.Options.DEFAULT(args);

        return new AnalysisRunner.Options(
                Path.of(properties.getProperty(REPO_SAVE_DIR)),
                Path.of(properties.getProperty(DD_OUTPUT_DIR)),
                        Path.of(properties.getProperty(DATASET_FILE)),
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

    public static void quitOnError() {
        Logger.error("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }
}
