package de.hub.mse.variantsync.datasets.util;

import net.ssehub.kernel_haven.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShellExecutor {
    private final Logger LOGGER;

    public ShellExecutor(Logger logger) {
        this.LOGGER = logger;
    }

    public boolean execute(String command, File directory) {
        LOGGER.logInfo("Executing '" + command + "' in directory " + directory + " ...");
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("sh", "-c", command);
        builder.directory(directory);
        boolean result = false;
        ExecutorService infoExecutor = null;
        ExecutorService warningExecutor = null;
        try {
            LOGGER.logDebug("COMMAND: " + builder.command());
            Process process = builder.start();
            // Start info logging
            infoExecutor = Executors.newSingleThreadExecutor();
            infoExecutor.submit(new StreamLogger(LOGGER, process.getInputStream()));
            // Start error logging - NOTE: Some programs use this to log their normal output so we only log warnings
            warningExecutor = Executors.newSingleThreadExecutor();
            warningExecutor.submit(new StreamLogger(LOGGER, process.getErrorStream(), Logger.Level.WARNING));
            boolean success = process.waitFor() == 0;
            if (success) {
                LOGGER.logInfo("...done. SUCCESS!");
                result = true;
            } else {
                LOGGER.logWarning("...done. PROCESS FAILED!");
            }
        } catch (InterruptedException | IOException e) {
            LOGGER.logException("Unexpected exception occurred!", e);
            throw new RuntimeException(e);
        } finally {
            if (infoExecutor != null) {
                LOGGER.logDebug("Shutting down infoExecutor...");
                infoExecutor.shutdown();
            }
            if (warningExecutor != null) {
                LOGGER.logDebug("Shutting down warningExecutor...");
                warningExecutor.shutdown();
            }
        }
        return result;
    }
}
