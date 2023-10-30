package org.variantsync.vevos.extraction;

public record FeatureMapping(String mapping) {

    @Override
    public String toString() {
        return mapping;
    }
}
