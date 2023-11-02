package org.variantsync.vevos.extraction.common;

public record PresenceCondition(String condition) {
    @Override
    public String toString() {
        return condition;
    }
}
