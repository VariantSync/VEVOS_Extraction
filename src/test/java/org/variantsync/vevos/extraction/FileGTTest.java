package org.variantsync.vevos.extraction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileGTTest {

    public static FileGT.Complete simpleFileGT() {
        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        unstableAfter.insert(new LineAnnotation(1, null, null));
        unstableAfter.insert(new LineAnnotation(3, null, null));
        unstableAfter.insert(new LineAnnotation(2, null, null));

        return unstableAfter
                .finishMutation()
                .combine(FileGT.Complete.empty());
    }

    @Test
    public void stagesAreConsumed() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable();
        var withInserted = unstableAfter.finishMutation();
        var stableAfter = withInserted.combine(completeBefore);

        Assertions.assertTrue(!completeBefore.consumed);
        Assertions.assertTrue(unstableAfter.consumed);
        Assertions.assertTrue(withInserted.consumed);

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


    @Test
    public void extendedGroundTruth() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        unstableAfter.insert(new LineAnnotation(2, null, null));
        unstableAfter.insert(new LineAnnotation(5, null, null));

        var finalGT = unstableAfter
                .finishMutation()
                .combine(completeBefore);

        Assertions.assertEquals(5, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());

        }
    }

    @Test
    public void extendedMultipleGroundTruth() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        unstableAfter.insert(new LineAnnotation(2, null, null))
                .insert(new LineAnnotation(3, null, null))
                .insert(new LineAnnotation(4, null, null));

        var finalGT = unstableAfter
                .finishMutation()
                .combine(completeBefore);

        Assertions.assertEquals(6, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }

    @Test
    public void filterRemoved() {
        FileGT.Complete completeBefore = simpleFileGT();

        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        var finalGT = unstableAfter
                .markRemoved(2)
                .finishMutation()
                .combine(completeBefore);

        Assertions.assertEquals(2, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }

    @Test
    public void filterAll() {
        FileGT.Complete completeBefore = simpleFileGT();


        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        var finalGT = unstableAfter
                .markRemoved(1)
                .markRemoved(2)
                .markRemoved(3)
                .finishMutation()
                .combine(completeBefore);

        Assertions.assertEquals(0, finalGT.size());
    }

    @Test
    public void fullPipeline() {
        FileGT.Complete completeBefore = simpleFileGT();


        FileGT.Mutable unstableAfter = new FileGT.Mutable();

        unstableAfter.insert(new LineAnnotation(2, null, null))
                .insert(new LineAnnotation(4, null, null))
                .insert(new LineAnnotation(5, null, null));

        unstableAfter.update(new LineAnnotation(1, null, null));

        var finalGT = unstableAfter
                .markRemoved(2)
                .finishMutation()
                .combine(completeBefore);

        Assertions.assertEquals(5, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }
}
