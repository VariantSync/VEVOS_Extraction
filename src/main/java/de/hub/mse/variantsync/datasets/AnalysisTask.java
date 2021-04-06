package de.hub.mse.variantsync.datasets;

import de.hub.mse.variantsync.datasets.kh.CommitUsabilityAnalysis;
import de.hub.mse.variantsync.datasets.util.ConfigManipulator;
import de.hub.mse.variantsync.datasets.util.ShellExecutor;
import net.ssehub.kernel_haven.PipelineConfigurator;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.util.Logger;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static de.hub.mse.variantsync.datasets.LinuxHistoryAnalysis.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class AnalysisTask implements Runnable {
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
    private static int existingTasksCount = 0;
    private final int taskNumber;
    private final File parentDir;
    private final List<RevCommit> commits;
    private final File parentPropertiesFile;
    private final String splName;
    private final EResultCollection collectOutput;

    public AnalysisTask(List<RevCommit> commits, File parentDir, File propertiesFile, String splName, Configuration config) {
        this.commits = commits;
        this.parentDir = parentDir;
        this.parentPropertiesFile = propertiesFile;
        this.splName = splName;
        this.taskNumber = existingTasksCount++;
        this.collectOutput = config.getValue(RESULT_COLLECTION_TYPE);
    }

    @Override
    public void run() {
        String taskName = String.valueOf(taskNumber);
        String threadName = Thread.currentThread().getName();
        LOGGER.logStatus("Started analysis task #" + taskName + " that is responsible for " + commits.size() + " commits.");
        File workDir = new File(parentDir, "run-" + taskName);
        File propertiesFile = new File(workDir, parentPropertiesFile.getName());
        File splDir = new File(workDir, splName);
        LOGGER.logInfo("Work Dir: " + workDir);
        LOGGER.logInfo("Properties File: " + propertiesFile);
        LOGGER.logInfo("SPL Dir: " + splDir);
        // Load the config
        try {
            Configuration config = null;
            config = new Configuration(propertiesFile);
            config.registerSetting(DefaultSettings.LOG_LEVEL);
            LOGGER.setLevel(config.getValue(DefaultSettings.LOG_LEVEL));
            prepareConfig(workDir, propertiesFile);
        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            quitOnError();
        }

        for (RevCommit commit : commits) {
            LOGGER.logStatus("Started analysis of commit " + commit.getName() + " in task #" + taskName);

            // Make sure the directory is not blocked
            checkBlocker(splDir);

            // Check out the next commit
            EXECUTOR.execute("git checkout " + commit.getName(), splDir);

            // Block the directory
            createBlocker(splDir);

            // Start the analysis pipeline
            LOGGER.logInfo("Start executing KernelHaven with configuration file " + propertiesFile.getPath());
            // TODO: Multi-threading breaks probably due to this singleton here! Fix it!
            EXECUTOR.execute("java -jar KernelHaven.jar " + propertiesFile.getAbsolutePath(), workDir);
            Thread.currentThread().setName(threadName);
            LOGGER.logInfo("KernelHaven execution finished.");

            if (collectOutput == EResultCollection.COLLECTED_DIRECTORIES) {
                Path pathToTargetDir = Paths.get(parentDir.getAbsolutePath(), "output", commit.getName());
                moveResultsToDirectory(workDir, pathToTargetDir, commit.getName());
            } else if (collectOutput == EResultCollection.REPOSITORY) {
                Path pathToTargetDir = Paths.get(parentDir.getAbsolutePath(), "output");
                // This part need to be synchronized or it might break if multiple tasks are used
                synchronized (AnalysisTask.class) {
                    moveResultsToDirectory(workDir, pathToTargetDir, commit.getName());
                    commitResults(pathToTargetDir.toFile(), commit);
                }
            }

            LOGGER.logInfo("Starting clean up...");
            // We have to set the name again because KernelHaven changes it
            EXECUTOR.execute("make clean", splDir);

            // Delete the blocker
            deleteBlocker(splDir);
        }
    }

    private void prepareConfig(File workDir, File propertiesFile) throws SetUpException {
        ConfigManipulator manipulator = new ConfigManipulator(propertiesFile);
        // Change the paths to the required directories
        manipulator.put(CommitUsabilityAnalysis.WORK_DIR.getKey(), workDir.getAbsolutePath());
        manipulator.put(DefaultSettings.SOURCE_TREE.getKey(), new File(workDir, splName).getAbsolutePath());
        manipulator.put(DefaultSettings.RESOURCE_DIR.getKey(), new File(workDir, "res").getAbsolutePath());
        manipulator.put(DefaultSettings.OUTPUT_DIR.getKey(), new File(workDir, "output").getAbsolutePath());
        manipulator.put(DefaultSettings.PLUGINS_DIR.getKey(), new File(workDir, "plugins").getAbsolutePath());
        manipulator.put(DefaultSettings.CACHE_DIR.getKey(), new File(workDir, "cache").getAbsolutePath());
        manipulator.put(DefaultSettings.LOG_DIR.getKey(), new File(workDir, "log").getAbsolutePath());
        LOGGER.logInfo("Set up configuration for " + propertiesFile);
        LOGGER.logInfo("Saving configuration " + propertiesFile);
        manipulator.writeToFile();
    }

    private static void moveResultsToDirectory(File workDir, Path pathToTargetDir, String commitId) {
        LOGGER.logInfo("Moving result to common output directory.");
        File collection_dir = pathToTargetDir.toFile();
        if (collection_dir.mkdir()) {
            LOGGER.logDebug("Created sub-dir for collecting the results for commit " + collection_dir.getName());
        }

        // Move the results of the analysis to the collected output directory according to the current commit
        File outputDir = new File(workDir, "output");
        File[] resultFiles = outputDir.listFiles((dir, name) -> name.contains("Blocks.csv"));
        if (resultFiles == null || resultFiles.length == 0) {
            LOGGER.logWarning("Found no result file in " + outputDir);
            EXECUTOR.execute("ls -al", outputDir);
            // Try once more after a timeout
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                LOGGER.logException("Exception while sleeping: ", e);
            }
            resultFiles = outputDir.listFiles((dir, name) -> name.contains("Blocks.csv"));
            if (resultFiles == null || resultFiles.length == 0) {
                LOGGER.logError("NO RESULT FILE IN " + outputDir.getAbsolutePath());
                logError(pathToTargetDir, commitId);
            } else if (resultFiles.length == 1) {
                try {
                    LOGGER.logInfo("Moving results from " + resultFiles[0].getAbsolutePath() + " to " + pathToTargetDir);
                    Files.move(resultFiles[0].toPath(), Paths.get(pathToTargetDir.toString(), "code-variability.csv"), REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.logException("Was not able to move the result file of the analysis: ", e);
                }
            } else {
                LOGGER.logError("FOUND MORE THAN ONE RESULT FILE IN " + outputDir.getAbsolutePath());
                logError(pathToTargetDir, commitId);
            }
        }

        // Move the cache of the extractors to the collected output directory
        LOGGER.logInfo("Moving extractor cache to common output directory.");
        File vmCache = new File(new File(workDir, "cache"), "vmCache.json");
        if (vmCache.exists()) {
            try {
                LOGGER.logInfo("Moving cache from " + vmCache.getAbsolutePath() + " to " + pathToTargetDir);
                Files.move(vmCache.toPath(), Paths.get(pathToTargetDir.toString(), "variability-model.json"), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the cached variability model: ", e);
                logError(pathToTargetDir, commitId);
            }
        } else {
            LOGGER.logError("NO VARIABILITY MODEL EXTRACTED TO " + vmCache);
            logError(pathToTargetDir, commitId);
        }
        LOGGER.logInfo("...done.");
    }

    private static void commitResults(File workingDirectory, RevCommit originalCommit) {
        // Save the commit which was just processed
        EXECUTOR.execute("echo \"" + originalCommit.getName() + "\" > CURRENT_COMMIT.txt", workingDirectory);
        // Add the changes to the results
        EXECUTOR.execute("git add .", workingDirectory);
        // Commit the changes
        EXECUTOR.execute("git commit -m \"" + originalCommit.getName() + "\"", workingDirectory);
        // Push the changes
        EXECUTOR.execute("git push origin main", workingDirectory);
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

    private static void logError(Path dir, String commitId) {
        EXECUTOR.execute("echo \"" + commitId + " \" >> ERROR.txt", dir.toFile());
    }
}