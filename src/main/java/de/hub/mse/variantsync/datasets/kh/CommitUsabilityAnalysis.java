package de.hub.mse.variantsync.datasets.kh;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AbstractAnalysis;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.ExtractorException;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A pretty much empty analysis that only checks whether the extractors work for the given project.
 *
 * @author Alex S.
 */
public class CommitUsabilityAnalysis extends AbstractAnalysis {
    public static final @NonNull Setting<@Nullable String> WORK_DIR
            = new Setting<>("working_directory", Setting.Type.STRING, false, null, "" +
            "Path to the working directory.");
    public static final @NonNull Setting<@Nullable Boolean> CHECK_CME
            = new Setting<>("analysis.check_code_model_extractor", Setting.Type.BOOLEAN, false, "false", "" +
            "A flag that determines whether the analysis checks all three extractors or only the variability and build model" +
            "extractors.");

    /**
     * Creates a new analysis.
     *
     * @param config The configuration.
     */
    public CommitUsabilityAnalysis(@NonNull Configuration config) {
        super(config);
        try {
            config.registerSetting(CHECK_CME);
        } catch (SetUpException e) {
            LOGGER.logError("Was not able to register config setting!");
        }
    }

    @Override
    public void run() {
        try {
            File workDir = new File(config.getValue(WORK_DIR));
            String taskName = "[" + workDir.getName() + "]";
            LOGGER.logDebug(taskName + "Running CommitUsabilityAnalysis in directory " + workDir);
            File resultFile = new File(workDir, "output/analysis_result.csv");
            LOGGER.logDebug(taskName + "Result File: " + resultFile);
            if (!resultFile.exists()) {
                createResultFile(resultFile);
            }

            RevCommit commit = getRevCommit(taskName);
            if (commit == null) {
                LOGGER.logError("Was not able to retrieve commit ID");
                return;
            }

            LOGGER.logDebug(taskName + "Working on commit " + commit.getName());

            boolean checkCME = config.getValue(CHECK_CME);
            if (checkCME) {
                cmProvider.start();
            }

            vmProvider.start();

            boolean cmSuccess = false;
            boolean bmSuccess = false;
            boolean vmSuccess = false;

            // variability
            VariabilityModel vm = vmProvider.getResult();
            int vmSize = 0;
            if (vm != null) {
                vmSize = vm.getVariables().size();
                LOGGER.logInfo(taskName + "Got a variability model with " + vm.getVariables().size() + " variables");
                vmSuccess = true;
                // Only start the bmProvider if vm succeeded, otherwise it will fail anyway
                bmProvider.start();
            } else {
                LOGGER.logError(taskName + "Got no variability model");
            }
            ExtractorException vmExc = vmProvider.getException();
            if (vmExc != null) {
                LOGGER.logExceptionInfo(taskName + "Got an exception from the variability model extractor", vmExc);
            }

            // build
            int bmSize = 0;
            if (vmSuccess) {
                BuildModel bm = bmProvider.getResult();
                if (bm != null) {
                    bmSize = bm.getSize();
                    LOGGER.logInfo(taskName + "Got a build model with " + bm.getSize() + " files");
                    bmSuccess = true;
                } else {
                    LOGGER.logError(taskName + "Got no build model");
                }
                ExtractorException bmExc = bmProvider.getException();
                if (bmExc != null) {
                    LOGGER.logException(taskName + "Got an exception from the build model extractor", bmExc);
                }
            }

            // code
            if (checkCME) {
                int numCm = 0;
                SourceFile<?> result;
                do {
                    result = cmProvider.getNextResult();
                    if (result != null) {
                        numCm++;
                    }
                } while (result != null);
                if (numCm > 0) {
                    cmSuccess = true;
                }
                LOGGER.logInfo(taskName + "Got " + numCm + " source files in the code model");

                ExtractorException cmExc;
                do {
                    cmExc = cmProvider.getNextException();
                    if (cmExc != null) {
                        LOGGER.logException(taskName + "Got an exception from the code model extractor", cmExc);
                    }
                } while (cmExc != null);
            }
            LOGGER.logInfo(taskName + "Finished extraction.");
            boolean extractorsSucceeded = bmSuccess && (!checkCME || cmSuccess);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile, true))) {
                LOGGER.logInfo(taskName + "Saving results.");
                String line;
                if (checkCME) {
                    line = String.format("%s,%s,%s,%s,%s,%s,%s,%d,%d", taskName, commit.getName(), parseParents(commit), extractorsSucceeded, cmSuccess, bmSuccess, vmSuccess, bmSize, vmSize);
                } else {
                    line = String.format("%s,%s,%s,%s,%s,%s,%s,%d,%d", taskName, commit.getName(), parseParents(commit), extractorsSucceeded, "NOT_CHECKED", bmSuccess, vmSuccess, bmSize, vmSize);
                }
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                LOGGER.logException("Exception while saving analysis result", e);
            }
        } catch (SetUpException e) {
            LOGGER.logException("Exception while starting extractors", e);
        }
    }

    private static void createResultFile(File resultFile) {
        // Initialize the result file
        LOGGER.logInfo("Initializing the result file " + resultFile + "...");
        if (resultFile.getParentFile().mkdirs()) {
            LOGGER.logInfo("...created parent folders...");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile))) {
            // Write the header
            String line = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", "THREAD_ID", "COMMIT", "COMMIT_PARENTS", "OVERALL_SUCCESS", "CM", "BM", "VM", "BM_SIZE", "VM_SIZE");
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            LOGGER.logException("Exception while creating result file", e);
        }
    }

    private RevCommit getRevCommit(String taskName) {
        File splDir = config.getValue(DefaultSettings.SOURCE_TREE);
        LOGGER.logDebug(taskName + "SPL Dir: " + splDir);
        Repository repository;
        RevCommit commit = null;
        try {
            repository = new FileRepositoryBuilder()
                    .setGitDir(new File(splDir, ".git"))
                    .build();
            Git git = new Git(repository);
            int count = 0;
            for (RevCommit c : git.log().setMaxCount(1).call()) {
                count++;
                commit = c;
            }
            if (count > 1) {
                LOGGER.logError(taskName + "More than one commit retrieved from the log!");
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
        if (commit == null) {
            LOGGER.logError(taskName + "No commit retrieved from the log!");
            return null;
        }
        return commit;
    }

    private String parseParents(RevCommit commit) {
        StringBuilder sb = new StringBuilder();
        for (RevCommit parent : commit.getParents()) {
            sb.append(parent.getName());
            sb.append(":");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
