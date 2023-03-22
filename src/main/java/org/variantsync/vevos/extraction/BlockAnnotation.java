package org.variantsync.vevos.extraction;

import org.prop4j.Node;

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
    private final Node featureMapping;
    private final Node presenceCondition;
    private int lineStartInclusive;
    private int lineEndInclusive;

    public BlockAnnotation(int lineStartInclusive, int lineEndInclusive, Node featureMapping, Node presenceCondition) {
        this.lineStartInclusive = lineStartInclusive;
        this.lineEndInclusive = lineEndInclusive;
        this.featureMapping = featureMapping;
        this.presenceCondition = presenceCondition;
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
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BlockAnnotation) obj;
        return this.lineStartInclusive == that.lineStartInclusive &&
                this.lineEndInclusive == that.lineEndInclusive &&
                Objects.equals(this.featureMapping, that.featureMapping) &&
                Objects.equals(this.presenceCondition, that.presenceCondition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineStartInclusive, lineEndInclusive, featureMapping, presenceCondition);
    }

    @Override
    public String toString() {
        return "BlockAnnotation[" +
                "lineStartInclusive=" + lineStartInclusive + ", " +
                "lineEndExclusive=" + lineEndInclusive + ", " +
                "featureMapping=" + featureMapping + ", " +
                "presenceCondition=" + presenceCondition + ']';
    }

    public String asCSVLine() {
        return "%s;%s;%d;%d".formatted(normalizeCondition(this.featureMapping.toString()), normalizeCondition(this.presenceCondition.toString()), this.lineStartInclusive, this.lineEndInclusive);
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
        return condition;
    }
}
