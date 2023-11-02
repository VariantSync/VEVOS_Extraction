package org.variantsync.vevos.extraction.gt;

public record PresenceCondition(String condition) {
    @Override
    public String toString() {
        return condition;
    }
}
