package org.variantsync.vevos.extraction;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The ground truth for the files of a repository at a specific commit (i.e., version).
 *
 * @param fileGTs   The ground truths for each file
 * @param variables The set of variables that can appear in the presence conditions
 */
public record GroundTruth(HashMap<String, FileGT> fileGTs, Set<String> variables) implements Serializable {
    private static final Pattern variableStart = Pattern.compile("\\$\\{");
    private static final Pattern variableEnd = Pattern.compile("}");
    private static final Pattern quotation = Pattern.compile("\"");

    public FileGT computeIfAbsent(String file, Function<? super String, ? extends FileGT> mappingFunction) {
        return this.fileGTs.computeIfAbsent(file, mappingFunction);
    }

    public FileGT get(String fileName) {
        return this.fileGTs.get(fileName);
    }

    public int size() {
        return this.fileGTs.size();
    }

    public void updateWith(GroundTruth updated) {
        // update the variables
        this.variables.addAll(updated.variables);

        // Handle files that have been newly added or updated, their ground truth has to be set to complete
        // There is no real base to combine them with, so we combine them with an empty FileGT
        for (String updatedFile : updated.fileGTs.keySet()) {
            FileGT fileGT = updated.get(updatedFile);
            if (fileGT instanceof FileGT.Removed || fileGT instanceof FileGT.Complete) {
                // Update the GT version number
                FileGT oldVersion = this.fileGTs.get(updatedFile);
                fileGT.setVersion(oldVersion.version());
                fileGT.incrementVersion();
                this.fileGTs.put(updatedFile, fileGT);
            } else {
                throw new IllegalStateException("Unexpected incomplete ground truth");
            }
        }
    }

    public static GroundTruth merge(GroundTruth... groundTruths) {
        Set<String> fileUnion = Arrays.stream(groundTruths).flatMap(gt -> gt.fileGTs.keySet().stream()).collect(Collectors.toSet());
        Set<String> variables = Arrays.stream(groundTruths).flatMap(gt -> gt.variables.stream()).collect(Collectors.toSet());
        GroundTruth merged = new GroundTruth(new HashMap<>(), new HashSet<>(variables));

        for (String fileName : fileUnion) {
            FileGT latestVersion = null;
            for (GroundTruth gt : groundTruths) {
                FileGT next = gt.get(fileName);
                if (next == null) {
                    continue;
                }
                if (latestVersion == null || latestVersion.version() < next.version()) {
                    latestVersion = next;
                }
                merged.fileGTs.put(fileName, latestVersion);
            }
        }
        return merged;
    }

    public String variablesListAsString() {
        List<String> variablesList = new ArrayList<>(this.variables);
        Collections.sort(variablesList);

        StringBuilder sb = new StringBuilder();
        for (String name : variablesList) {
            if (name.equals("True") || name.equals("False")) {
                continue;
            }
            name = name.replaceAll(variableStart.pattern(), "");
            name = name.replaceAll(variableEnd.pattern(), "");
            name = name.replaceAll(quotation.pattern(), "");
            sb.append(name).append(System.lineSeparator());
        }
        return sb.toString();
    }

    public String asCSVString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Path;File Condition;Block Condition;Presence Condition;start;end");
        sb.append(System.lineSeparator());
        ArrayList<String> fileNames = new ArrayList<>(this.fileGTs.keySet());
        Collections.sort(fileNames);
        for (String name : fileNames) {
            if (this.fileGTs.get(name) instanceof FileGT.Complete fileGT) {
                sb.append(fileGT.csvLines());
            } else {
                throw new IllegalStateException("Not possible to create CSV line for incomplete file ground truth");
            }
        }
        return sb.toString();
    }
}
