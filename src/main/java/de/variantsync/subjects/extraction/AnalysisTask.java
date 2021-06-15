package de.variantsync.subjects.extraction;

import de.variantsync.subjects.extraction.kh.CommitUsabilityAnalysis;
import de.variantsync.subjects.extraction.util.ConfigManipulator;
import de.variantsync.subjects.extraction.util.ShellExecutor;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.util.Logger;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.variantsync.subjects.extraction.VariabilityExtraction.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class AnalysisTask implements Runnable {
    private final static String SUCCESS_COMMIT_FILE = "SUCCESS_COMMITS.txt";
    private final static String ERROR_COMMIT_FILE = "ERROR_COMMITS.txt";
    private final static String INCOMPLETE_PC_COMMIT_FILE = "PARTIAL_SUCCESS_COMMITS.txt";
    private static final String COMMIT_PARENTS_FILE = "PARENTS.txt";
    private static final String COMMIT_MESSAGE_FILE = "MESSAGE.txt";
    private static final Logger LOGGER = Logger.get();
    private static final ShellExecutor EXECUTOR = new ShellExecutor(LOGGER);
    private static int existingTasksCount = 0;
    private final int taskNumber;
    private final File parentDir;
    private final List<RevCommit> commits;
    private final File parentPropertiesFile;
    private final String splName;
    private final EResultCollection collectOutput;
    private final long timeout;

    public AnalysisTask(List<RevCommit> commits, File parentDir, File propertiesFile, String splName, Configuration config, long timeout) {
        this.commits = commits;
        this.parentDir = parentDir;
        this.parentPropertiesFile = propertiesFile;
        this.splName = splName;
        this.taskNumber = existingTasksCount++;
        this.collectOutput = config.getValue(RESULT_COLLECTION_TYPE);
        this.timeout = timeout;
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
            Configuration config;
            config = new Configuration(propertiesFile);
            config.registerSetting(LOG_LEVEL_MAIN);
            config.registerSetting(EXTRACTION_TIMEOUT);
            LOGGER.setLevel(config.getValue(LOG_LEVEL_MAIN));
            prepareConfig(workDir, propertiesFile);
        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            return;
        }

        int count = 1;
        for (RevCommit commit : commits) {
            LOGGER.logStatus("Started analysis of commit " + commit.getName() + " in task #" + taskName);
            LOGGER.logStatus("Commit number " + count + " of " + commits.size());
            // Make sure the directory is not blocked
            checkBlocker(splDir);

            // Check out the next commit
            EXECUTOR.execute("git checkout --force " + commit.getName(), splDir);

            // Block the directory
            createBlocker(splDir);

            File prepareFail = new File(splDir, "PREPARE_FAILED");
            if (prepareFail.exists()) {
                LOGGER.logError("The prepare fail flag must not exist yet!");
            }

            // Adjust the Makefiles in the project to remove all error flags that can cause exceptions
            // when using newer libraries and compilers
            adjustMakefiles(splDir);

            // Start the analysis pipeline
            LOGGER.logStatus("Start executing KernelHaven with configuration file " + propertiesFile.getPath());
            EXECUTOR.execute("java -jar KernelHaven.jar " + propertiesFile.getAbsolutePath(), workDir, timeout, TimeUnit.SECONDS);
            Thread.currentThread().setName(threadName);
            LOGGER.logStatus("KernelHaven execution finished.");

            if (collectOutput == EResultCollection.COLLECTED_DIRECTORIES) {
                Path pathToTargetDir = Paths.get(parentDir.getAbsolutePath(), "output", commit.getName());
                synchronized (AnalysisTask.class) {
                    moveResultsToDirectory(workDir, pathToTargetDir, pathToTargetDir.getParent().getParent(), commit, prepareFail);
                }
            } else if (collectOutput == EResultCollection.LOCAL_REPOSITORY || collectOutput == EResultCollection.REMOTE_REPOSITORY) {
                Path pathToTargetDir = Paths.get(parentDir.getAbsolutePath(), "output");
                // This part need to be synchronized or it might break if multiple tasks are used
                synchronized (AnalysisTask.class) {
                    moveResultsToDirectory(workDir, pathToTargetDir, pathToTargetDir, commit, prepareFail);
                    commitResults(pathToTargetDir.toFile(), commit);
                    if (collectOutput == EResultCollection.REMOTE_REPOSITORY) {
                        LOGGER.logStatus("Pushing result to remote repository.");
                        // Push the changes
                        EXECUTOR.execute("git push origin main", pathToTargetDir.toFile());
                    }
                }
            }

            // Delete the blocker
            deleteBlocker(splDir);

            LOGGER.logStatus("Starting clean up...");

            // Restore the makefiles
            EXECUTOR.execute("git restore .", splDir);

            // Remove all untracked files and directories
            EXECUTOR.execute("git clean -fdx", splDir);

            // Remove the temporary directory created by busyboot
            // TODO: Move this to a customized variant of busyboot
            File tempBusyBootDirectory = new File(splDir.getParentFile(), "" + splDir.getName() + "UnchangedCopy");
            if (tempBusyBootDirectory.exists()) {
                EXECUTOR.execute("rm -rf ../" + splDir.getName() + "UnchangedCopy", splDir);
            }
            count++;
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

    private static void moveResultsToDirectory(File workDir, Path pathToTargetDir, Path pathToMetaDir, RevCommit commit, File prepareFail) {
        String commitId = commit.getName();
        LOGGER.logStatus("Moving result to common output directory.");
        File collection_dir = pathToTargetDir.toFile();
        if (collection_dir.mkdir()) {
            LOGGER.logDebug("Created sub-dir for collecting the results for commit " + collection_dir.getName());
        }

        // Move the results of the analysis to the collected output directory according to the current commit
        File outputDir = new File(workDir, "output");
        File[] resultFiles = outputDir.listFiles((dir, name) -> name.contains("Blocks.csv"));
        boolean hasError = false;

        if (resultFiles == null || resultFiles.length == 0) {
            LOGGER.logError("NO RESULT FILE IN " + outputDir.getAbsolutePath());
            hasError = true;
        } else if (resultFiles.length == 1) {
            try {
                LOGGER.logInfo("Moving results from " + resultFiles[0].getAbsolutePath() + " to " + pathToTargetDir);
                Files.move(resultFiles[0].toPath(), Paths.get(pathToTargetDir.toString(), "code-variability.csv"), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the result file of the analysis: ", e);
            }
        } else {
            LOGGER.logError("FOUND MORE THAN ONE RESULT FILE IN " + outputDir.getAbsolutePath());
            hasError = true;
        }

        // Move the cache of the extractors to the collected output directory
        LOGGER.logStatus("Moving extractor cache to common output directory.");
        File vmCache = new File(new File(workDir, "cache"), "vmCache.json");
        if (vmCache.exists()) {
            try {
                LOGGER.logInfo("Moving cache from " + vmCache.getAbsolutePath() + " to " + pathToTargetDir);
                Files.move(vmCache.toPath(), Paths.get(pathToTargetDir.toString(), "variability-model.json"), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the cached variability model: ", e);
                hasError = true;
            }
        } else {
            LOGGER.logError("NO VARIABILITY MODEL EXTRACTED TO " + vmCache);
            hasError = true;
        }

        // Move the log to the common output directory
        LOGGER.logStatus("Moving KernelHaven log to common output directory");
        File logDir = new File(workDir, "log");
        File[] logFiles = logDir.listFiles((dir, name) -> name.contains(".log"));
        File targetLogDir = new File(pathToTargetDir.toFile().getParentFile().getParentFile(), "log");
        if(targetLogDir.mkdir()) {
            LOGGER.logInfo("Log dir created under " + targetLogDir);
        }
        if (logFiles == null || logFiles.length == 0) {
            LOGGER.logWarning("NO LOG FILE IN " + logDir.getAbsolutePath());
        } else {
            if (logFiles.length > 1) {
                LOGGER.logWarning("FOUND MORE THAN ONE LOG FILE IN " + outputDir.getAbsolutePath());
            }
            try {
                LOGGER.logInfo("Moving log from " + logFiles[0].getAbsolutePath() + " to " + targetLogDir);
                Files.move(logFiles[0].toPath(), Paths.get(targetLogDir.getAbsolutePath(), commitId + ".log"));
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the log file of the analysis: ", e);
            }
        }

        if (hasError) {
            EXECUTOR.execute("echo \"" + commitId + " \" >> " + ERROR_COMMIT_FILE, pathToMetaDir.toFile());
            if (Objects.requireNonNull(collection_dir.listFiles()).length == 0) {
                try {
                    Files.delete(collection_dir.toPath());
                } catch (IOException e) {
                    LOGGER.logError("Was not able to delete the result collection directory " + collection_dir);
                }
            }
        } else {
            Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
            parentIds.ifPresent(s -> EXECUTOR.execute("echo " + s + " >> " + COMMIT_PARENTS_FILE, collection_dir));
            EXECUTOR.execute("echo \"" + commit.getFullMessage() + "\" >> " + COMMIT_MESSAGE_FILE, collection_dir);
            if (prepareFail.exists()) {
                LOGGER.logWarning("The 'make allyesconfig prepare' call failed, the extracted presence conditions may not be correct!");
                EXECUTOR.execute("echo \"" + commitId + " \" >> " + INCOMPLETE_PC_COMMIT_FILE, pathToMetaDir.toFile());
            } else {
                EXECUTOR.execute("echo \"" + commitId + " \" >> " + SUCCESS_COMMIT_FILE, pathToMetaDir.toFile());
            }
        }

        LOGGER.logInfo("...done.");
    }

    private static void commitResults(File workingDirectory, RevCommit originalCommit) {
        LOGGER.logStatus("Committing results to repository.");
        // Save the commit which was just processed
        EXECUTOR.execute("echo \"" + originalCommit.getName() + "\" > CURRENT_COMMIT.txt", workingDirectory);
        // Save the message of the commit which was just processed
        EXECUTOR.execute("echo \"" + originalCommit.getFullMessage() + "\" > COMMIT_MESSAGE.txt", workingDirectory);
        // Add the changes to the results
        EXECUTOR.execute("git add .", workingDirectory);
        // Commit the changes
        EXECUTOR.execute("git commit -m \"" + originalCommit.getName() + "\"", workingDirectory);
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

    private void adjustMakefiles(File dir) {
        LOGGER.logInfo("Adjusting Makefiles in " + dir);
        try {
            final Stream<Path> makefiles = Files.find(dir.toPath(),
                    Integer.MAX_VALUE,
                    (path, basicFileAttributes) -> path.toFile().isFile() && path.toFile().getName().contains("Makefile"),
                    FileVisitOption.FOLLOW_LINKS);
            makefiles.forEach(this::removeErrorFlags);
        } catch (IOException e) {
            LOGGER.logException("Was not able to search for Makefiles: ", e);
            quitOnError();
        }
    }

    private void removeErrorFlags(Path pathToFile) {
        LOGGER.logDebug("Adjusting Makefile: " + pathToFile);
        List<String> lines = null;
        // Read the file's content
        try(BufferedReader reader = new BufferedReader(new FileReader(pathToFile.toFile()))) {
            lines = reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.logException("Was not able to read Makefile: " + pathToFile, e);
            quitOnError();
        }
        if (lines == null) {
            LOGGER.logError("The file's content is null: " + pathToFile);
            quitOnError();
        }

        // Remove all error flags
        List<String> fixedLines = new ArrayList<>(Objects.requireNonNull(lines).size());
        for (String line : lines) {
            // Replace "-Wall" with "-Wno-error"
            line = line.replaceAll("-Wall", "-w");

            // Note: It seems that setting '-w' is the only fix we need. Additionally, the other 'fixes' can break the
            // Makefile in some cases
            // Replace "-Werror=SOMETHING" with "-Wno-error=SOMETHING"
            // line = line.replaceAll("-Werror=", "-Wno-error=");
            // Replace all remaining error flags, that follow the pattern "-WSOMETHING", with "-Wno-error=SOMETHING"
            // line = line.replaceAll("(?!(-Wno-error|-Wp))-W", "-Wno-error=");
            // Replace all cases with the construct "-Wno-error=SOMETHING=VALUE" with ""
            // line = line.replaceAll("-Wno-error=[\\S]+=\\$\\{[\\S]+}", "");
            // line = line.replaceAll("-Wno-error=[\\S]+=[\\S]+", "");
            fixedLines.add(line);
        }

        // Write the content
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(pathToFile.toFile()))) {
            for (String line : fixedLines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.logException("Was not able to write adjusted Makefile " + pathToFile, e);
            quitOnError();
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