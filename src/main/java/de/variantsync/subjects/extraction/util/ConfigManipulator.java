package de.variantsync.subjects.extraction.util;
import net.ssehub.kernel_haven.util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManipulator {
    private final File propertiesFile;
    private List<String> lines;
    private final Logger LOGGER = Logger.get();

    public ConfigManipulator(File propertiesFile) {
        this.propertiesFile = propertiesFile;
        try (BufferedReader fileReader = Files.newBufferedReader(propertiesFile.toPath())) {
            this.lines = fileReader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.logException("Exception while trying to load properties file " + propertiesFile + ": ", e);
            throw new RuntimeException(e);
        }
    }

    public void put(String key, String value) {
        List<String> changedLines = new ArrayList<>(lines.size());
        boolean somethingChanged = false;
        for (String line : lines) {
            if (line.trim().startsWith(key)) {
                if (somethingChanged) {
                    LOGGER.logWarning("Changing multiple lines in properties file in one call!");
                }
                StringBuilder lineBuilder = new StringBuilder();
                String[] parts = line.split("=");
                lineBuilder.append(parts[0]);
                lineBuilder.append(" = ");
                lineBuilder.append(value);
                changedLines.add(lineBuilder.toString());
                LOGGER.logInfo("Changed line '" + line + "' to '" + lineBuilder.toString() + "'.");
                somethingChanged = true;
            } else {
                changedLines.add(line);
            }
        }
        if (!somethingChanged) {
            String line = key + " = " + value;
            LOGGER.logInfo("Adding new key-value pair to configuration " + propertiesFile + ": '" + line + "'");
            changedLines.add(line);
        }
        this.lines = changedLines;
    }

    public void writeToFile() {
        try(BufferedWriter writer = Files.newBufferedWriter(propertiesFile.toPath())) {
            for (String l : lines) {
                writer.write(l);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.logException("Exception while trying to save properties file " + propertiesFile + ": ", e);
        }
    }
}
