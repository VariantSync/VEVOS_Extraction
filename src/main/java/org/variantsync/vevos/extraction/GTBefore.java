package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

public class GTBefore {
    public static class Initial extends FileGroundTruth {
        public Initial() {
            super();
        }

        public Initial(GTAfter.Stable after) {
            super(after);
            after.consumed = true;
        }

        public GTBefore.Unstable startFiltering() {
            consumed = true;
            return new Unstable(this);
        }
    }


    public static class Unstable extends FileGroundTruth {

        private Unstable(Initial initial) {
            super(initial);
        }

        public Unstable filterLine(int lineNumber) {
            var index = lineNumber-1;
            Assert.assertTrue(!consumed);

            var previousEntry = this.delete(index);
            Assert.assertTrue(previousEntry != null);

            return this;
        }

        public GTBefore.Filtered finishFiltering() {
            this.consumed = true;
            return new GTBefore.Filtered(this);
        }
    }

    public static class Filtered extends FileGroundTruth {
        // We can only add new lines until this Empty has been consumed
        private Filtered(GTBefore.Unstable unstable) {
            super(unstable);
        }
    }
}
