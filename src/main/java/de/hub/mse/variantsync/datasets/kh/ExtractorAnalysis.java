package de.hub.mse.variantsync.datasets.kh;
/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
// TODO: Refactor to AnalysisComponent
public class ExtractorAnalysis extends AbstractAnalysis {
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
    public ExtractorAnalysis(@NonNull Configuration config) {
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
            LOGGER.logDebug( taskName + "Running ExtractorAnalysis in directory " + workDir);
            File result_file = new File(workDir, "output/analysis_result.csv");
            LOGGER.logDebug(taskName + "Result File: " + result_file);
            if (!result_file.exists()) {
                LOGGER.logError(taskName + "The result file was not found...exiting.");
                return;
            }

            RevCommit commit = getRevCommit(taskName);
            if (commit == null) return;
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
                LOGGER.logInfo(taskName + "Got no variability model");
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
                    LOGGER.logInfo(taskName + "Got no build model");
                }
                ExtractorException bmExc = bmProvider.getException();
                if (bmExc != null) {
                    LOGGER.logExceptionInfo(taskName + "Got an exception from the build model extractor", bmExc);
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
                        LOGGER.logExceptionInfo(taskName + "Got an exception from the code model extractor", cmExc);
                    }
                } while (cmExc != null);
            }

            boolean extractorsSucceeded = bmSuccess && (!checkCME || cmSuccess);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(result_file, true))) {
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

    private RevCommit getRevCommit(String taskName) {
        File linuxDir = config.getValue(DefaultSettings.SOURCE_TREE);
        LOGGER.logDebug(taskName + "Linux Dir: " + linuxDir);
        Repository repository;
        RevCommit commit = null;
        try {
            repository = new FileRepositoryBuilder()
                    .setGitDir(new File(linuxDir, ".git"))
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
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }
}
