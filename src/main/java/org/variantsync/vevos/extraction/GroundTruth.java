package org.variantsync.vevos.extraction;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

public record GroundTruth(HashMap<String, FileGT> fileGTs, Set<String> variables) implements Serializable {
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
        List<String> variablesList = new ArrayList<>(this.variables);
        Collections.sort(variablesList);

        StringBuilder sb = new StringBuilder();
        for (String name : variablesList) {
            if (name.equals("True") || name.equals("False")) {
                continue;
            }
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
