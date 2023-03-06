package org.variantsync.vevos.extraction.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.tinylog.Logger;

public class StreamLogger {
    private final BufferedReader reader;
    private final LogChannel logChannel;

    public StreamLogger(InputStream stream, LogChannel logChannel) {
        this.reader = new BufferedReader(new InputStreamReader(stream));
        this.logChannel = logChannel;
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
                        Logger.info(line);
                        break;
                    case WARNING:
                        Logger.warn(line);
                        break;
                    case ERROR:
                        Logger.error(line);
                        break;
                }
            } catch (IOException e) {
                Logger.error("Error while logging InputStream...");
                Logger.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        Logger.debug("Stream logging complete.");
    }

    public enum LogChannel {
        INFO,
        WARNING,
        ERROR,
    }
}
