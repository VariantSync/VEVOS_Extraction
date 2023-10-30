package org.variantsync.vevos.extraction;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents the ground truth annotation for a block of lines in a file with the same feature mapping
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
    private final FeatureMapping featureMappingBefore;
    private final PresenceCondition presenceConditionBefore;
    private final FeatureMapping featureMappingAfter;
    private final PresenceCondition presenceConditionAfter;
    private int lineStartInclusive;
    private int lineEndInclusive;

    public BlockAnnotation(int lineStartInclusive, int lineEndInclusive,
                           FeatureMapping featureMappingBefore, PresenceCondition presenceConditionBefore,
                           FeatureMapping featureMappingAfter, PresenceCondition presenceConditionAfter) {
        this.lineStartInclusive = lineStartInclusive;
        this.lineEndInclusive = lineEndInclusive;
        this.featureMappingBefore = featureMappingBefore;
        this.presenceConditionBefore = presenceConditionBefore;
        this.featureMappingAfter = featureMappingAfter;
        this.presenceConditionAfter = presenceConditionAfter;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockAnnotation that = (BlockAnnotation) o;
        return lineStartInclusive == that.lineStartInclusive
                && lineEndInclusive == that.lineEndInclusive
                && Objects.equals(featureMappingBefore, that.featureMappingBefore)
                && Objects.equals(presenceConditionBefore, that.presenceConditionBefore)
                && Objects.equals(featureMappingAfter, that.featureMappingAfter)
                && Objects.equals(presenceConditionAfter, that.presenceConditionAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureMappingBefore, presenceConditionBefore, featureMappingAfter, presenceConditionAfter, lineStartInclusive, lineEndInclusive);
    }

    public boolean annotationEquals(BlockAnnotation other) {
        return this.featureMappingBefore.equals(other.featureMappingBefore)
                && this.featureMappingAfter.equals(other.featureMappingAfter)
                && this.presenceConditionBefore.equals(other.presenceConditionBefore)
                && this.presenceConditionAfter.equals(other.presenceConditionAfter);
    }

    public boolean annotationEquals(LineAnnotation other) {
        return this.featureMappingBefore.equals(other.featureMappingBefore())
                && this.featureMappingAfter.equals(other.featureMappingAfter())
                && this.presenceConditionBefore.equals(other.presenceConditionBefore())
                && this.presenceConditionAfter.equals(other.presenceConditionAfter());
    }

    @Override
    public String toString() {
        return "BlockAnnotation[" +
                "lineStartInclusive=" + lineStartInclusive + ", " +
                "lineEndExclusive=" + lineEndInclusive + ", " +
                "featureMappingBefore=" + featureMappingBefore + ", " +
                "presenceConditionBefore=" + presenceConditionBefore + ", " +
                "featureMappingAfter=" + featureMappingAfter + ", " +
                "presenceConditionAfter=" + presenceConditionAfter + ']';
    }

    public String asCSVLine() {
        return "%s;%s;%s;%s;%d;%d".formatted(normalizeCondition(this.featureMappingBefore.mapping()),
                normalizeCondition(this.presenceConditionBefore.condition()),
                normalizeCondition(this.featureMappingAfter.mapping()),
                normalizeCondition(this.presenceConditionAfter.condition()),
                this.lineStartInclusive,
                this.lineEndInclusive);
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
