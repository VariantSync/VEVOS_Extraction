package org.variantsync.vevos.extraction;

import org.variantsync.vevos.extraction.util.ConfigManipulator;
import org.variantsync.vevos.extraction.util.ShellExecutor;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class AnalysisTask implements Runnable {
    public static final @NonNull Setting<@Nullable String> WORK_DIR
            = new Setting<>("working_directory", Setting.Type.STRING, false, null, "" +
            "Path to the working directory.");
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
    private final long timeout;
    private final boolean fullExtraction;

    public AnalysisTask(List<RevCommit> commits, File parentDir, File propertiesFile, String splName, long timeout, boolean fullExtraction) {
        this.commits = commits;
        this.parentDir = parentDir;
        this.parentPropertiesFile = propertiesFile;
        this.splName = splName;
        this.taskNumber = existingTasksCount++;
        this.timeout = timeout;
        this.fullExtraction = fullExtraction;
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
            config.registerSetting(Extraction.LOG_LEVEL_MAIN);
            config.registerSetting(Extraction.EXTRACTION_TIMEOUT);
            LOGGER.setLevel(config.getValue(Extraction.LOG_LEVEL_MAIN));
            prepareConfig(workDir, propertiesFile);
        } catch (SetUpException e) {
            LOGGER.logError("Invalid configuration detected:", e.getMessage());
            return;
        }

        Path pathToTargetDir = Paths.get(parentDir.getAbsolutePath(), "output");
        Set<String> processedCommits = determineProcessedCommits(pathToTargetDir);

        int count = 1;
        // Process commits assigned to this task
        for (RevCommit commit : commits) {
            if (processedCommits.contains(commit.getName())) {
                LOGGER.logStatus("Skipping " + commit.getName() + " as it was already processed.");
                count++;
                continue;
            }
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

            synchronized (AnalysisTask.class) {
                moveResultsToDirectory(workDir, pathToTargetDir, commit, prepareFail);
            }

            // Delete the blocker
            deleteBlocker(splDir);

            LOGGER.logStatus("Starting clean up...");

            // Restore the makefiles
            EXECUTOR.execute("git restore .", splDir);

            // Remove all untracked files and directories
            EXECUTOR.execute("git clean -fdx", splDir);

            // Remove the temporary directory created by busyboot
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
        manipulator.put(WORK_DIR.getKey(), workDir.getAbsolutePath());
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
    
    private Set<String> determineProcessedCommits(Path pathToTargetDir) {
        Set<String> processedCommits = new HashSet<>();
        try {
            Path successFile = Paths.get(pathToTargetDir.toString(), SUCCESS_COMMIT_FILE);
            Path errorFile = Paths.get(pathToTargetDir.toString(), ERROR_COMMIT_FILE);
            Path incompletePCFile = Paths.get(pathToTargetDir.toString(), INCOMPLETE_PC_COMMIT_FILE);
            if (Files.exists(successFile)) processedCommits.addAll(Files.readAllLines(successFile));
            if (Files.exists(errorFile)) processedCommits.addAll(Files.readAllLines(errorFile));
            if (Files.exists(incompletePCFile)) processedCommits.addAll(Files.readAllLines(incompletePCFile));
        } catch (IOException e) {
            LOGGER.logException("Was not able to determine processed commits.", e);
        }
        return processedCommits;
    }

    private void moveResultsToDirectory(File workDir, Path pathToTargetDir, RevCommit commit, File prepareFail) {
        String commitId = commit.getName();
        LOGGER.logStatus("Moving result to common output directory.");
        File data_collection_dir = pathToTargetDir.resolve("data").resolve(commitId).toFile();
        File log_collection_dir = pathToTargetDir.resolve("log").toFile();

        if (data_collection_dir.mkdirs()) {
            LOGGER.logDebug("Created sub-dir for collecting the results for commit " + data_collection_dir.getName());
        }

        if (log_collection_dir.mkdirs()) {
            LOGGER.logDebug("Created sub-dir for collecting the log for commit " + data_collection_dir.getName());
        }

        File outputDir = new File(workDir, "output");
        // Move the results of the analysis to the collected output directory according to the current commit
        LOGGER.logStatus("Moving presence conditions to common output directory.");
        boolean hasError = movePresenceConditions(outputDir, data_collection_dir);

        LOGGER.logStatus("Moving DIMACS feature model to common output directory.");
        if (fullExtraction) {
            hasError = hasError | moveDimacsModel(outputDir, data_collection_dir);
        }

        LOGGER.logStatus("Moving FILTERED file to common output directory.");
        if(moveFilterCount(outputDir, data_collection_dir)) {
            LOGGER.logWarning("Moving FILTERED failed.");
        }
        
        LOGGER.logStatus("Moving VARIABLES file to common output directory.");
        if(moveVariablesFile(outputDir, data_collection_dir)) {
            LOGGER.logWarning("Moving VARIABLES failed.");
        }
        
        // Move the cache of the extractors to the collected output directory
        LOGGER.logStatus("Moving extractor cache to common output directory.");
        if (fullExtraction) {
            hasError = hasError | moveFeatureModel(workDir, data_collection_dir);
        }

        // Move the log to the common output directory
        LOGGER.logStatus("Moving KernelHaven log to common output directory");
        moveKernelHavenLog(workDir, log_collection_dir, commitId);

        if (hasError) {
            EXECUTOR.execute("echo \"" + commitId + "\" >> " + ERROR_COMMIT_FILE, pathToTargetDir.toFile());
            if (Objects.requireNonNull(data_collection_dir.listFiles()).length == 0) {
                try {
                    Files.delete(data_collection_dir.toPath());
                } catch (IOException e) {
                    LOGGER.logError("Was not able to delete the result collection directory " + data_collection_dir);
                }
            }
        } else {
            writeParents(commit, data_collection_dir);
            writeToFile(data_collection_dir, COMMIT_MESSAGE_FILE, commit.getFullMessage());
            if (prepareFail.exists() && this.fullExtraction) {
                LOGGER.logWarning("KernelHaven was not able to correctly load the build model, the extracted file presence conditions are incomplete!");
                EXECUTOR.execute("echo \"" + commitId + "\" >> " + INCOMPLETE_PC_COMMIT_FILE, pathToTargetDir.toFile());
            } else {
                EXECUTOR.execute("echo \"" + commitId + "\" >> " + SUCCESS_COMMIT_FILE, pathToTargetDir.toFile());
            }
        }

        try {
            new ZipFile(new File(data_collection_dir.getParentFile(), commitId + ".zip")).addFolder(data_collection_dir);
            EXECUTOR.execute("rm -rf " + commitId, data_collection_dir.getParentFile());
        } catch (ZipException e) {
            LOGGER.logError("Was not able to zip variability data of commit " + commit.getName());
        }

        LOGGER.logInfo("...done.");
    }

    private static void writeToFile(File collection_dir, String fileName, String fullMessage) {
        Path pathToCommitFile = collection_dir.toPath().resolve(fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(pathToCommitFile.toFile()))) {
            bw.write(fullMessage);
        } catch (IOException e) {
            LOGGER.logException("Was not able to write " + pathToCommitFile, e);
        }
    }

    private static void writeParents(RevCommit commit, File collection_dir) {
        Optional<String> parentIds = Arrays.stream(commit.getParents()).map(RevCommit::getName).reduce((s, s2) -> s + " " + s2);
        parentIds.ifPresent(s -> writeToFile(collection_dir, COMMIT_PARENTS_FILE, s));
    }

    private static void moveKernelHavenLog(File workDir, File targetDir, String commitId) {
        File logDir = new File(workDir, "log");
        File[] logFiles = logDir.listFiles((dir, name) -> name.contains(".log"));
        if (logFiles == null || logFiles.length == 0) {
            LOGGER.logWarning("NO LOG FILE IN " + logDir.getAbsolutePath());
        } else {
            if (logFiles.length > 1) {
                LOGGER.logWarning("FOUND MORE THAN ONE LOG FILE IN " + logDir.getAbsolutePath());
            }
            try {
                LOGGER.logInfo("Moving log from " + logFiles[0].getAbsolutePath() + " to " + targetDir);
                Files.move(logFiles[0].toPath(), Paths.get(targetDir.getAbsolutePath(), commitId + ".log"));
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the log file of the analysis: ", e);
            }
        }
    }

    private static boolean moveFeatureModel(File workDir, File targetDir) {
        boolean hasError = false;
        File vmCache = new File(new File(workDir, "cache"), "vmCache.json");
        if (vmCache.exists()) {
            try {
                LOGGER.logInfo("Moving cache from " + vmCache.getAbsolutePath() + " to " + targetDir);
                Files.move(vmCache.toPath(), Paths.get(targetDir.toString(), "variability-model.json"), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the cached variability model: ", e);
                hasError = true;
            }
        } else {
            LOGGER.logError("NO VARIABILITY MODEL EXTRACTED TO " + vmCache);
            hasError = true;
        }
        return hasError;
    }

    private static boolean moveOutputFile(File outputDir, File targetDir, String sourceName, String targetName, boolean errorExpected) {
        boolean hasError = false;
        File[] resultFiles = outputDir.listFiles((dir, name) -> name.contains(sourceName));
        if (resultFiles == null || resultFiles.length == 0) {
            if (!errorExpected) {
                LOGGER.logError("NO RESULT FILE IN " + outputDir.getAbsolutePath());
            }
            hasError = true;
        } else if (resultFiles.length == 1) {
            try {
                LOGGER.logInfo("Moving results from " + resultFiles[0].getAbsolutePath() + " to " + targetDir);
                Files.move(resultFiles[0].toPath(), Paths.get(targetDir.toString(), targetName), REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.logException("Was not able to move the result file of the analysis: ", e);
            }
        } else {
            LOGGER.logError("FOUND MORE THAN ONE RESULT FILE IN " + outputDir.getAbsolutePath());
            for (File f : resultFiles) {
                LOGGER.logError(f.getAbsolutePath());
            }
            LOGGER.logWarning("Cleaning output directory...");
            EXECUTOR.execute("rm -f ./*", outputDir);
            hasError = true;
        }
        return hasError;
    }

    private static boolean movePresenceConditions(File outputDir, File targetDir) {
        return moveOutputFile(outputDir, targetDir, "Blocks.csv", "code-variability.spl.csv", false);
    }

    private static boolean moveDimacsModel(File outputDir, File targetDir) {
        return moveOutputFile(outputDir, targetDir, "feature-model.dimacs", "feature-model.dimacs", false);
    }
    
    private static boolean moveFilterCount(File outputDir, File targetDir) {
        return moveOutputFile(outputDir, targetDir, "FILTERED.txt", "FILTERED.txt", true);
    }

    private static boolean moveVariablesFile(File outputDir, File targetDir) {
        return moveOutputFile(outputDir, targetDir, "VARIABLES.txt", "VARIABLES.txt", true);
    }

    private void createBlocker(File dir) {
        LOGGER.logInfo("Blocking directory " + dir);
        File blocker = new File(dir, "BLOCKER.txt");
        try {
            if (!blocker.createNewFile()) {
                LOGGER.logError("Was not able to create blocker file!");
                Extraction.quitOnError();
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
            Extraction.quitOnError();
        }
    }

    private void removeErrorFlags(Path pathToFile) {
        LOGGER.logDebug("Adjusting Makefile: " + pathToFile);
        List<String> lines = null;
        // Read the file's content
        try (BufferedReader reader = new BufferedReader(new FileReader(pathToFile.toFile()))) {
            lines = reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.logException("Was not able to read Makefile: " + pathToFile, e);
            Extraction.quitOnError();
        }
        if (lines == null) {
            LOGGER.logError("The file's content is null: " + pathToFile);
            Extraction.quitOnError();
        }

        // Remove all error flags
        List<String> fixedLines = new ArrayList<>(Objects.requireNonNull(lines).size());
        for (String line : lines) {
            // Replace "-Wall" with "-Wno-error"
            line = line.replaceAll("-Wall", "-w");

            // Note: It seems that setting '-w' is the only fix we need. Other 'fixes' might break a Makefile
            fixedLines.add(line);
        }

        // Write the content
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToFile.toFile()))) {
            for (String line : fixedLines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.logException("Was not able to write adjusted Makefile " + pathToFile, e);
            Extraction.quitOnError();
        }
    }

    private void checkBlocker(File dir) {
        LOGGER.logInfo("Checking block of directory " + dir);
        File blocker = new File(dir, "BLOCKER.txt");
        if (blocker.exists()) {
            LOGGER.logError("The SPL directory is blocked by another task! This indicates a bug in the " +
                    "implementation of multi-threading.");
            Extraction.quitOnError();
        } else {
            LOGGER.logInfo("NO BLOCK FOUND - OK");
        }
    }

    private void deleteBlocker(File dir) {
        LOGGER.logInfo("Removing block of directory " + dir);
        File blocker = new File(dir, "BLOCKER.txt");
        if (!blocker.delete()) {
            LOGGER.logError("Was not able to delete blocker file!");
            Extraction.quitOnError();
        } else {
            LOGGER.logInfo("BLOCK REMOVED - OK");
        }
    }

}