package org.variantsync.vevos.extraction;

public record PresenceCondition(String condition) {
    @Override
    public String toString() {
        return condition;
    }
}
