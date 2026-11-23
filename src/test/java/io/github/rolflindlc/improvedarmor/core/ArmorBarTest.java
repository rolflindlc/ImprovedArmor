package io.github.rolflindlc.improvedarmor.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArmorBarTest {

    @Test
    public void emptyBar() {
        ArmorBar.Layout l = ArmorBar.layout(new double[] {0, 0}, 3);
        assertEquals(0, l.total());
        assertEquals(0, l.owners().length);
        assertEquals(1, l.rows());
        assertFalse(l.compact());
    }

    @Test
    public void ironSetKeepsPieceOrder() {
        // helmet 2, chestplate 6, leggings 5, boots 2
        ArmorBar.Layout l = ArmorBar.layout(new double[] {2, 6, 5, 2}, 3);
        assertEquals(15, l.total());
        assertEquals(1, l.rows());
        assertArrayEquals(new int[] {0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3}, l.owners());
    }

    @Test
    public void resistanceAddsASecondRow() {
        // full diamond (20) + Resistance II (10)
        ArmorBar.Layout l = ArmorBar.layout(new double[] {3, 8, 6, 3, 0, 10}, 3);
        assertEquals(30, l.total());
        assertEquals(2, l.rows());
        assertEquals(5, l.owners()[29]);
        assertEquals(3, l.owners()[19]);
    }

    @Test
    public void zeroSourcesAreSkipped() {
        ArmorBar.Layout l = ArmorBar.layout(new double[] {0, 4, 0, 2}, 3);
        assertArrayEquals(new int[] {1, 1, 1, 1, 3, 3}, l.owners());
    }

    @Test
    public void fractionalPointsRoundFairly() {
        // 1.5 + 1.5 = 3 points: one each, the middle one goes to the first source (1.5 covers 0.5 and 1.5 is the edge)
        ArmorBar.Layout l = ArmorBar.layout(new double[] {1.5, 1.5}, 3);
        assertEquals(3, l.total());
        assertArrayEquals(new int[] {0, 0, 1}, l.owners());
    }

    @Test
    public void overflowCollapsesToOneProportionalRow() {
        ArmorBar.Layout l = ArmorBar.layout(new double[] {40, 40}, 3);
        assertEquals(80, l.total());
        assertTrue(l.compact());
        assertEquals(1, l.rows());
        assertEquals(ArmorBar.POINTS_PER_ROW, l.owners().length);
        assertEquals(0, l.owners()[9]);
        assertEquals(1, l.owners()[10]);
    }

    @Test
    public void exactlyMaxRowsIsNotCompact() {
        ArmorBar.Layout l = ArmorBar.layout(new double[] {60}, 3);
        assertFalse(l.compact());
        assertEquals(3, l.rows());
    }
}
