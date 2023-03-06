package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.vevos.extraction.util.ShellExecutor;
import org.variantsync.vevos.extraction.util.GitUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Extraction {
    private static File propertiesFile;
    public static final String LOG_LEVEL_MAIN
            = "log.level.main";
    public static final String PATH_TO_SOURCE_REPO
            = "source_tree";
    public static final String WORKING_DIR_NAME
            = "working_dir_name";
    public static final String URL_OF_SOURCE_REPO
            = "source_repo_url";
    public static final String NUMBER_OF_THREADS
            = "analysis.number_of_tasks";
    public static final String RESULT_REPO_URL
            = "result.repo.url";
    public static final String RESULT_REPO_COMMITTER_NAME
            = "result.repo.committer.name";
    public static final String RESULT_REPO_COMMITTER_EMAIL
            = "result.repo.committer.email";
    public static final String EXTRACTION_TIMEOUT
            = "extraction.timeout";
    public static final String ANALYSIS_CLASS
            = "analysis.class";
    private static final ShellExecutor EXECUTOR = new ShellExecutor();

    public static void main(String... args) throws IOException, GitAPIException {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        checkOS(isWindows);
        Logger.info("Starting SPL history analysis.");

        // Parse the arguments
        propertiesFile = getPropertiesFile(args);
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
        Properties properties = getProperties(propertiesFile);

        // Update the repo information if necessary
        if (properties.getProperty(PATH_TO_SOURCE_REPO).equals("TBD")) {
            Logger.info("Expecting repo link as first argument...");
            Logger.info("Provided repo link: " + repo);

            String[] parts = repo.split("/");
            String repoName = parts[parts.length-1];
            repoName = repoName.split("\\.")[0];
            Logger.info("Identified repo name: " + repoName);

            properties.setProperty(PATH_TO_SOURCE_REPO, "./" + repoName);
            properties.setProperty(URL_OF_SOURCE_REPO, repo);
        }

        // Clone the SPL if necessary and return the File that points to the directory
        File splDir = setUpSPLDirectory(properties);
        // Load git history
        List<RevCommit> commits = GitUtil.getCommits(splDir, firstCommit, lastCommit);
        Logger.info("Identified " + commits.size() + " commit(s) for processing.");

        // Number of threats is the Minimum of the specified number and the number of commits to process
        int numberOfThreads = Math.min(Integer.parseInt(properties.getProperty(NUMBER_OF_THREADS)),commits.size());

        // Create the directories for each task running the analysis
        File workingDirectory = setUpWorkingDirectory(properties, splDir, numberOfThreads);

        Logger.info("Starting thread pool with " + numberOfThreads + " threads.");
        ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);
        Logger.info("Splitting commits into " + numberOfThreads + " subset(s).");
        List<List<RevCommit>> commitSubsets = GitUtil.splitCommitsIntoSubsets(commits, numberOfThreads);
        Logger.info("...done.");
        // Create a task for each commit subset and submit it to the thread pool
        int count = 0;
        Logger.info("Scheduling tasks...");
        for (List<RevCommit> commitSubset : commitSubsets) {
            count += commitSubset.size();
            threadPool.submit(new AnalysisTask(commitSubset, workingDirectory, propertiesFile, splDir.getName(), Long.parseLong(properties.getProperty(EXTRACTION_TIMEOUT))));
        }
        Logger.info("all " + commitSubsets.size() + " tasks scheduled.");
        threadPool.shutdown();
        if (count != commits.size()) {
            Logger.error("Subsets not created correctly: ",
                    new IllegalStateException("Expected the subsets to contain " + commits.size() +
                            " commits but only processed " + count + " commits."));
        }
        Logger.info("All tasks submitted...");
        Logger.info("Submitted a total of " + commits.size() + " commits.");
    }

    private static void checkOS(boolean isWindows) {
        Logger.info("OS NAME: " + System.getProperty("os.name"));
        if (isWindows) {
            Logger.error("Running the analysis under Windows is not supported as the Linux/BusyBox sources are not" +
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

    private static File setUpSPLDirectory(Properties config) {
        // Clone the SPL repo if required
        File splDir = new File(config.getProperty(PATH_TO_SOURCE_REPO));
        if (splDir.exists()) {
            Logger.info("Directory with SPL sources found.");
        } else {
            Logger.info("Cloning SPL...");
            Logger.warn("Depending on the download speed this might take several minutes.");
            Logger.warn("Consider cloning the repository manually for a better estimate of the download time.");
            File executionDir = splDir.getParentFile() == null ? new File(System.getProperty("user.dir")) : splDir.getParentFile();

            if (!EXECUTOR.execute("git clone " + config.getProperty(URL_OF_SOURCE_REPO), executionDir)) {
                quitOnError();
            }
        }
        return splDir;
    }

    private static File setUpWorkingDirectory(Properties config, File splDir, int numberOfThreads) {
        File workingDirectory = new File(System.getProperty("user.dir"));
        workingDirectory = new File(workingDirectory, config.getProperty(WORKING_DIR_NAME));
        Logger.info("Working Directory: " + workingDirectory);
        Logger.info("Setting up working directory...");

        // Create the directory where the results of the individual runs are collected
        File overallOutputDirectory = new File(workingDirectory, "output");
        if (!overallOutputDirectory.exists()) {
            if (overallOutputDirectory.mkdirs()) {
                Logger.info("Created common output directory.");
            }
        }


        for (int i = 0; i < numberOfThreads; i++) {
            File subDir = new File(workingDirectory, "run-" + i);
            // Create the path to the working directory of each task
            if (!subDir.exists()) {
                if (subDir.mkdirs()) {
                    Logger.debug("Created directory for task number #" + i);
                }
            }
            // Create the directories that are expected by KernelHaven
            createKernelHavenDirs(subDir);
            // Copy the SPL sources to the subDir, so that it can be analyzed locally
            File targetFile = new File(subDir, splDir.getName());
            if (!targetFile.exists()) {
                Logger.info("Copying the SPL directory to the sub directory for task #" + i + ".");
                EXECUTOR.execute("cp -rf " + splDir.getAbsolutePath() + " .", subDir);
            } else {
                Logger.debug("SPL directory exists in sub dir.");
            }
            // Copy the properties file to the subDir
            Logger.debug("Copying the properties file to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f " + propertiesFile.getAbsolutePath() + " .", subDir);
            // Copy the KernelHaven plugins to the sub-dir
            Logger.debug("Copying the KernelHaven plugin to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f ../Extraction-* " + subDir + "/plugins/", workingDirectory);
            //EXECUTOR.execute("cp -f ../plugins/* " + subDir + "/plugins/", workingDirectory);
            // Copy KernelHaven to the sub-dir
            Logger.debug("Copying KernelHaven to the sub directory for task #" + i + ".");
            EXECUTOR.execute("cp -f ../KernelHaven.jar " + subDir + "/", workingDirectory);
        }
        Logger.info("...done with setting up working directory.");
        return workingDirectory;
    }

    private static void createKernelHavenDirs(File taskDirectory) {
        Logger.info("Creating directories required by KernelHaven...");
        if (new File(taskDirectory, "res").mkdirs()) {
            Logger.debug("Resource directory created.");
        }
        if (new File(taskDirectory, "output").mkdirs()) {
            Logger.debug("Output directory created.");
        }
        if (new File(taskDirectory, "plugins").mkdirs()) {
            Logger.debug("Plugins directory created.");
        }
        if (new File(taskDirectory, "cache").mkdirs()) {
            Logger.debug("Cache directory created.");
        }
        if (new File(taskDirectory, "log").mkdirs()) {
            Logger.debug("Log directory created.");
        }
        Logger.info("...done.");
    }

    public static void quitOnError() {
        Logger.error("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
    }


}
