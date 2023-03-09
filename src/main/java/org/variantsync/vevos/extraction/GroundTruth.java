package org.variantsync.vevos.extraction;

import java.util.Hashtable;

public record GroundTruth(Hashtable<String, FileGT> fileGTs) {
    public FileGT getOrDefault(String fileName, FileGT fileGT) {
        return this.fileGTs.getOrDefault(fileName, fileGT);
    }
}
