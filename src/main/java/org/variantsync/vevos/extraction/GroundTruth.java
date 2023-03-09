package org.variantsync.vevos.extraction;

import java.util.Hashtable;
import java.util.function.Function;

public record GroundTruth(Hashtable<String, FileGT> fileGTs) {
    public synchronized FileGT computeIfAbsent(String file, Function<? super String, ? extends FileGT> mappingFunction) {
        return this.fileGTs.computeIfAbsent(file, mappingFunction);
    }

    public synchronized FileGT get(String fileName) {
        return this.fileGTs.get(fileName);
    }

    public synchronized int size() {
        return this.fileGTs.size();
    }
}
