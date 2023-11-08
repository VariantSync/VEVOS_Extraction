package org.variantsync.vevos.extraction.gt;

import java.io.Serializable;

public record FeatureMapping(String mapping) implements Serializable {

    @Override
    public String toString() {
        return mapping;
    }
}
