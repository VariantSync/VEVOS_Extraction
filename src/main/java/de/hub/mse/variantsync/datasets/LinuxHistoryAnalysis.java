package de.hub.mse.variantsync.datasets;

import de.hub.mse.variantsync.datasets.kh.CommitUsabilityAnalysis;
import de.hub.mse.variantsync.datasets.util.ShellExecutor;
import net.ssehub.kernel_haven.PipelineConfigurator;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LinuxHistoryAnalysis {

    public static final @NonNull Setting<@Nullable String> PATH_TO_SOURCE_REPO
            = new Setting<>("source_tree", Setting.Type.STRING, true, null, "" +
            "Path to the folder with the repository in which the investigated SPL is managed.");
    // TODO: Generify so that the commit analysis is not the only analysis that can be run
    public static final @NonNull Setting<@Nullable String> URL_OF_SOURCE_REPO
            = new Setting<>("source_repo_url", Setting.Type.STRING, true, "https://github.com/torvalds/linux.git",
            "URL of the git repository that manages the sources of the investigated SPL.");
    public static final @NonNull Setting<@Nullable Integer> NUMBER_OF_THREADS
            = new Setting<>("analysis.number_of_tasks", Setting.Type.INTEGER, false, "1", "" +
            "The number of tasks that are used to run the analysis. The SPL sources are copied once for each task.");
    public static final @NonNull Setting<@Nullable Boolean> COLLECT_OUTPUT
            = new Setting<>("analysis.collect_output", Setting.Type.BOOLEAN, false, "false",
            "Whether the results of the conducted analysis are to be collected in a common output directory");
    protected static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        LOGGER.setLevel(LOG_LEVEL);
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
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile, splDir.getName()));
        }
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
        if (config.getValue(COLLECT_OUTPUT)) {
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
            if (!new File(subDir, splDir.getName()).exists()) {
                EXECUTOR.execute("cp -rf " + splDir.getAbsolutePath() + " .", subDir);
            }
            // Copy the properties file to the subDir
            EXECUTOR.execute("cp -f " + config.getPropertyFile().getAbsolutePath() + " .", subDir);
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

    private static void quitOnError() {
        LOGGER.logError("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }

    private static class AnalysisTask implements Runnable {
        private static final Logger LOGGER = Logger.get();
        private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
        private static int existingTasksCount = 0;
        private final int taskNumber;
        private final File parentDir;
        private final List<RevCommit> commits;
        private final File parentPropertiesFile;
        private final String splName;

        public AnalysisTask(List<RevCommit> commits, File parentDir, File propertiesFile, String splName) {
            this.commits = commits;
            this.parentDir = parentDir;
            this.parentPropertiesFile = propertiesFile;
            this.splName = splName;
            this.taskNumber = existingTasksCount++;
            LOGGER.setLevel(LOG_LEVEL);
        }

        @Override
        public void run() {
            String taskName = String.valueOf(taskNumber);
            String threadName = Thread.currentThread().getName();
            LOGGER.logInfo("Started analysis task #" + taskName + " that is responsible for " + commits.size() + " commits.");
            File workDir = new File(parentDir, "run-" + taskName);
            File propertiesFile = new File(workDir, parentPropertiesFile.getName());
            File splDir = new File(workDir, splName);
            // Load the config
            Configuration config;
            try {
                LOGGER.logDebug("Setting up configuration for " + propertiesFile);
                config = new Configuration(propertiesFile);
                DefaultSettings.registerAllSettings(config);
                config.registerSetting(CommitUsabilityAnalysis.WORK_DIR);
                // Change the paths to the required directories
                config.setValue(CommitUsabilityAnalysis.WORK_DIR, workDir.getAbsolutePath());
                config.setValue(DefaultSettings.SOURCE_TREE, new File(workDir, splName));
                config.setValue(DefaultSettings.RESOURCE_DIR, new File(workDir, "res"));
                config.setValue(DefaultSettings.OUTPUT_DIR, new File(workDir, "output"));
                config.setValue(DefaultSettings.PLUGINS_DIR, new File(workDir, "plugins"));
                config.setValue(DefaultSettings.CACHE_DIR, new File(workDir, "cache"));
                config.setValue(DefaultSettings.LOG_DIR, new File(workDir, "log"));
            } catch (SetUpException e) {
                config = null;
                LOGGER.logError("Invalid configuration detected:", e.getMessage());
                quitOnError();
            }


            for (RevCommit commit : commits) {
                LOGGER.logInfo("Started analysis of commit " + commit.getName() + " in task #" + taskName);

                // Make sure the directory is not blocked
                checkBlocker(splDir);

                // Check out the next commit
                EXECUTOR.execute("git checkout " + commit.getName(), splDir);

                // Block the directory
                createBlocker(splDir);

                // Start the analysis pipeline
                LOGGER.logInfo("Start executing KernelHaven with configuration file " + propertiesFile.getPath());
                try {
                    PipelineConfigurator.instance().init(config);
                } catch (SetUpException e) {
                    LOGGER.logError("Invalid configuration detected:", e.getMessage());
                    quitOnError();
                }

                // Execute the analysis pipeline
                LOGGER.setLevel(Objects.requireNonNull(config).getValue(DefaultSettings.LOG_LEVEL));
                PipelineConfigurator.instance().execute();
                LOGGER.setLevel(LOG_LEVEL);
                LOGGER.logInfo("KernelHaven execution finished.");
                // TODO: Look for bug here
                if (Objects.requireNonNull(config).getValue(COLLECT_OUTPUT)) {
                    LOGGER.logInfo("Moving result to common output directory.");
                    File collection_dir = new File(new File(parentDir, "output"), commit.getName());
                    if (collection_dir.mkdir()) {
                       LOGGER.logDebug("Created sub-dir for collecting the results for commit " + commit.getName());
                    }
                    // Move the results of the analysis to the collected output directory according to the current commit
                    EXECUTOR.execute("mv ./output/* ../output/" + commit.getName() + "/", workDir);
                }
                LOGGER.logInfo("Starting clean up...");
                // We have to set the name again because KernelHaven changes it
                Thread.currentThread().setName(threadName);
                EXECUTOR.execute("make clean", splDir);

                // Delete the blocker
                deleteBlocker(splDir);
            }
        }

        private void createBlocker(File dir) {
            LOGGER.logInfo("Blocking directory " + dir);
            File blocker = new File(dir, "BLOCKER.txt");
            try {
                if (!blocker.createNewFile()) {
                    LOGGER.logError("Was not able to create blocker file!");
                    quitOnError();
                } else {
                    LOGGER.logInfo("BLOCKED - OK");
                }
            } catch (IOException e) {
                LOGGER.logException("Exception was thrown upon creating blocker file: ", e);
            }
        }

        private void checkBlocker(File dir) {
            LOGGER.logInfo("Checking block of directory " + dir);
            File blocker = new File(dir, "BLOCKER.txt");
            if (blocker.exists()) {
                LOGGER.logError("The SPL directory is blocked by another task! This indicates a bug in the " +
                        "implementation of multi-threading.");
                quitOnError();
            } else {
                LOGGER.logInfo("NO BLOCK FOUND - OK");
            }
        }

        private void deleteBlocker(File dir) {
            LOGGER.logInfo("Removing block of directory " + dir);
            File blocker = new File(dir, "BLOCKER.txt");
            if (!blocker.delete()) {
                LOGGER.logError("Was not able to delete blocker file!");
                quitOnError();
            } else {
                LOGGER.logInfo("BLOCK REMOVED - OK");
            }
        }
    }
}
