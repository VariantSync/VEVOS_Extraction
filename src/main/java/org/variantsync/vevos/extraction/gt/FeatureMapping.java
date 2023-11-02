package org.variantsync.vevos.extraction.gt;

public record FeatureMapping(String mapping) {

    @Override
    public String toString() {
        return mapping;
    }
}
