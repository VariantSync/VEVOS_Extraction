package org.variantsync.vevos.extraction.util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

public class ShellExecutor {

    public boolean execute(String command, File directory, long timeout, TimeUnit timeUnit) {
        Logger.info("Executing '" + command + "' in directory " + directory + " ...");
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("sh", "-c", command);
        builder.directory(directory);
        boolean result = false;
        try {
            Logger.debug("COMMAND: " + builder.command());
            Process process = builder.start();
            // Start info logging
            StreamLogger infoLogger = new StreamLogger(process.getInputStream(), StreamLogger.LogChannel.INFO);
            // Start error logging - NOTE: Some programs use this to log their normal output so we only log warnings
            StreamLogger errorLogger = new StreamLogger(process.getErrorStream(), StreamLogger.LogChannel.WARNING);
            boolean success;
            if (timeout > 0) {
                boolean finished = process.waitFor(timeout, timeUnit);
                if (finished) {
                    success = process.exitValue() == 0;
                } else {
                    Logger.error("PROCESS TIMEOUT");
                    success = false;
                }
            } else {
                success = process.waitFor() == 0;
            }
            if (success) {
                Logger.info("...done. SUCCESS!");
                result = true;
            } else {
                Logger.error("...done. PROCESS FAILED!");
            }
            infoLogger.log();
            errorLogger.log();
        } catch (InterruptedException | IOException e) {
            Logger.error("Unexpected exception occurred!", e);
            throw new RuntimeException(e);
        }
        return result;
    }

    public boolean execute(String command, File directory) {
        return this.execute(command, directory, 0, null);
    }
}
