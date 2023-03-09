package org.variantsync.vevos.extraction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroundTruthTest {

    @Test
    public void stagesAreConsumed() {
        GTBefore.Stable stableBefore = new GTBefore.Stable();
        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(0);
        var withInserted = unstableAfter.finishInsertion();
        var withBefore = withInserted.combine(stableBefore.startFiltering().finishFiltering());
        var stableAfter = withBefore.finalizeGT();

        Assertions.assertTrue(stableBefore.consumed);
        Assertions.assertTrue(unstableAfter.consumed);
        Assertions.assertTrue(withInserted.consumed);
        Assertions.assertTrue(withBefore.consumed);

        Assertions.assertFalse(stableAfter.consumed);
        stableBefore = new GTBefore.Stable(stableAfter);
        Assertions.assertTrue(stableAfter.consumed);
        Assertions.assertFalse(stableBefore.consumed);
    }

    @Test
    public void firstGroundTruth() {
        var stableAfter = simpleGTAfter();

        Assertions.assertFalse(stableAfter.consumed);
        Assertions.assertEquals(3, stableAfter.size());
        Assertions.assertEquals(1, stableAfter.get(0).lineNumber());
        Assertions.assertEquals(2, stableAfter.get(1).lineNumber());
        Assertions.assertEquals(3, stableAfter.get(2).lineNumber());
    }

    public static GTAfter.Stable simpleGTAfter() {
        GTBefore.Stable stableBefore = new GTBefore.Stable();
        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(3);

        unstableAfter.insert(new LineAnnotation(1, null, null));
        unstableAfter.insert(new LineAnnotation(3, null, null));
        unstableAfter.insert(new LineAnnotation(2, null, null));

        return unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .finishFiltering())
                .finalizeGT();
    }

    @Test
    public void extendedGroundTruth() {
        var stableBefore = new GTBefore.Stable(simpleGTAfter());
        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(5);

        unstableAfter.insert(new LineAnnotation(2, null, null));
        unstableAfter.insert(new LineAnnotation(5, null, null));

        var finalGT = unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .finishFiltering())
                .finalizeGT();

        Assertions.assertEquals(5, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());

        }
    }

    @Test
    public void extendedMultipleGroundTruth() {
        var stableBefore = new GTBefore.Stable(simpleGTAfter());
        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(6);

        unstableAfter.insert(new LineAnnotation(2, null, null))
                .insert(new LineAnnotation(3, null, null))
                .insert(new LineAnnotation(4, null, null));

        var finalGT = unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .finishFiltering())
                .finalizeGT();

        Assertions.assertEquals(6, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }

    @Test
    public void filterRemoved() {
        var stableBefore = new GTBefore.Stable(simpleGTAfter());

        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(2);

        var finalGT = unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .filterLine(2)
                        .finishFiltering())
                .finalizeGT();

        Assertions.assertEquals(2, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }

    @Test
    public void filterAll() {
        var stableBefore = new GTBefore.Stable(simpleGTAfter());

        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(0);

        var finalGT = unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .filterLine(1)
                        .filterLine(2)
                        .filterLine(3)
                        .finishFiltering())
                .finalizeGT();

        Assertions.assertEquals(0, finalGT.size());
    }

    @Test
    public void fullPipeline() {
        var stableBefore = new GTBefore.Stable(simpleGTAfter());

        GTAfter.UnstableEmpty unstableAfter = new GTAfter.UnstableEmpty(5);

        unstableAfter.insert(new LineAnnotation(2, null, null))
                .insert(new LineAnnotation(4, null, null))
                .insert(new LineAnnotation(5, null, null));

        var finalGT = unstableAfter
                .finishInsertion()
                .combine(stableBefore
                        .startFiltering()
                        .filterLine(2)
                        .finishFiltering())
                .finalizeGT();

        Assertions.assertEquals(5, finalGT.size());
        for (int i = 0; i < finalGT.size(); i++) {
            Assertions.assertEquals(i+1, finalGT.get(i).lineNumber());
        }
    }
}