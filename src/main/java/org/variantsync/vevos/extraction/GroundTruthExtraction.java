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

public abstract class GroundTruthExtraction {
    protected final Properties properties;

    protected GroundTruthExtraction(Properties properties) {
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
        // TODO: load dynamically
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
            throw new RuntimeException("The required constructor does not exist for the specified class " + args[1]);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Logger.error("Was not able to instantiate extraction class with the propterties " + args[0]
                    + " and the class name " + args[1]);
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
            Logger.error("The second program argument must specify a valid GroundTruthExtraction class.");
            throw new IllegalArgumentException("The second program argument must specify a valid GroundTruthExtraction class.");
        }
    }

    private static GroundTruthExtraction initializeExtraction(Class<?> extractionClass, Properties properties)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
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

    /**
     * Throws an error if the host OS is Windows.
     */
    public static void checkOS() {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
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
     * @param commitName  The id of the commit for which the GT has been calculated
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

    protected abstract BiConsumer<Repository, Path> extractionRunner();
}
