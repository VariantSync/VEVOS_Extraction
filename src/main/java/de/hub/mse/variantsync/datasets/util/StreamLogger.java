package de.hub.mse.variantsync.datasets.util;

import net.ssehub.kernel_haven.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamLogger {
    private final Logger logger;
    private final BufferedReader reader;
    private final Logger.Level logChannel;

    public StreamLogger(Logger logger, InputStream stream) {
        this(logger, stream, Logger.Level.INFO);
    }

    public StreamLogger(Logger logger, InputStream stream, Logger.Level logChannel) {
        this.reader = new BufferedReader(new InputStreamReader(stream));
        this.logChannel = logChannel;
        this.logger = logger;
    }

    public void log() {
        while (true) {
            try {
                if (!reader.ready()) {
                    break;
                }
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                switch (this.logChannel) {
                    case INFO:
                        logger.logInfo(line);
                        break;
                    case WARNING:
                        logger.logWarning(line);
                        break;
                    case ERROR:
                        logger.logError(line);
                        break;
                }
            } catch (IOException e) {
                logger.logError("Error while logging InputStream...");
                logger.logError(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        logger.logDebug("Stream logging complete.");
    }
}
