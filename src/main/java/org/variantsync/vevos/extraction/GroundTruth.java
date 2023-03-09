package org.variantsync.vevos.extraction;

import java.util.Hashtable;

public record GroundTruth(Hashtable<String, FileGroundTruth> fileGTs) {
}
