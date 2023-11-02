package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;
import org.variantsync.vevos.extraction.gt.GroundTruth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import static org.variantsync.vevos.extraction.ConfigProperties.*;

public class ExecutionUtilities {

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
}
