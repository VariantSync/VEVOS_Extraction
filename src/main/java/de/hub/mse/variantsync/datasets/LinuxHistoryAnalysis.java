package de.hub.mse.variantsync.datasets;

import de.hub.mse.variantsync.datasets.kh.ExtractorAnalysis;
import de.hub.mse.variantsync.datasets.util.CountedThread;
import de.hub.mse.variantsync.datasets.util.CountingThreadFactory;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LinuxHistoryAnalysis {

    public static final @NonNull Setting<@Nullable String> PATH_TO_LINUX
            = new Setting<>("source_tree", Setting.Type.STRING, true, null, "" +
            "Path to the linux sources.");
    public static final @NonNull Setting<@Nullable Integer> NUMBER_OF_THREADS
            = new Setting<>("analysis.number_of_threads", Setting.Type.INTEGER, false, "1", "" +
            "The number of threads that are used to run the analysis. The linux sources are copied once for each thread.");
    protected static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;
    private static final Logger LOGGER = Logger.get();
    private static final String LINUX_REPO = "https://github.com/torvalds/linux.git";
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
    private static final String PATH_TO_RESULT = "output/analysis_result.csv";

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        LOGGER.setLevel(LOG_LEVEL);
        checkOS(isWindows);
        LOGGER.logInfo("Starting Linux history analysis.");

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
        // Clone linux if necessary and return the File that points to the directory
        File linuxDir = setUpLinuxDirectory(config);
        // Create the directories for each thread running the analysis
        File workingDirectory = setUpWorkingDirectory(config, linuxDir);
        // Load git history
        List<RevCommit> commits = getCommits(firstCommit, lastCommit, linuxDir);

        int numberOfThreads = config.getValue(NUMBER_OF_THREADS);
        LOGGER.logInfo("Starting thread pool with " + numberOfThreads + " threads.");
        ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads, new CountingThreadFactory(LOGGER));

        List<List<RevCommit>> commitSubsets = splitCommitsIntoSubsets(commits, numberOfThreads);

        // Create a task for each commit and submit it to the thread pool
        int count = 0;
        for (List<RevCommit> commitSubset : commitSubsets) {
            count += commitSubset.size();
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile));
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
            config.registerSetting(PATH_TO_LINUX);
            config.registerSetting(NUMBER_OF_THREADS);
        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            quitOnError();
        }
        return config;
    }

    private static File setUpLinuxDirectory(Configuration config) {
        // Clone linux if required
        File linuxDir = new File(config.getValue(PATH_TO_LINUX));
        if (linuxDir.exists()) {
            LOGGER.logInfo("Directory with linux sources found.");
        } else {
            LOGGER.logInfo("Cloning linux...");
            LOGGER.logWarning("Depending on the download speed this might take several minutes.");
            LOGGER.logWarning("Consider cloning the repository manually for a better estimate of the download time.");
            if (!EXECUTOR.execute("git clone " + LINUX_REPO, linuxDir.getParentFile())) {
                quitOnError();
            }
        }
        return linuxDir;
    }

    private static File setUpWorkingDirectory(Configuration config, File linuxDir) {
        int numberOfThreads = config.getValue(NUMBER_OF_THREADS);
        File workingDirectory = new File(System.getProperty("user.dir"));
        workingDirectory = new File(workingDirectory, "commit-analysis");
        LOGGER.logInfo("Working Directory: " + workingDirectory);
        LOGGER.logInfo("Setting up working directory...");
        for (int i = 0; i < numberOfThreads; i++) {
            File subDir = new File(workingDirectory, "run-" + i);
            // Create the path to the working directory of each thread
            if (!subDir.exists()) {
                if (subDir.mkdirs()) {
                    LOGGER.logDebug("Created directory for thread number #" + i);
                }
            }
            // Create the directories that are expected by KernelHaven
            createKernelHavenDirs(subDir);
            // Create the result csv file for the analysis
            createResultFile(subDir);
            // Copy linux to the subDir, so that it can be analyzed locally
            if (!new File(subDir, "linux").exists()) {
                EXECUTOR.execute("cp -rf " + linuxDir.getAbsolutePath() + " .", subDir);
            }
            // Copy the properties file to the subDir
            EXECUTOR.execute("cp -f " + config.getPropertyFile().getAbsolutePath() + " .", subDir);
        }
        LOGGER.logInfo("...done with setting up working directory.");
        return workingDirectory;
    }

    private static void createKernelHavenDirs(File threadDirectory) {
        LOGGER.logInfo("Creating directories required by KernelHaven...");
        if (new File(threadDirectory, "res").mkdirs()) {
            LOGGER.logDebug("Resource directory created.");
        }
        if (new File(threadDirectory, "output").mkdirs()) {
            LOGGER.logDebug("Output directory created.");
        }
        if (new File(threadDirectory, "plugins").mkdirs()) {
            LOGGER.logDebug("Plugins directory created.");
        }
        if (new File(threadDirectory, "cache").mkdirs()) {
            LOGGER.logDebug("Cache directory created.");
        }
        if (new File(threadDirectory, "log").mkdirs()) {
            LOGGER.logDebug("Log directory created.");
        }
        LOGGER.logInfo("...done.");
    }


    // TODO: Move logic for writing the results to analysis pipeline
    private static void createResultFile(File workingDirectory) {
        // Initialize the result file
        File result_file = new File(workingDirectory, PATH_TO_RESULT);
        LOGGER.logInfo("Initializing the result file " + result_file + "...");
        if (result_file.getParentFile().mkdirs()) {
            LOGGER.logInfo("...created parent folders...");

        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(result_file))) {
            // Write the header
            String line = String.format("%s,%s,%s,%s,%s,%s,%s,%s", "THREAD_ID", "COMMIT", "OVERALL_SUCCESS", "CM", "BM", "VM", "BM_SIZE", "VM_SIZE");
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            LOGGER.logException("Exception while creating result file", e);
        }
    }

    private static List<RevCommit> getCommits(String firstCommit, String lastCommit, File linuxDir) throws IOException, GitAPIException {
        Iterable<RevCommit> commitIterable = getCommits(linuxDir);

        List<RevCommit> commits = new LinkedList<>();
        if (firstCommit != null && lastCommit != null) {
            LOGGER.logInfo("Commit range specified...filtering commits.");
            // Filter all commits not in the specified range of commits
            boolean inRange = false;
            for (RevCommit commit : commitIterable) {
                if (commit.getName().equals(firstCommit) || commit.getName().equals(lastCommit)) {
                    // Invert the value upon reaching one of the boundaries
                    inRange = !inRange;
                }
                if (inRange) {
                    commits.add(commit);
                }
            }
            LOGGER.logInfo("" + commits.size() + " remain for analysis.");
        } else {
            // Add all commits, if not commit range was specified
            commitIterable.forEach(commits::add);
        }
        return commits;
    }

    private static Iterable<RevCommit> getCommits(File linuxDir) throws IOException, GitAPIException {
        LOGGER.logDebug("Initializing git repo...");
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(linuxDir, ".git"))
                .build();
        Git git = new Git(repository);
        LOGGER.logDebug("...done.");
        LOGGER.logDebug("Retrieving git history...");
        return git.log().all().call();
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
        private final File parentDir;
        private final List<RevCommit> commits;
        private final File parentPropertiesFile;

        public AnalysisTask(List<RevCommit> commits, File parentDir, File propertiesFile) {
            this.commits = commits;
            this.parentDir = parentDir;
            this.parentPropertiesFile = propertiesFile;
            LOGGER.setLevel(LOG_LEVEL);
        }

        @Override
        public void run() {
            String threadName = String.valueOf(((CountedThread) Thread.currentThread()).getInstanceNumber());
            LOGGER.logInfo("Started analysis task that is responsible for " + commits.size() + " commits.");
            File workDir = new File(parentDir, "run-" + threadName);
            File propertiesFile = new File(workDir, parentPropertiesFile.getName());
            // Load the config
            Configuration config;
            try {
                LOGGER.logDebug("Setting up configuration for " + propertiesFile);
                config = new Configuration(propertiesFile);
                DefaultSettings.registerAllSettings(config);
                config.registerSetting(ExtractorAnalysis.WORK_DIR);
                // Change the paths to the required directories
                config.setValue(ExtractorAnalysis.WORK_DIR, workDir.getAbsolutePath());
                config.setValue(DefaultSettings.SOURCE_TREE, new File(workDir, "linux"));
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

            File linuxDir = new File(workDir, "linux");

            for (RevCommit commit : commits) {
                LOGGER.logInfo("Started analysis of commit " + commit.getName() + " in thread " + threadName);

                // Make sure the directory is not blocked
                checkBlocker(linuxDir);

                // Check out the next commit
                EXECUTOR.execute("git checkout " + commit.getName(), linuxDir);

                // Block the directory
                createBlocker(linuxDir);

                // Start the analysis pipeline
                LOGGER.logInfo("Start executing KernelHaven with configuration file " + propertiesFile.getPath());
                try {
                    PipelineConfigurator.instance().init(config);
                } catch (SetUpException e) {
                    LOGGER.logError("Invalid configuration detected:", e.getMessage());
                    quitOnError();
                }
                PipelineConfigurator.instance().execute();
                // We have to set the name again because KernelHaven changes it
                Thread.currentThread().setName(threadName);
                EXECUTOR.execute("make clean", linuxDir);

                // Delete the blocker
                deleteBlocker(linuxDir);
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
                LOGGER.logError("The linux directory is blocked by another thread! This indicates a bug in the " +
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
