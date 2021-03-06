package org.variantsync.vevos.extraction;

import org.variantsync.vevos.extraction.kh.FullExtraction;
import org.variantsync.vevos.extraction.util.ShellExecutor;
import org.variantsync.vevos.extraction.util.GitUtil;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.EnumSetting;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Extraction {
    public static final @NonNull EnumSetting<Logger.Level> LOG_LEVEL_MAIN
            = new EnumSetting<>("log.level.main", Logger.Level.class, true, null, "" +
            "Log level of main execution.");
    public static final @NonNull Setting<@Nullable String> PATH_TO_SOURCE_REPO
            = new Setting<>("source_tree", Setting.Type.STRING, true, null, "" +
            "Path to the folder with the repository in which the investigated SPL is managed.");
    public static final @NonNull Setting<@Nullable String> WORKING_DIR_NAME
            = new Setting<>("working_dir_name", Setting.Type.STRING, false, "extraction-results", "" +
            "Name of the directory in which the analysis results and temporary files are stored.");
    public static final @NonNull Setting<@Nullable String> URL_OF_SOURCE_REPO
            = new Setting<>("source_repo_url", Setting.Type.STRING, false, null,
            "URL of the git repository that manages the sources of the investigated SPL.");
    public static final @NonNull Setting<@Nullable Integer> NUMBER_OF_THREADS
            = new Setting<>("analysis.number_of_tasks", Setting.Type.INTEGER, false, "1", "" +
            "The number of tasks that are used to run the analysis. The SPL sources are copied once for each task.");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_URL
            = new Setting<>("result.repo.url", Setting.Type.STRING, false, null,
            "The url to the repository to which the results are pushed to if result.collection_type is set to 'Repository'");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_COMMITTER_NAME
            = new Setting<>("result.repo.committer.name", Setting.Type.STRING, false, "Variability Extraction",
            "The name of the committer if result.collection_type is set to 'Repository'");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_COMMITTER_EMAIL
            = new Setting<>("result.repo.committer.email", Setting.Type.STRING, false, null,
            "The email of the committer if result.collection_type is set to 'Repository'");
    public static final @NonNull Setting<@Nullable Integer> EXTRACTION_TIMEOUT
            = new Setting<>("extraction.timeout", Setting.Type.INTEGER, false, "0", "" +
            "The timeout for the KernelHaven execution in seconds.");
    public static final @NonNull Setting<@Nullable String> ANALYSIS_CLASS
            = new Setting<>("analysis.class", Setting.Type.STRING, true, null, "Class of the pipeline that is used for the analysis");
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        checkOS(isWindows);
        LOGGER.logStatus("Starting SPL history analysis.");

        // Parse the arguments
        File propertiesFile = getPropertiesFile(args);
        String repo = args[1];
        String firstCommit = null;
        String lastCommit = null;
        if (args.length > 2) {
            firstCommit = args[2];
            if (args.length > 3) {
                lastCommit = args[3];
            }
        }

        // Load the configuration
        Configuration config = getConfiguration(propertiesFile);
        LOGGER.setLevel(config.getValue(LOG_LEVEL_MAIN));

        // Update the repo information if necessary
        if (config.getValue(PATH_TO_SOURCE_REPO).equals("TBD")) {
            LOGGER.logStatus("Expecting repo link as first argument...");
            LOGGER.logStatus("Provided repo link: " + repo);

            String[] parts = repo.split("/");
            String repoName = parts[parts.length-1];
            repoName = repoName.split("\\.")[0];
            LOGGER.logStatus("Identified repo name: " + repoName);

            config.setValue(PATH_TO_SOURCE_REPO, "./" + repoName);
            config.setValue(URL_OF_SOURCE_REPO, repo);
        }

        // Clone the SPL if necessary and return the File that points to the directory
        File splDir = setUpSPLDirectory(config);
        // Load git history
        List<RevCommit> commits = GitUtil.getCommits(splDir, firstCommit, lastCommit);
        LOGGER.logStatus("Identified " + commits.size() + " commit(s) for processing.");

        // Number of threats is the Minimum of the specified number and the number of commits to process
        int numberOfThreads = Math.min(config.getValue(NUMBER_OF_THREADS),commits.size());

        // Create the directories for each task running the analysis
        File workingDirectory = setUpWorkingDirectory(config, splDir, numberOfThreads);

        LOGGER.logStatus("Starting thread pool with " + numberOfThreads + " threads.");
        ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);
        LOGGER.logStatus("Splitting commits into " + numberOfThreads + " subset(s).");
        List<List<RevCommit>> commitSubsets = GitUtil.splitCommitsIntoSubsets(commits, numberOfThreads);
        LOGGER.logStatus("...done.");
        // Create a task for each commit subset and submit it to the thread pool
        int count = 0;
        LOGGER.logStatus("Scheduling tasks...");
        boolean fullExtraction = config.getValue(ANALYSIS_CLASS).endsWith(FullExtraction.class.getName());
        for (List<RevCommit> commitSubset : commitSubsets) {
            count += commitSubset.size();
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile, splDir.getName(), config.getValue(EXTRACTION_TIMEOUT), fullExtraction));
        }
        LOGGER.logStatus("all " + commitSubsets.size() + " tasks scheduled.");
        threadPool.shutdown();
        if (count != commits.size()) {
            LOGGER.logException("Subsets not created correctly: ",
                    new IllegalStateException("Expected the subsets to contain " + commits.size() +
                            " commits but only processed " + count + " commits."));
        }
        LOGGER.logStatus("All tasks submitted...");
        LOGGER.logStatus("Submitted a total of " + commits.size() + " commits.");
    }

    private static void checkOS(boolean isWindows) {
        LOGGER.logInfo("OS NAME: " + System.getProperty("os.name"));
        if (isWindows) {
            LOGGER.logError("Running the analysis under Windows is not supported as the Linux/BusyBox sources are not" +
                    "checked out correctly.");
            quitOnError();
        }
    }

    private static File getPropertiesFile(String[] args) {
        File propertiesFile = null;
        for (String arg : args) {
            if (propertiesFile == null) {
                propertiesFile = new File(arg);
            }
        }

        if (propertiesFile == null) {
            LOGGER.logError("You must specify a .properties file as first argument");
            quitOnError();
        }

        return propertiesFile;
    }

    private static Configuration getConfiguration(File propertiesFile) {
        Configuration config = null;
        try {
            config = new Configuration(Objects.requireNonNull(propertiesFile));
            config.registerSetting(LOG_LEVEL_MAIN);
            config.registerSetting(PATH_TO_SOURCE_REPO);
            config.registerSetting(WORKING_DIR_NAME);
            config.registerSetting(URL_OF_SOURCE_REPO);
            config.registerSetting(NUMBER_OF_THREADS);
            config.registerSetting(RESULT_REPO_URL);
            config.registerSetting(RESULT_REPO_COMMITTER_NAME);
            config.registerSetting(RESULT_REPO_COMMITTER_EMAIL);
            config.registerSetting(EXTRACTION_TIMEOUT);
            config.registerSetting(ANALYSIS_CLASS);
        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            quitOnError();
        }
        return config;
    }

    private static File setUpSPLDirectory(Configuration config) {
        // Clone the SPL repo if required
        File splDir = new File(config.getValue(PATH_TO_SOURCE_REPO));
        if (splDir.exists()) {
            LOGGER.logInfo("Directory with SPL sources found.");
        } else {
            LOGGER.logStatus("Cloning SPL...");
            LOGGER.logWarning("Depending on the download speed this might take several minutes.");
            LOGGER.logWarning("Consider cloning the repository manually for a better estimate of the download time.");
            File executionDir = splDir.getParentFile() == null ? new File(System.getProperty("user.dir")) : splDir.getParentFile();

            if (!EXECUTOR.execute("git clone " + config.getValue(URL_OF_SOURCE_REPO), executionDir)) {
                quitOnError();
            }
        }
        return splDir;
    }

    private static File setUpWorkingDirectory(Configuration config, File splDir, int numberOfThreads) {
        File workingDirectory = new File(System.getProperty("user.dir"));
        workingDirectory = new File(workingDirectory, config.getValue(WORKING_DIR_NAME));
        LOGGER.logInfo("Working Directory: " + workingDirectory);
        LOGGER.logStatus("Setting up working directory...");

        // Create the directory where the results of the individual runs are collected
        File overallOutputDirectory = new File(workingDirectory, "output");
        if (!overallOutputDirectory.exists()) {
            if (overallOutputDirectory.mkdirs()) {
                LOGGER.logInfo("Created common output directory.");
            }
        }


        for (int i = 0; i < numberOfThreads; i++) {
            File subDir = new File(workingDirectory, "run-" + i);
            // Create the path to the working directory of each task
            if (!subDir.exists()) {
                if (subDir.mkdirs()) {
                    LOGGER.logDebug("Created directory for task number #" + i);
                }
            }
            // Create the directories that are expected by KernelHaven
            createKernelHavenDirs(subDir);
            // Copy the SPL sources to the subDir, so that it can be analyzed locally
            File targetFile = new File(subDir, splDir.getName());
            if (!targetFile.exists()) {
                LOGGER.logStatus("Copying the SPL directory to the sub directory for task #" + i + ".");
                EXECUTOR.execute("cp -rf " + splDir.getAbsolutePath() + " .", subDir);
            } else {
                LOGGER.logDebug("SPL directory exists in sub dir.");
            }
            // Copy the properties file to the subDir
            LOGGER.logDebug("Copying the properties file to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f " + config.getPropertyFile().getAbsolutePath() + " .", subDir);
            // Copy the KernelHaven plugins to the sub-dir
            LOGGER.logDebug("Copying the KernelHaven plugin to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f ../Extraction-* " + subDir + "/plugins/", workingDirectory);
            //EXECUTOR.execute("cp -f ../plugins/* " + subDir + "/plugins/", workingDirectory);
            // Copy KernelHaven to the sub-dir
            LOGGER.logDebug("Copying KernelHaven to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f ../KernelHaven.jar " + subDir + "/", workingDirectory);
        }
        LOGGER.logStatus("...done with setting up working directory.");
        return workingDirectory;
    }

    private static void createKernelHavenDirs(File taskDirectory) {
        LOGGER.logStatus("Creating directories required by KernelHaven...");
        if (new File(taskDirectory, "res").mkdirs()) {
            LOGGER.logDebug("Resource directory created.");
        }
        if (new File(taskDirectory, "output").mkdirs()) {
            LOGGER.logDebug("Output directory created.");
        }
        if (new File(taskDirectory, "plugins").mkdirs()) {
            LOGGER.logDebug("Plugins directory created.");
        }
        if (new File(taskDirectory, "cache").mkdirs()) {
            LOGGER.logDebug("Cache directory created.");
        }
        if (new File(taskDirectory, "log").mkdirs()) {
            LOGGER.logDebug("Log directory created.");
        }
        LOGGER.logInfo("...done.");
    }

    public static void quitOnError() {
        LOGGER.logError("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }


}
