package org.variantsync.vevos.extraction;


import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Represents the ground truth annotation for a single line in a file
 * @param lineNumber
 * @param featureMapping
 * @param presenceCondition
 * @param nodeType
 */
public record LineAnnotation(int lineNumber, String featureMapping, String presenceCondition, String nodeType, Set<String> uniqueContainedFeatures) implements Serializable {
    public final static LineAnnotation EMPTY = new LineAnnotation(-1, "True", "True", "", Collections.singleton("True"));

    public int index() {
        return this.lineNumber-1;
    }

    public LineAnnotation withOffset(int offset) {
        return new LineAnnotation(this.lineNumber + offset, this.featureMapping, this.presenceCondition, this.nodeType, this.uniqueContainedFeatures);
    }

    public static LineAnnotation rootAnnotation(int lineNumber) {
        return new LineAnnotation(lineNumber, "True", "True", "ROOT", Collections.singleton("True"));
    }

    @Override
    public String toString() {
        return "%d, %s, FM =%s, PC = %s".formatted(lineNumber, nodeType, featureMapping, presenceCondition);
    }

}
