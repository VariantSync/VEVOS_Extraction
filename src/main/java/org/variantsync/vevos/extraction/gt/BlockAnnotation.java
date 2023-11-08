package org.variantsync.vevos.extraction.gt;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents the ground truth annotation for a block of lines in a file with the same feature
 * mapping
 */
public final class BlockAnnotation implements Serializable {
    private static final Pattern t = Pattern.compile("True");
    private static final Pattern f = Pattern.compile("False");
    private static final Pattern not_1 = Pattern.compile("^-");
    private static final Pattern not_2 = Pattern.compile(" -");
    private static final Pattern not_3 = Pattern.compile("\\(-");
    private static final Pattern and = Pattern.compile(" & ");
    private static final Pattern or = Pattern.compile(" \\| ");
    private static final Pattern variableStart = Pattern.compile("\\$\\{");
    private static final Pattern variableEnd = Pattern.compile("}");
    private static final Pattern quotation = Pattern.compile("\"");
    private static final Pattern semicolon = Pattern.compile(";");
    private final FeatureMapping featureMapping;
    private final PresenceCondition presenceCondition;
    private int lineStartInclusive;
    private int lineEndInclusive;
    private String nodeType;

    public BlockAnnotation(int lineStartInclusive, int lineEndInclusive,
            FeatureMapping featureMapping, PresenceCondition presenceCondition, String nodeType) {
        this.lineStartInclusive = lineStartInclusive;
        this.lineEndInclusive = lineEndInclusive;
        this.featureMapping = featureMapping;
        this.presenceCondition = presenceCondition;
        this.nodeType = nodeType;
    }

    public void setLineStartInclusive(int lineStartInclusive) {
        this.lineStartInclusive = lineStartInclusive;
    }

    public void setLineEndInclusive(int lineEndInclusive) {
        this.lineEndInclusive = lineEndInclusive;
    }

    public int lineStartInclusive() {
        return lineStartInclusive;
    }

    public int lineEndExclusive() {
        return lineEndInclusive;
    }

    public String nodeType() {
        return nodeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BlockAnnotation that = (BlockAnnotation) o;
        return lineStartInclusive == that.lineStartInclusive
                && lineEndInclusive == that.lineEndInclusive
                && Objects.equals(featureMapping, that.featureMapping)
                && Objects.equals(presenceCondition, that.presenceCondition)
                && Objects.equals(nodeType, that.nodeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureMapping, presenceCondition, lineStartInclusive, lineEndInclusive,
                nodeType);
    }

    public boolean annotationEquals(BlockAnnotation other) {
        return this.featureMapping.equals(other.featureMapping)
                && this.presenceCondition.equals(other.presenceCondition)
                && this.nodeType.equals(other.nodeType);
    }

    public boolean annotationEquals(LineAnnotation other) {
        return this.featureMapping.equals(other.featureMapping())
                && this.presenceCondition.equals(other.presenceCondition())
                && this.nodeType.equals(other.nodeType());
    }

    @Override
    public String toString() {
        return "[" + "lineStartInclusive=" + lineStartInclusive + ", " + "lineEndExclusive="
                + lineEndInclusive + ", " + "nodeType=" + nodeType + ", " + "featureMapping="
                + featureMapping + ", " + "presenceCondition=" + presenceCondition + ']';
    }

    public String asCSVLine() {
        return "%s;%s;%s;%d;%d".formatted(normalizeCondition(this.featureMapping.mapping()),
                normalizeCondition(this.presenceCondition.condition()), this.nodeType,
                this.lineStartInclusive, this.lineEndInclusive);
    }

    /**
     * Normalizes the given feature mapping or presence condition String to KernelHaven format.
     *
     * @param condition The mapping/PC String to be normalized
     * @return A normalized version of the condition
     */
    private String normalizeCondition(String condition) {
        condition = condition.replaceAll(t.pattern(), "1");
        condition = condition.replaceAll(f.pattern(), "0");
        condition = condition.replaceAll(not_1.pattern(), "!");
        condition = condition.replaceAll(not_2.pattern(), " !");
        condition = condition.replaceAll(not_3.pattern(), "(!");
        condition = condition.replaceAll(and.pattern(), " && ");
        condition = condition.replaceAll(or.pattern(), " || ");
        condition = condition.replaceAll(variableStart.pattern(), "");
        condition = condition.replaceAll(variableEnd.pattern(), "");
        condition = condition.replaceAll(quotation.pattern(), "");
        condition = condition.replaceAll(semicolon.pattern(), "SEMICOLON");
        return condition;
    }
}
