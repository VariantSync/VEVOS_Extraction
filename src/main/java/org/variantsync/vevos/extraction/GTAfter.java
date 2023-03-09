package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

import java.util.HashSet;

public class GTAfter {

    public static class UnstableEmpty extends FileGroundTruth {
        private final HashSet<Integer> updatedIndices;

        public UnstableEmpty() {
            super();
            this.updatedIndices = new HashSet<>();
        }

        public UnstableEmpty insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            var previousEntry = this.insert(line.index(), line);
            Assert.assertTrue(previousEntry == null);
            return this;
        }

        public UnstableEmpty update(LineAnnotation line) {
            this.insert(line);
            this.updatedIndices.add(line.index());
            return this;
        }

        public UnstableWithInserted finishInsertion() {
            this.consumed = true;
            return new UnstableWithInserted(this);
        }
    }

    public static class UnstableWithInserted extends FileGroundTruth {
        private final HashSet<Integer> updatedIndices;

        private UnstableWithInserted(UnstableEmpty unstableEmptyGT) {
            super(unstableEmptyGT);
            this.updatedIndices = unstableEmptyGT.updatedIndices;
        }

        public GTAfter.Stable combine(GTBefore.Filtered filteredBeforeGT) {
            this.consumed = true;

            // The offset is required to track differences between indices of the before and the after version of the
            // ground truth
            // Lines that have been removed reduce the offset by 1
            // Lines that have been added increase the offset by 1
            int offset = 0;
            for (int i = 0; i < filteredBeforeGT.size(); i++) {
                if (filteredBeforeGT.get(i) == null) {
                    // The annotated line has been removed, adjust the offset and check the next one
                    offset -= 1;
                    continue;
                }
                offset = this.findPositionAndInsert(filteredBeforeGT.get(i), i, offset);
            }

            return new GTAfter.Stable(this);
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
                    // Found an empty spot in the GTAfter. This is where the annotation belongs
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

    public static class Stable extends FileGroundTruth {

        private Stable(UnstableWithInserted unstable) {
            super(unstable);
        }

    }
}
