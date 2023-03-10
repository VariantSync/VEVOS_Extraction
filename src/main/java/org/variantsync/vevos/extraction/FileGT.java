package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.util.Assert;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class FileGT implements Iterable<LineAnnotation>, Serializable {
    private final ArrayList<LineAnnotation> annotations;
    protected final String file;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected FileGT(String file) {
        this.annotations = new ArrayList<>();
        this.consumed = false;
        this.file = file;
    }

    protected FileGT(FileGT other) {
        this.annotations = other.annotations;
        this.consumed = false;
        this.file = other.file;
    }

    protected int size() {
        return annotations.size();
    }

    protected LineAnnotation get(int index) {
        return this.annotations.get(index);
    }

    protected LineAnnotation insert(int index, LineAnnotation annotation) {
        growIfRequired(index);
        return this.annotations.set(index, annotation);
    }

    protected void growIfRequired(int index) {
        // Increase the size of the array if necessary
        if (index >= this.annotations.size()) {
            this.annotations.ensureCapacity(index + 1);
        }
        while (index >= this.annotations.size()) {
            this.annotations.add(null);
        }
    }

    @Override
    public Iterator<LineAnnotation> iterator() {
        return this.annotations.iterator();
    }

    public boolean isMutable() {
        return this instanceof Mutable;
    }

    public boolean isIncomplete() {
        return this instanceof Incomplete;
    }

    public boolean isComplete() {
        return this instanceof Complete;
    }

    public boolean isRemoved() {
        return this instanceof Removed;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(this.file).append("\n");

        for (LineAnnotation line : this) {
            sb.append(line);
            sb.append("\n");
        }
        sb.append("+++");
        sb.append("\n");
        return sb.toString();
    }

    public static class Mutable extends FileGT {
        private final HashSet<Integer> updatedIndices;
        private final HashSet<Integer> removedIndices;

        public Mutable(String file) {
            super(file);
            this.updatedIndices = new HashSet<>();
            this.removedIndices = new HashSet<>();
        }

        public Mutable insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            this.insert(line.index(), line);
            return this;
        }

        public Mutable update(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            this.insert(line);
            this.updatedIndices.add(line.index());
            return this;
        }

        public Mutable markRemoved(int lineNumber) {
            Assert.assertTrue(!consumed);

            this.removedIndices.add(lineNumber-1);
            return this;
        }

        public Incomplete finishMutation() {
            this.consumed = true;
            return new Incomplete(this);
        }
    }

    public static class Incomplete extends FileGT {
        private final HashSet<Integer> updatedIndices;
        private final HashSet<Integer> removedIndices;

        private Incomplete(Mutable mutableGT) {
            super(mutableGT);
            this.updatedIndices = mutableGT.updatedIndices;
            this.removedIndices = mutableGT.removedIndices;
        }

        public Complete combine(Complete oldGT) {
            this.consumed = true;

            // The offset is required to track differences between indices of the before and the after version of the
            // ground truth
            // Lines that have been removed reduce the offset by 1
            // Lines that have been added increase the offset by 1
            int offset = 0;
            for (int i = 0; i < oldGT.size(); i++) {
                if (this.removedIndices.contains(i)) {
                    // The annotated line has been removed, adjust the offset and check the next one
                    offset -= 1;
                    continue;
                }
                offset = this.findPositionAndInsert(oldGT.get(i), i, offset);
            }

            for (int i = 0; i < this.size(); i++) {
                if (this.get(i) == null) {
                    // If there are still null values, they must have occurred due to an endif, which is not processed by DiffDetective
                    this.insert(i, new LineAnnotation(i+1, "", "", "endif"));
                }
            }
            return new Complete(this);
        }

        private int findPositionAndInsert(LineAnnotation annotation, int index, int offset) {
            // We have to increment the offset for each line that has been added in the new version
            // For this, we check positions in the target annotations starting from the index with offset
            for (int j = index+offset; j < Integer.MAX_VALUE; j++) {
                if (j < this.size() && this.get(j) != null) {
                    if (!this.updatedIndices.contains(j)) {
                        // There is an added line, increase the offset and check the next possible location
                        offset += 1;
                    } else {
                        // This line has been updated, so we do not take it into the new ground truth
                        break;
                    }
                } else {
                    // Found an empty spot in the FileGroundTruth. This is where the annotation belongs
                    // Apply the offset to the annotations line number;
                    var previousEntry = this.insert(j, annotation.withOffset(offset));
                    Assert.assertTrue(previousEntry == null);
                    break;
                }
            }
            // Return the updated offset
            return offset;
        }
    }

    public static class Complete extends FileGT {

        private Complete(String file) {
            super(file);
        }

        private Complete(Incomplete incomplete) {
            super(incomplete);
        }

        public static Complete empty() {
            return new Complete("");
        }

    }

    public static class Removed extends FileGT {

        public Removed(String file) {
            super(file);
        }
    }
}
