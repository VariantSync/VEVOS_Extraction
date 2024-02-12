package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;
import org.variantsync.vevos.extraction.gt.GroundTruth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.BiConsumer;

import static org.variantsync.vevos.extraction.ConfigProperties.*;

/**
 * Base class for ground truth extractions. This class offers basic utilities for any ground truth
 * extraction and expects the implementation of an extraction runner that is provided in form of a
 * supplier method.
 *
 * Each GroundTruthExtraction must be initialized with a set of properties that configure the
 * extraction.
 */
public abstract class GroundTruthExtraction {
    protected final Properties properties;

    /**
     * Initialize the basic GroundTruth extraction with a set of extraction properties.
     */
    protected GroundTruthExtraction(Properties properties) {
        this.properties = properties;
    }

    /**
     * Main method to start the extraction. The method first loads the properties from the specified
     * file and then intializes the specified extraction class with those properties. Lastly, it
     * starts the ground truth extraction with the configuration specified in the properties.
     *
     * @param args Two arguments are expected: First, a path to a properties file in which the
     *        extraction is configured, and second, the full specifier of a GroundTruthExtraction
     *        subclass. The subclass' constructor must match the constructor of
     *        GroundTruthExtraction.
     * @throws IOException When loading the properties fails.
     */
    public static void main(String[] args) throws IOException {
        checkOS();

        // Load the configuration
        Properties properties = getProperties(getPropertiesFile(args));

        //
        Class<?> extractionClass;
        try {
            extractionClass = determineExtractionClass(args);
        } catch (ClassNotFoundException e) {
            Logger.error("The class " + args[1] + " provided as program argument was not found.");
            throw new RuntimeException(e);
        }
        GroundTruthExtraction extraction;
        try {
            extraction = initializeExtraction(extractionClass, properties);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "The required constructor does not exist for the specified class " + args[1]);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Logger.error("Was not able to instantiate extraction class with the propterties "
                    + args[0] + " and the class name " + args[1]);
            throw new RuntimeException(e);
        }

        var options = diffdetectiveOptions(properties);
        Logger.info("Starting SPL history analysis.");
        extraction.run(options);
    }

    private static Class<?> determineExtractionClass(String... args) throws ClassNotFoundException {
        if (args.length > 1) {
            return Class.forName(args[1]);
        } else {
            Logger.error(
                    "The second program argument must specify a valid GroundTruthExtraction class.");
            throw new IllegalArgumentException(
                    "The second program argument must specify a valid GroundTruthExtraction class.");
        }
    }

    private static GroundTruthExtraction initializeExtraction(Class<?> extractionClass,
            Properties properties) throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        Constructor<?> constructor = extractionClass.getDeclaredConstructor(Properties.class);
        constructor.setAccessible(true); // If the constructor is not public
        return (GroundTruthExtraction) constructor.newInstance(properties);
    }

    /**
     * Loads the properties in the given file.
     *
     * @param propertiesFile The file to load
     * @return The loaded properties
     */
    public static Properties getProperties(File propertiesFile) {
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
     * Options for the execution of DiffDetective
     *
     * @param properties The properties loaded by main()
     * @return The options instance
     */
    public static AnalysisRunner.Options diffdetectiveOptions(Properties properties) {
        final String[] allowedFileExtensions;
        String propertyValue = properties.getProperty(FILE_EXTENSIONS);
        if (propertyValue == null) {
            final String[] defaultExtensions = {"h", "hpp", "c", "cpp"};
            allowedFileExtensions = defaultExtensions;
        } else {
            allowedFileExtensions = propertyValue.split("\\w*,\\w*");
        }

        return new AnalysisRunner.Options(Path.of(properties.getProperty(REPO_SAVE_DIR)),
                Path.of(properties.getProperty(DD_OUTPUT_DIR)),
                Path.of(properties.getProperty(DATASET_FILE)), repo -> {
                    final PatchDiffParseOptions repoDefault = repo.getParseOptions();
                    return new PatchDiffParseOptions(
                            PatchDiffParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                            new VariationDiffParseOptions(
                                    repoDefault.variationDiffParseOptions().annotationParser(),
                                    false, false));
                }, repo -> new DiffFilter.Builder().allowMerge(true)
                        .allowedFileExtensions(allowedFileExtensions).build(),
                true, false);
    }

    /**
     * Throws an error if the host OS is Windows.
     */
    public static void checkOS() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            Logger.error(
                    "Running the analysis under Windows is not supported as the Linux/BusyBox sources are not"
                            + "checked out correctly.");
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

    /**
     * Parses the file in which the properties are located from the arguments.
     *
     * @param args the arguments to parse
     * @return the properties file
     */
    public static File getPropertiesFile(String[] args) {
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
     * Prints the given ground truth to console.
     *
     * @param groundTruth GT to print
     * @param commitName The id of the commit for which the GT has been calculated
     */
    public static void print(GroundTruth groundTruth, String commitName) {
        System.out.println();
        System.out.printf("*****************   %s   ******************", commitName);
        System.out.println();
        for (String file : groundTruth.fileGTs().keySet()) {
            System.out.println(groundTruth.get(file));
        }
    }

    /**
     * Starts the extraction.
     *
     * @param options The options for DiffDetective
     * @throws IOException If an IO error occurs in DiffDetective
     */
    public void run(AnalysisRunner.Options options) throws IOException {
        AnalysisRunner.run(options, extractionRunner());
    }

    protected int numProcessors() {
        final int availableProcessors;
        String numThreads = this.properties.getProperty(NUM_THREADS);
        if (numThreads == null || numThreads.trim().isEmpty() || numThreads.trim().equals("0")) {
            availableProcessors = Runtime.getRuntime().availableProcessors();
        } else {
            availableProcessors = Integer.parseInt(numThreads);
        }
        return availableProcessors;
    }

    protected int diffDetectiveBatchSize() {
        final int batchSize;
        String configuredSize = this.properties.getProperty(BATCH_SIZE);
        if (configuredSize == null || configuredSize.trim().isEmpty()
                || configuredSize.trim().equals("0")) {
            batchSize = 256;
        } else {
            batchSize = Integer.parseInt(configuredSize);
        }
        return batchSize;
    }

    /**
     * Return a runner for the ground truth extraction. The runner receives pairs of repositories
     * and paths to result output directories and then starts a DiffDetective analysis. See
     * {@link FastGroundTruthExtraction} and {@link FullGroundTruthExtraction} for examples.
     */
    protected abstract BiConsumer<Repository, Path> extractionRunner();
}
