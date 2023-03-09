package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;

public class FileGT {
    private final ArrayList<LineAnnotation> annotations;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected FileGT() {
        this.annotations = new ArrayList<>();
        this.consumed = false;
    }

    protected FileGT(FileGT other) {
        this.annotations = other.annotations;
        this.consumed = false;
    }

    protected int size() {
        return annotations.size();
    }

    protected LineAnnotation get(int index) {
        return this.annotations.get(index);
    }

    protected LineAnnotation insert(int index, LineAnnotation annotation) {
        // Increase the size of the array if necessary
        if (index >= this.annotations.size()) {
            this.annotations.ensureCapacity(index + 1);
        }
        while (index >= this.annotations.size()) {
            this.annotations.add(null);
        }
        return this.annotations.set(index, annotation);
    }

    public static class Mutable extends FileGT {
        private final HashSet<Integer> updatedIndices;
        private final HashSet<Integer> removedIndices;

        public Mutable() {
            super();
            this.updatedIndices = new HashSet<>();
            this.removedIndices = new HashSet<>();
        }

        public Mutable insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            var previousEntry = this.insert(line.index(), line);
            Assert.assertTrue(previousEntry == null);
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

            Assert.assertTrue(this.removedIndices.add(lineNumber-1));
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

        private Complete() {
            super();
        }

        private Complete(Incomplete unstable) {
            super(unstable);
        }

        public static Complete empty() {
            return new Complete();
        }

    }
}
