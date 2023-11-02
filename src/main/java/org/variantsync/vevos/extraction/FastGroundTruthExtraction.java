package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.vevos.extraction.analysis.FastVariabilityAnalysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.variantsync.vevos.extraction.ConfigProperties.*;
import static org.variantsync.vevos.extraction.ExecutionUtilities.*;

public class FastGroundTruthExtraction {
    private final Properties properties;

    public FastGroundTruthExtraction(Properties properties) {
        this.properties = properties;
    }

    /**
     * Main method to start the extraction.
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {
        checkOS();

        // Load the configuration
        Properties properties = getProperties(getPropertiesFile(args));
        var extraction = new FastGroundTruthExtraction(properties);

        var options = diffdetectiveOptions(properties);
        Logger.info("Starting SPL history analysis.");
        extraction.run(options);
    }

    private BiConsumer<Repository, Path> buildRunner() {
        return (repo, repoOutputDir) -> {
            Path extractionDir = Path.of(this.properties.getProperty(GT_SAVE_DIR));
            Path resultsRoot = extractionDir.resolve(repo.getRepositoryName());
            boolean printEnabled = Boolean.parseBoolean(this.properties.getProperty(PRINT_ENABLED));

            FastVariabilityAnalysis analysis = new FastVariabilityAnalysis(printEnabled, resultsRoot,
                    Boolean.parseBoolean(properties.getProperty(IGNORE_PC_CHANGES)),
                    Boolean.parseBoolean(properties.getProperty(EXTRACT_CODE_MATCHING)));
            final BiFunction<Repository, Path, Analysis> AnalysisFactory = (r, out) -> new Analysis(
                    "PCAnalysis",
                    List.of(
                            analysis
                    ),
                    r,
                    out
            );
            final int availableProcessors;
            String numThreads = this.properties.getProperty(NUM_THREADS);
            if (numThreads == null || numThreads.trim().isEmpty() || numThreads.trim().equals("0")) {
                availableProcessors = Runtime.getRuntime().availableProcessors();
            } else {
                availableProcessors = Integer.parseInt(numThreads);
            }
            final int batchSize;
            String configuredSize = this.properties.getProperty(BATCH_SIZE);
            if (configuredSize == null || configuredSize.trim().isEmpty() || configuredSize.trim().equals("0")) {
                batchSize = 256;
            } else {
                batchSize = Integer.parseInt(configuredSize);
            }

            Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir), batchSize, availableProcessors);

            FastVariabilityAnalysis.numProcessed = 0;
        };
    }

    /**
     * Starts the extraction.
     *
     * @param options The options for DiffDetective
     * @throws IOException If an IO error occurs in DiffDetective
     */
    public void run(AnalysisRunner.Options options) throws IOException {
        AnalysisRunner.run(options, buildRunner());
    }

}
