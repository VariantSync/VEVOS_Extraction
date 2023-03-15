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

    public void complete(GroundTruth base) {
        // update the variables
        this.variables.addAll(base.variables);

        Set<String> baseFiles = base.fileGTs.keySet();
        Set<String> updatedFiles = this.fileGTs.keySet();

        // First, handle files that have been newly added, their ground truth has to be set to complete
        // There is no real base to combine them with, so we combine them with an empty FileGT
        for (String updatedFile : updatedFiles) {
            if (baseFiles.contains(updatedFile)) {
                continue;
            }
            FileGT temp = this.get(updatedFile);
            if (temp instanceof FileGT.Removed) {
                if (temp.file.equals("/dev/null")) {
                    // TODO: This is only a workaround and should be handled by diff detective
                    // The case occurs, if certain temporary files have been tracken by accident
                    // Ideally, diff detective would associate the name of the file before the removal, and not
                    // the name of the file after removal
                    continue;
                }
            }
            FileGT.Complete updatedFileGT = ((FileGT.Mutable) this.get(updatedFile)).finishMutation();
            this.fileGTs.put(updatedFile,updatedFileGT);
        }
        // TODO: This is only a workaround and should be handled by diff detective (see above)
        this.fileGTs.remove("/dev/null");

        // Then, handle files that have had a GT before
        for (String baseFile : baseFiles) {
            FileGT.Complete baseFileGT = (FileGT.Complete) base.get(baseFile);
            if (!updatedFiles.contains(baseFile)) {
                // This file has not been updated, we want the entire ground truth for it
                this.fileGTs.put(baseFile, baseFileGT);
            } else {
                // This file has been updated and needs to be completed
                FileGT updatedFileGT = this.get(baseFile);
                if (updatedFileGT instanceof FileGT.Removed) {
                    // It is set to null if the entire file has been removed
                    this.fileGTs.remove(baseFile);
                } else {
                    FileGT.Mutable incompleteGT = (FileGT.Mutable) updatedFileGT;
                    this.fileGTs.put(baseFile, incompleteGT.finishMutation());
                }
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
            if( this.fileGTs.get(name) instanceof FileGT.Complete fileGT) {
                sb.append(fileGT.csvLines());
            } else {
                throw new IllegalStateException("Not possible to create CSV line for incomplete file ground truth");
            }
        }
        return sb.toString();
    }
}
