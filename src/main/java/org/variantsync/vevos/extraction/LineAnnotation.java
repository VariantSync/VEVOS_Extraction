package org.variantsync.vevos.extraction;


import java.io.Serializable;

/**
 * Represents the ground truth annotation for a single line in a file
 * @param lineNumber
 * @param featureMapping
 * @param presenceCondition
 * @param nodeType
 */
public record LineAnnotation(int lineNumber, String featureMapping, String presenceCondition, String nodeType) implements Serializable {
    public final static LineAnnotation EMPTY = new LineAnnotation(-1, "", "", "");

    public int index() {
        return this.lineNumber-1;
    }

    public LineAnnotation withOffset(int offset) {
        return new LineAnnotation(this.lineNumber + offset, this.featureMapping, this.presenceCondition, this.nodeType);
    }

    public static LineAnnotation rootAnnotation(int lineNumber) {
        return new LineAnnotation(lineNumber, "True", "True", "ROOT");
    }

    @Override
    public String toString() {
        return "%d, %s, FM =%s, PC = %s".formatted(lineNumber, nodeType, featureMapping, presenceCondition);
    }

}
