package org.variantsync.vevos.extraction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.variantsync.vevos.extraction.gt.FeatureMapping;
import org.variantsync.vevos.extraction.gt.FileGT;
import org.variantsync.vevos.extraction.gt.LineAnnotation;
import org.variantsync.vevos.extraction.gt.PresenceCondition;

import java.util.Collections;

public class FileGTTest {

    public static FileGT.Complete simpleFileGT() {
        FileGT.Mutable unstableAfter = new FileGT.Mutable("");

        unstableAfter.insert(new LineAnnotation(1, new FeatureMapping("True"), new PresenceCondition("True"), "", Collections.singleton("True")));
        unstableAfter.insert(new LineAnnotation(3, new FeatureMapping("True"), new PresenceCondition("True"), "", Collections.singleton("True")));
        unstableAfter.insert(new LineAnnotation(2, new FeatureMapping("True"), new PresenceCondition("True"), "", Collections.singleton("True")));

        return unstableAfter
                .finishMutation();
    }

    @Test
    public void stagesAreConsumed() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable("");
        var stableAfter = unstableAfter.finishMutation();

        Assertions.assertFalse(completeBefore.isConsumed());
        Assertions.assertTrue(unstableAfter.isConsumed());

        Assertions.assertFalse(stableAfter.isConsumed());
    }

    @Test
    public void firstGroundTruth() {
        var stableAfter = simpleFileGT();

        Assertions.assertFalse(stableAfter.isConsumed());
        Assertions.assertEquals(3, stableAfter.size());
        Assertions.assertEquals(1, stableAfter.get(0).lineNumber());
        Assertions.assertEquals(2, stableAfter.get(1).lineNumber());
        Assertions.assertEquals(3, stableAfter.get(2).lineNumber());
    }

}
