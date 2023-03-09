package org.variantsync.vevos.extraction;

public class GroundTruth {
    protected final LineAnnotation[] annotations;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected GroundTruth(LineAnnotation[] annotations) {
        this.annotations = annotations;
        this.consumed = false;
    }

    public int size() {
        return annotations.length;
    }

    public LineAnnotation get(int index) {
        return this.annotations[index];
    }
}
