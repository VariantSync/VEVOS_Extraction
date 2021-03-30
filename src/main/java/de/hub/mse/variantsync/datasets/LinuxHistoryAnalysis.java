package de.hub.mse.variantsync.datasets;

import de.hub.mse.variantsync.datasets.util.ShellExecutor;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class LinuxHistoryAnalysis {

    public static final @NonNull Setting<@Nullable String> PATH_TO_SOURCE_REPO
            = new Setting<>("source_tree", Setting.Type.STRING, true, null, "" +
            "Path to the folder with the repository in which the investigated SPL is managed.");
    public static final @NonNull Setting<@Nullable String> URL_OF_SOURCE_REPO
            = new Setting<>("source_repo_url", Setting.Type.STRING, true, "https://github.com/torvalds/linux.git",
            "URL of the git repository that manages the sources of the investigated SPL.");
    public static final @NonNull Setting<@Nullable Integer> NUMBER_OF_THREADS
            = new Setting<>("analysis.number_of_tasks", Setting.Type.INTEGER, false, "1", "" +
            "The number of tasks that are used to run the analysis. The SPL sources are copied once for each task.");
    public static final @NonNull Setting<@Nullable Boolean> COLLECT_OUTPUT
            = new Setting<>("analysis.output_by_commit", Setting.Type.BOOLEAN, false, "false",
            "Whether the results of the conducted analysis are to be collected in a common output directory");
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
    private static boolean collectOutput;

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        checkOS(isWindows);
        LOGGER.logInfo("Starting SPL history analysis.");

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
        collectOutput = config.getValue(COLLECT_OUTPUT);
        if (collectOutput) {
            LOGGER.logDebug("Analysis configured to collect the output of the started tasks.");
        }

        // Clone the SPL if necessary and return the File that points to the directory
        File splDir = setUpSPLDirectory(config);
        // Create the directories for each task running the analysis
        File workingDirectory = setUpWorkingDirectory(config, splDir);
        // Load git history
        List<RevCommit> commits = getCommits(splDir, lastCommit, firstCommit);


        int numberOfThreads = config.getValue(NUMBER_OF_THREADS);
        LOGGER.logInfo("Starting thread pool with " + numberOfThreads + " threads.");
        ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);

        List<List<RevCommit>> commitSubsets = splitCommitsIntoSubsets(commits, numberOfThreads);

        // Create a task for each commit subset and submit it to the thread pool
        int count = 0;
        for (List<RevCommit> commitSubset : commitSubsets) {
            count += commitSubset.size();
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile, splDir.getName(), collectOutput));
        }
        // TODO: Check whether the behavior here is valid
        threadPool.shutdown();
        if (count != commits.size()) {
            LOGGER.logException("Subsets not created correctly: ",
                    new IllegalStateException("Expected the subsets to contain " + commits.size() +
                            " commits but only processed " + count + " commits."));
        }
        LOGGER.logInfo("All tasks submitted...");
        LOGGER.logInfo("Submitted a total of " + commits.size() + " commits.");
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
            config.registerSetting(NUMBER_OF_THREADS);
            config.registerSetting(COLLECT_OUTPUT);
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
            LOGGER.logInfo("Cloning SPL...");
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
        workingDirectory = new File(workingDirectory, "commit-analysis");
        LOGGER.logInfo("Working Directory: " + workingDirectory);
        LOGGER.logInfo("Setting up working directory...");

        // Create the directory where the results of the individual runs are collected
        if (collectOutput) {
            File overallOutputDirectory = new File(workingDirectory, "output");
            if (!overallOutputDirectory.exists()) {
                if(overallOutputDirectory.mkdirs()) {
                    LOGGER.logInfo("Created common output directory.");
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
                try {
                    LOGGER.logDebug("Copying the SPL directory to the sub directory for task #" + i + ".");
                    Files.copy(splDir.toPath(), targetFile.toPath());
                } catch (IOException e) {
                    LOGGER.logException("An Exception occurred while trying to copy the SPL directory: ", e);
                }
            }
            // Copy the properties file to the subDir
            targetFile = new File(subDir, config.getPropertyFile().getName());
            try {
                LOGGER.logDebug("Copying the properties file to the sub directory for task #" + i + ".");
                Files.copy(config.getPropertyFile().toPath(), targetFile.toPath(), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("An Exception occurred while trying to copy the properties file: ", e);
            }
        }
        LOGGER.logInfo("...done with setting up working directory.");
        return workingDirectory;
    }

    private static void createKernelHavenDirs(File taskDirectory) {
        LOGGER.logInfo("Creating directories required by KernelHaven...");
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

    private static List<RevCommit> getCommits(File splDir, String lastCommitId, String firstCommitId) throws IOException, GitAPIException {
        Git gitRepo = initGitForRepo(splDir);

        List<RevCommit> commits = new LinkedList<>();
        if (firstCommitId != null && lastCommitId != null) {
            LOGGER.logInfo("Commit range specified...filtering commits.");
            // Get the commit objects
            ObjectId firstCommit = gitRepo.getRepository().resolve(firstCommitId);
            ObjectId lastCommit = gitRepo.getRepository().resolve(lastCommitId);
            Iterable<RevCommit> commitIterable = gitRepo.log().addRange(firstCommit, lastCommit).call();
            // Filter all commits not in the specified range of commits
            boolean inRange = false;
            for (RevCommit commit : commitIterable) {
                if (commit.getName().equals(firstCommitId) || commit.getName().equals(lastCommitId)) {
                    // Invert the value upon reaching one of the boundaries
                    inRange = !inRange;
                }
                if (inRange) {
                    commits.add(commit);
                }
            }
            LOGGER.logInfo("" + commits.size() + " remain for analysis.");
        } else {
            Iterable<RevCommit> commitIterable = gitRepo.log().all().call();
            // Add all commits, if not commit range was specified
            commitIterable.forEach(commits::add);
        }
        return commits;
    }

    private static Git initGitForRepo(File splDir) throws IOException {
        LOGGER.logDebug("Initializing git repo...");
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(splDir, ".git"))
                .build();
        Git git = new Git(repository);
        LOGGER.logDebug("...done.");
        return git;
    }

    private static List<List<RevCommit>> splitCommitsIntoSubsets(List<RevCommit> commits, int numberOfThreads) {
        List<List<RevCommit>> commitSubsets = new ArrayList<>(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            commitSubsets.add(new LinkedList<>());
        }
        for (int i = 0; i < commits.size(); ) {
            for (int k = 0; k < numberOfThreads; k++) {
                RevCommit nextCommit = commits.get(i);
                commitSubsets.get(k).add(nextCommit);
                i++;
                if (i >= commits.size()) {
                    break;
                }
            }
        }
        return commitSubsets;
    }

    static void quitOnError() {
        LOGGER.logError("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }


}
