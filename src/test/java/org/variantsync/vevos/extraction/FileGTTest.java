package org.variantsync.vevos.extraction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileGTTest {

    public static FileGT.Complete simpleFileGT() {
        FileGT.Mutable unstableAfter = new FileGT.Mutable("");

        unstableAfter.insert(new LineAnnotation(1, null, null, ""));
        unstableAfter.insert(new LineAnnotation(3, null, null, ""));
        unstableAfter.insert(new LineAnnotation(2, null, null, ""));

        return unstableAfter
                .finishMutation();
    }

    @Test
    public void stagesAreConsumed() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable("");
        var stableAfter = unstableAfter.finishMutation();

        Assertions.assertFalse(completeBefore.consumed);
        Assertions.assertTrue(unstableAfter.consumed);

        Assertions.assertFalse(stableAfter.consumed);
    }

    @Test
    public void firstGroundTruth() {
        var stableAfter = simpleFileGT();

        Assertions.assertFalse(stableAfter.consumed);
        Assertions.assertEquals(3, stableAfter.size());
        Assertions.assertEquals(1, stableAfter.get(0).lineNumber());
        Assertions.assertEquals(2, stableAfter.get(1).lineNumber());
        Assertions.assertEquals(3, stableAfter.get(2).lineNumber());
    }

}
