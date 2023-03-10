package org.variantsync.vevos.extraction;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.Function;

public record GroundTruth(Hashtable<String, FileGT> fileGTs) implements Serializable {
    public synchronized FileGT computeIfAbsent(String file, Function<? super String, ? extends FileGT> mappingFunction) {
        return this.fileGTs.computeIfAbsent(file, mappingFunction);
    }

    public synchronized FileGT get(String fileName) {
        return this.fileGTs.get(fileName);
    }

    public synchronized int size() {
        return this.fileGTs.size();
    }

    public synchronized void complete(GroundTruth base) {
        Set<String> baseFiles = base.fileGTs.keySet();
        Set<String> updatedFiles = this.fileGTs.keySet();

        // First, handle files that have been newly added, their ground truth has to be set to complete
        // There is no real base to combine them with, so we combine them with an empty FileGT
        for (String updatedFile : updatedFiles) {
            if (baseFiles.contains(updatedFile)) {
                continue;
            }
            FileGT.Incomplete updatedFileGT = ((FileGT.Mutable) this.get(updatedFile)).finishMutation();
            this.fileGTs.put(updatedFile,updatedFileGT.combine(FileGT.Complete.empty()));
        }

        // Then, handle files that have had a GT before
        for (String baseFile : baseFiles) {
            FileGT.Complete baseFileGT = (FileGT.Complete) base.get(baseFile);
            if (!updatedFiles.contains(baseFile)) {
                // This file has not been updated, we want the entire ground truth for it
                this.fileGTs.put(baseFile, baseFileGT);
            } else {
                // This file has been updated and needs to be completed
                FileGT.Mutable incompleteGT = (FileGT.Mutable) this.get(baseFile);
                if (incompleteGT == null) {
                    // It is set to null if the entire file has been removed
                    this.fileGTs.remove(baseFile);
                } else {
                    this.fileGTs.put(baseFile, incompleteGT.finishMutation().combine(baseFileGT));
                }
            }
        }
    }
}
