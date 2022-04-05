package org.variantsync.vevos.extraction.util;

import net.ssehub.kernel_haven.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ShellExecutor {
    private final Logger LOGGER;

    public ShellExecutor(Logger logger) {
        this.LOGGER = logger;
    }

    public boolean execute(String command, File directory, long timeout, TimeUnit timeUnit) {
        LOGGER.logInfo("Executing '" + command + "' in directory " + directory + " ...");
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("sh", "-c", command);
        builder.directory(directory);
        boolean result = false;
        try {
            LOGGER.logDebug("COMMAND: " + builder.command());
            Process process = builder.start();
            // Start info logging
            StreamLogger infoLogger = new StreamLogger(LOGGER, process.getInputStream());
            // Start error logging - NOTE: Some programs use this to log their normal output so we only log warnings
            StreamLogger errorLogger = new StreamLogger(LOGGER, process.getErrorStream(), Logger.Level.WARNING);
            boolean success;
            if (timeout > 0) {
                boolean finished = process.waitFor(timeout, timeUnit);
                if (finished) {
                    success = process.exitValue() == 0;
                } else {
                    LOGGER.logError("PROCESS TIMEOUT");
                    success = false;
                }
            } else {
                success = process.waitFor() == 0;
            }
            if (success) {
                LOGGER.logInfo("...done. SUCCESS!");
                result = true;
            } else {
                LOGGER.logError("...done. PROCESS FAILED!");
            }
            infoLogger.log();
            errorLogger.log();
        } catch (InterruptedException | IOException e) {
            LOGGER.logException("Unexpected exception occurred!", e);
            throw new RuntimeException(e);
        }
        return result;
    }

    public boolean execute(String command, File directory) {
        return this.execute(command, directory, 0, null);
    }
}
