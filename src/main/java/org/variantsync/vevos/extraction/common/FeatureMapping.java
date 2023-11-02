package org.variantsync.vevos.extraction.common;

public record FeatureMapping(String mapping) {

    @Override
    public String toString() {
        return mapping;
    }
}
