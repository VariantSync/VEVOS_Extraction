package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

public class GTBefore {
    public static class Stable extends GroundTruth {
        public Stable() {
            super(new LineAnnotation[0]);
        }

        public Stable(GTAfter.Stable after) {
            super(after.annotations);
            after.consumed = true;
        }

        public GTBefore.Unstable startFiltering() {
            consumed = true;
            return new Unstable(this);
        }
    }


    public static class Unstable extends GroundTruth {

        private Unstable(Stable stable) {
            super(stable.annotations);
        }

        public Unstable filterLine(int lineNumber) {
            var index = lineNumber-1;
            Assert.assertTrue(!consumed);
            Assert.assertTrue(annotations[index] != null);

            annotations[index] = null;
            return this;
        }

        public GTBefore.Filtered finishFiltering() {
            this.consumed = true;
            return new GTBefore.Filtered(this);
        }
    }

    public static class Filtered extends GroundTruth {
        // We can only add new lines until this Empty has been consumed
        private Filtered(GTBefore.Unstable unstable) {
            super(unstable.annotations);
        }
    }
}
