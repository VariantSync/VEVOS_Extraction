package org.variantsync.vevos.extraction;


import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Represents the ground truth annotation for a single line in a file
 * @param lineNumber
 * @param featureMappingBefore
 * @param presenceConditionBefore
 * @param nodeType
 */
public record LineAnnotation(int lineNumber,
                             FeatureMapping featureMappingBefore, PresenceCondition presenceConditionBefore,
                             FeatureMapping featureMappingAfter, PresenceCondition presenceConditionAfter,
                             String nodeType, Set<String> uniqueContainedFeatures) implements Serializable {
    public final static LineAnnotation EMPTY = new LineAnnotation(-1,
            new FeatureMapping("True"), new PresenceCondition("True"),
            new FeatureMapping("True"), new PresenceCondition("True"),
            "", Collections.singleton("True"));

    public int index() {
        return this.lineNumber-1;
    }

    public LineAnnotation withOffset(int offset) {
        return new LineAnnotation(this.lineNumber + offset,
                this.featureMappingBefore, this.presenceConditionBefore,
                this.featureMappingAfter, this.presenceConditionAfter,
                this.nodeType, this.uniqueContainedFeatures);
    }

    public static LineAnnotation rootAnnotation(int lineNumber) {
        return new LineAnnotation(lineNumber,
                new FeatureMapping("True"), new PresenceCondition("True"),
                new FeatureMapping("True"), new PresenceCondition("True"),
                "ROOT", Collections.singleton("True"));
    }

    @Override
    public String toString() {
        return "%d, %s, FM Before =%s, PC Before = %s, FM After =%s, PC After =%s".formatted(lineNumber, nodeType,
                featureMappingBefore, presenceConditionBefore, featureMappingAfter, presenceConditionAfter);
    }

}
