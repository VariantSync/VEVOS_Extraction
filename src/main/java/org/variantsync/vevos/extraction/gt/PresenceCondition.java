package org.variantsync.vevos.extraction.gt;

import java.io.Serializable;

public record PresenceCondition(String condition) implements Serializable {
    @Override
    public String toString() {
        return condition;
    }
}
