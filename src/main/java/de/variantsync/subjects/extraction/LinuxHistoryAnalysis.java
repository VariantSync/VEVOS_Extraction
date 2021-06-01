package de.variantsync.subjects.extraction;

import de.variantsync.subjects.extraction.util.GitUtil;
import de.variantsync.subjects.extraction.util.ShellExecutor;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
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

public class LinuxHistoryAnalysis {

    public static final @NonNull Setting<@Nullable String> PATH_TO_SOURCE_REPO
            = new Setting<>("source_tree", Setting.Type.STRING, true, null, "" +
            "Path to the folder with the repository in which the investigated SPL is managed.");
    public static final @NonNull Setting<@Nullable String> WORKING_DIR_NAME
            = new Setting<>("working_dir_name", Setting.Type.STRING, false, "extraction-results", "" +
            "Name of the directory in which the analysis results and temporary files are stored.");
    public static final @NonNull Setting<@Nullable String> URL_OF_SOURCE_REPO
            = new Setting<>("source_repo_url", Setting.Type.STRING, true, "https://github.com/torvalds/linux.git",
            "URL of the git repository that manages the sources of the investigated SPL.");
    public static final @NonNull Setting<@Nullable Integer> NUMBER_OF_THREADS
            = new Setting<>("analysis.number_of_tasks", Setting.Type.INTEGER, false, "1", "" +
            "The number of tasks that are used to run the analysis. The SPL sources are copied once for each task.");
    public static final @NonNull EnumSetting<EResultCollection> RESULT_COLLECTION_TYPE
            = new EnumSetting<>("result.collection_type", EResultCollection.class, false, EResultCollection.NONE,
            "The way in which the results of several analysis tasks are collected, e.g., in a common output directory");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_URL
            = new Setting<>("result.repo.url", Setting.Type.STRING, false, null,
            "The url to the repository to which the results are pushed to if result.collection_type is set to 'Repository'");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_COMMITTER_NAME
            = new Setting<>("result.repo.committer.name", Setting.Type.STRING, false, "Variability Extraction",
            "The name of the committer if result.collection_type is set to 'Repository'");
    public static final @NonNull Setting<@Nullable String> RESULT_REPO_COMMITTER_EMAIL
            = new Setting<>("result.repo.committer.email", Setting.Type.STRING, false, null,
            "The email of the committer if result.collection_type is set to 'Repository'");
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
    private static EResultCollection resultCollectionType;

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        checkOS(isWindows);
        LOGGER.logStatus("Starting SPL history analysis.");

        // Parse the arguments
        File propertiesFile = getPropertiesFile(args);
        String firstCommit = null;
        String lastCommit = null;
        if (args.length > 1) {
            firstCommit = args[1];
            if (args.length > 2) {
                lastCommit = args[2];
            } else {
                LOGGER.logError("A first commit id " + firstCommit + " was provided, but a last commit id is " +
                        "required as well if only a specific range of commits is to be investigated...");
                quitOnError();
            }
        }

        // Load the configuration
        Configuration config = getConfiguration(propertiesFile);
        LOGGER.setLevel(config.getValue(DefaultSettings.LOG_LEVEL));
        resultCollectionType = config.getValue(RESULT_COLLECTION_TYPE);
        if (resultCollectionType != EResultCollection.NONE) {
            LOGGER.logDebug("Analysis configured to collect the output of the started tasks.");
        }

        // Clone the SPL if necessary and return the File that points to the directory
        File splDir = setUpSPLDirectory(config);
        // Create the directories for each task running the analysis
        File workingDirectory = setUpWorkingDirectory(config, splDir);
        // Load git history
        List<RevCommit> commits = GitUtil.getCommits(splDir, firstCommit, lastCommit);

        int numberOfThreads = config.getValue(NUMBER_OF_THREADS);
        LOGGER.logStatus("Starting thread pool with " + numberOfThreads + " threads.");
        ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);
        LOGGER.logStatus("Splitting commits into " + numberOfThreads + " subset(s).");
        List<List<RevCommit>> commitSubsets = GitUtil.splitCommitsIntoSubsets(commits, numberOfThreads);
        LOGGER.logStatus("...done.");
        // Create a task for each commit subset and submit it to the thread pool
        int count = 0;
        LOGGER.logStatus("Scheduling tasks...");
        for (List<RevCommit> commitSubset : commitSubsets) {
            count += commitSubset.size();
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile, splDir.getName(), config));
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
            LOGGER.logError("Running the analysis under Windows is not supported as the Linux sources are not" +
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
            config.registerSetting(DefaultSettings.LOG_LEVEL);
            config.registerSetting(PATH_TO_SOURCE_REPO);
            config.registerSetting(WORKING_DIR_NAME);
            config.registerSetting(URL_OF_SOURCE_REPO);
            config.registerSetting(NUMBER_OF_THREADS);
            config.registerSetting(RESULT_COLLECTION_TYPE);
            config.registerSetting(RESULT_REPO_URL);
            config.registerSetting(RESULT_REPO_COMMITTER_NAME);
            config.registerSetting(RESULT_REPO_COMMITTER_EMAIL);
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
            if (!EXECUTOR.execute("git clone " + config.getValue(URL_OF_SOURCE_REPO), splDir.getParentFile())) {
                quitOnError();
            }
        }
        return splDir;
    }

    private static File setUpWorkingDirectory(Configuration config, File splDir) {
        int numberOfThreads = config.getValue(NUMBER_OF_THREADS);
        File workingDirectory = new File(System.getProperty("user.dir"));
        workingDirectory = new File(workingDirectory, config.getValue(WORKING_DIR_NAME));
        LOGGER.logInfo("Working Directory: " + workingDirectory);
        LOGGER.logStatus("Setting up working directory...");

        // Create the directory where the results of the individual runs are collected
        if (resultCollectionType != EResultCollection.NONE) {
            File overallOutputDirectory = new File(workingDirectory, "output");
            if (!overallOutputDirectory.exists()) {
                if (overallOutputDirectory.mkdirs()) {
                    LOGGER.logInfo("Created common output directory.");
                    if (resultCollectionType == EResultCollection.LOCAL_REPOSITORY || resultCollectionType == EResultCollection.REMOTE_REPOSITORY) {
                        // Initialize a git repository
                        EXECUTOR.execute("git init", overallOutputDirectory);
                        EXECUTOR.execute("git config user.name \"" + config.getValue(RESULT_REPO_COMMITTER_NAME) + "\"", overallOutputDirectory);
                        EXECUTOR.execute("git config user.email \"" + config.getValue(RESULT_REPO_COMMITTER_EMAIL) + "\"", overallOutputDirectory);
                        EXECUTOR.execute("touch init", overallOutputDirectory);
                        EXECUTOR.execute("git add .", overallOutputDirectory);
                        EXECUTOR.execute("git commit -m \"Initial commit\"", overallOutputDirectory);
                        if (resultCollectionType == EResultCollection.REMOTE_REPOSITORY) {
                            EXECUTOR.execute("git remote add origin " + config.getValue(RESULT_REPO_URL), overallOutputDirectory);
                            EXECUTOR.execute("git push -uf origin main", overallOutputDirectory);
                        }
                    }
                }
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
            LOGGER.logDebug("Copying the VariabilityExtraction as KernelHaven plugin to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f ../VariabilityExtraction-* " + subDir + "/plugins/", workingDirectory);
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
