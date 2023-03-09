package org.variantsync.vevos.extraction;

import java.util.ArrayList;

public class FileGroundTruth {
    private final ArrayList<LineAnnotation> annotations;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected FileGroundTruth() {
        this.annotations = new ArrayList<>();
        this.consumed = false;
    }

    protected FileGroundTruth(FileGroundTruth other) {
        this.annotations = other.annotations;
        this.consumed = false;
    }

    public int size() {
        return annotations.size();
    }

    public LineAnnotation get(int index) {
        return this.annotations.get(index);
    }

    public LineAnnotation insert(int index, LineAnnotation annotation) {
        // Increase the size of the array if necessary
        if (index >= this.annotations.size()) {
            this.annotations.ensureCapacity(index + 1);
        }
        while (index >= this.annotations.size()) {
            this.annotations.add(null);
        }
        return this.annotations.set(index, annotation);
    }

    public LineAnnotation delete(int index) {
        return this.annotations.set(index, null);
    }
}
