package org.variantsync.vevos.extraction;

import org.prop4j.Node;

public record LineAnnotation(int lineNumber, Node presenceCondition, Node featureMapping) {

    public int index() {
        return this.lineNumber-1;
    }

    public LineAnnotation withOffset(int offset) {
        return new LineAnnotation(this.lineNumber + offset, this.presenceCondition, this.featureMapping);
    }
}
