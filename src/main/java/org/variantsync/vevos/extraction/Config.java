package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;

import java.nio.file.Path;
import java.util.Properties;

public class Config {
    public static final String PRINT_ENABLED
            = "extraction.print-enabled";
    public static final String GT_SAVE_DIR
            = "extraction.gt-save-dir";
    public static final String IGNORE_PC_CHANGES
            = "extraction.ignore-pc-changes";
    public static final String DATASET_FILE
            = "diff-detective.dataset-file";
    public static final String DD_OUTPUT_DIR
            = "diff-detective.output-dir";
    public static final String REPO_SAVE_DIR
            = "diff-detective.repo-storage-dir";
    public static final String NUM_THREADS
            = "diff-detective.num-threads";
    public static final String BATCH_SIZE
            = "diff-detective.batch-size";
    public static final String EXTRACT_CODE_MATCHING
            = "extraction.extract-code-matching";

    /**
     * Options for the execution of DiffDetective
     *
     * @param properties The properties loaded by main()
     * @return The options instance
     */
    public static AnalysisRunner.Options diffdetectiveOptions(Properties properties) {

        return new AnalysisRunner.Options(
                Path.of(properties.getProperty(REPO_SAVE_DIR)),
                Path.of(properties.getProperty(DD_OUTPUT_DIR)),
                Path.of(properties.getProperty(DATASET_FILE)),
                repo -> {
                    final PatchDiffParseOptions repoDefault = repo.getParseOptions();
                    return new PatchDiffParseOptions(
                            PatchDiffParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                            new VariationDiffParseOptions(
                                    repoDefault.variationDiffParseOptions().annotationParser(),
                                    false,
                                    false
                            )
                    );
                },
                repo -> new DiffFilter.Builder()
                        .allowMerge(true)
                        // TODO: make configurable
                        .allowedFileExtensions("h", "hpp", "c", "cpp")
                        .build(),
                true,
                false
        );
    }
}
