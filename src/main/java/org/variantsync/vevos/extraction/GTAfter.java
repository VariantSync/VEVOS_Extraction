package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

public class GTAfter {

    public static class UnstableEmpty extends GroundTruth {

        public UnstableEmpty(int size) {
            super(new LineAnnotation[size]);
        }

        public UnstableEmpty insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            Assert.assertTrue(annotations[line.index()] == null);
            annotations[line.index()] = line;
            return this;
        }

        public UnstableWithInserted finishInsertion() {
            this.consumed = true;
            return new UnstableWithInserted(this);
        }
    }

    public static class UnstableWithInserted extends GroundTruth {

        private UnstableWithInserted(UnstableEmpty unstableEmptyGT) {
            super(unstableEmptyGT.annotations);
        }

        public UnstableWithInsertedAndCombined combine(GTBefore.Filtered filteredBeforeGT) {
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
                LineAnnotation annotation = befAnnotations[i];
                // We have to increment the offset for each line that has been added in the new version
                // For this, we check positions in the target annotations starting from the index with offset
                for (int j = i+offset; j < this.annotations.length; j++) {
                    if (this.annotations[j] != null) {
                        // There is an added line, increase the offset and check the next possible location
                        offset+=1;
                    } else {
                        // Found an empty spot in the GTAfter. This is where the annotation belongs
                        // Apply the offset to the annotations line number;
                        this.annotations[j] = annotation.withOffset(offset);
                        break;
                    }
                }
            }

            return new UnstableWithInsertedAndCombined(this);
        }
    }

    public static class UnstableWithInsertedAndCombined extends GroundTruth {
        private UnstableWithInsertedAndCombined(UnstableWithInserted withInserted) {
            super(withInserted.annotations);
        }

        public void update(LineAnnotation annotation) {
            Assert.assertTrue(!consumed);
            Assert.assertTrue(annotations[annotation.index()] != null);
            this.annotations[annotation.index()] = annotation;
        }

        public GTAfter.Stable finalizeGT() {
            this.consumed = true;
            return new GTAfter.Stable(this);
        }
    }

    public static class Stable extends GroundTruth {

        private Stable(UnstableWithInsertedAndCombined unstable) {
            super(unstable.annotations);
        }

    }
}
