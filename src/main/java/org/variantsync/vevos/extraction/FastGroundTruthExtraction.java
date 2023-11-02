package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.vevos.extraction.analysis.FastVariabilityAnalysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.variantsync.vevos.extraction.Config.*;

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



    /**
     * Parses the file in which the properties are located from the arguments.
     *
     * @param args the arguments to parse
     * @return the properties file
     */
    private static File getPropertiesFile(String[] args) {
        File propertiesFile = null;
        if (args.length > 0) {
            propertiesFile = new File(args[0]);
        }

        if (propertiesFile == null) {
            Logger.error("You must specify a .properties file as first argument");
            quitOnError();
        }

        return propertiesFile;
    }

    /**
     * Loads the properties in the given file.
     *
     * @param propertiesFile The file to load
     * @return The loaded properties
     */
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

    /**
     * Throws an error if the host OS is Windows.
     */
    private static void checkOS() {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        Logger.info("OS NAME: " + System.getProperty("os.name"));
        if (isWindows) {
            Logger.error("Running the analysis under Windows is not supported as the Linux/BusyBox sources are not" +
                    "checked out correctly.");
            quitOnError();
        }
    }

    /**
     * Logs an error message and quits the extraction with an exception.
     */
    public static void quitOnError() {
        Logger.error("An error occurred and the program has to quit.");
        throw new IllegalStateException("Not able to continue analysis due to previous error");
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
