package org.variantsync.vevos.extraction;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The ground truth for the files of a repository at a specific commit (i.e., version).
 *
 * @param fileGTs   The ground truths for each file
 * @param variables The set of variables that can appear in the presence conditions
 */
public record GroundTruth(HashMap<String, FileGT> fileGTs, Set<String> variables)
        implements Serializable {
    private static final Pattern variableStart = Pattern.compile("\\$\\{");
    private static final Pattern variableEnd = Pattern.compile("}");
    private static final Pattern quotation = Pattern.compile("\"");
    private static final Pattern semicolon = Pattern.compile(";");

    private static String variablesListAsString(Set<String> variables) {
        List<String> variablesList = new ArrayList<>(variables);
        Collections.sort(variablesList);

        StringBuilder sb = new StringBuilder();
        for (String name : variablesList) {
            if (name.equals("True") || name.equals("False")) {
                continue;
            }
            name = name.replaceAll(variableStart.pattern(), "");
            name = name.replaceAll(variableEnd.pattern(), "");
            name = name.replaceAll(quotation.pattern(), "");
            name = name.replaceAll(semicolon.pattern(), "SEMICOLON");
            sb.append(name).append(System.lineSeparator());
        }
        return sb.toString();
    }

    public FileGT computeIfAbsent(String file,
                                  Function<? super String, ? extends FileGT> mappingFunction) {
        return this.fileGTs.computeIfAbsent(file, mappingFunction);
    }

    public FileGT get(String fileName) {
        return this.fileGTs.get(fileName);
    }

    public int size() {
        return this.fileGTs.size();
    }

    public boolean isEmpty() {
        return this.fileGTs.isEmpty();
    }

    public void updateWith(GroundTruth updated) {
        // update the variables
        this.variables.addAll(updated.variables);

        // Handle files that have been newly added or updated, their ground truth has to be set to
        // complete
        // There is no real base to combine them with, so we combine them with an empty FileGT
        for (String updatedFile : updated.fileGTs.keySet()) {
            FileGT fileGT = updated.get(updatedFile);
            if (fileGT instanceof FileGT.Removed) {
                // It is set to removed if the entire file has been removed
                this.fileGTs.remove(updatedFile);
            } else if (fileGT instanceof FileGT.Complete updatedFileGT) {
                this.fileGTs.put(updatedFile, updatedFileGT);
            } else {
                throw new IllegalStateException("Unexpected incomplete ground truth");
            }
        }
    }

    public String variablesListAsString() {
        return variablesListAsString(this.variables);
    }

    public String combinedVariablesListAsString(GroundTruth other) {
        HashSet<String> variables = new HashSet<>(this.variables);
        variables.addAll(other.variables);
        return variablesListAsString(variables);
    }

    public String asPcCsvString() {
        return generateCsv(
                "Path;File Condition;Block Condition Before;Presence Condition Before;Block Condition After;Presence Condition After;start;end",
                FileGT.Complete::csvPCLines
        );
    }

    public String asMatchingCsvString() {
        return generateCsv(
                "Path;Line Number; Counterpart",
                FileGT.Complete::csvMatchingLines
        );
    }

    private String generateCsv(String header, Function<FileGT.Complete, String> lineGenerator) {
        StringBuilder sb = new StringBuilder();
        sb.append(header);
        sb.append(System.lineSeparator());
        ArrayList<String> fileNames = new ArrayList<>(this.fileGTs.keySet());
        Collections.sort(fileNames);
        for (String name : fileNames) {
            if (this.fileGTs.get(name) instanceof FileGT.Complete fileGT) {
                sb.append(lineGenerator.apply(fileGT));
            } else {
                throw new IllegalStateException(
                        "Not possible to create CSV line for incomplete file ground truth");
            }
        }
        return sb.toString();
    }
}
