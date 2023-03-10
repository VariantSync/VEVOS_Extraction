package org.variantsync.vevos.extraction;


import java.io.Serializable;

public record LineAnnotation(int lineNumber, String featureMapping, String presenceCondition) implements Serializable {

    public int index() {
        return this.lineNumber-1;
    }

    public LineAnnotation withOffset(int offset) {
        return new LineAnnotation(this.lineNumber + offset, this.featureMapping, this.presenceCondition);
    }



}
