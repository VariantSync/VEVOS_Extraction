package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

public class GTAfter {

    public static class UnstableEmpty extends GroundTruth {
        private final boolean[] updated;

        public UnstableEmpty(int size) {
            super(new LineAnnotation[size]);
            this.updated = new boolean[size];
        }

        public UnstableEmpty insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            Assert.assertTrue(annotations[line.index()] == null);
            annotations[line.index()] = line;
            return this;
        }

        public UnstableEmpty update(LineAnnotation line) {
            this.insert(line);
            this.updated[line.index()] = true;
            return this;
        }

        public UnstableWithInserted finishInsertion() {
            this.consumed = true;
            return new UnstableWithInserted(this);
        }
    }

    public static class UnstableWithInserted extends GroundTruth {
        private final boolean[] updated;

        private UnstableWithInserted(UnstableEmpty unstableEmptyGT) {
            super(unstableEmptyGT.annotations);
            this.updated = unstableEmptyGT.updated;
        }

        public GTAfter.Stable combine(GTBefore.Filtered filteredBeforeGT) {
            this.consumed = true;

            LineAnnotation[] befAnnotations = filteredBeforeGT.annotations;
            // The offset is required to track differences between indices of the before and the after version of the
            // ground truth
            // Lines that have been removed reduce the offset by 1
            // Lines that have been added increase the offset by 1
            int offset = 0;
            for (int i = 0; i < befAnnotations.length; i++) {
                if (befAnnotations[i] == null) {
                    // The annotated line has been removed, adjust the offset and check the next one
                    offset -= 1;
                    continue;
                }
                offset = this.findPositionAndInsert(befAnnotations[i], i, offset);
            }

            return new GTAfter.Stable(this);
        }

        private int findPositionAndInsert(LineAnnotation annotation, int index, int offset) {
            // We have to increment the offset for each line that has been added in the new version
            // For this, we check positions in the target annotations starting from the index with offset
            for (int j = index+offset; j < this.annotations.length; j++) {
                if (this.annotations[j] != null) {
                    if (!this.updated[j]) {
                        // There is an added line, increase the offset and check the next possible location
                        offset += 1;
                    } else {
                        // This line has been updated, so we do not take it into the new ground truth
                        break;
                    }
                } else {
                    // Found an empty spot in the GTAfter. This is where the annotation belongs
                    // Apply the offset to the annotations line number;
                    this.annotations[j] = annotation.withOffset(offset);
                    break;
                }
            }
            // Return the updated offset
            return offset;
        }
    }

    public static class Stable extends GroundTruth {

        private Stable(UnstableWithInserted unstable) {
            super(unstable.annotations);
        }

    }
}
