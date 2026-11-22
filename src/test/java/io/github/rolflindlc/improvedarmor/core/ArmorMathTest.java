package io.github.rolflindlc.improvedarmor.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ArmorMathTest {
    private static final double EPS = 1e-9;
    private final ArmorParams p = ArmorParams.defaults();

    private static double ehp(double armor, double k) {
        return 1.0 / (1.0 - ArmorMath.reduction(armor, k));
    }

    @Test
    public void noArmorLeavesDamageUntouched() {
        assertEquals(10.0, ArmorMath.damageAfterArmor(p, 10, 0, 0, 0, 0, true), EPS);
    }

    @Test
    public void ironSetSmallHitReduction() {
        assertEquals(15.0 / 37.0, ArmorMath.reduction(15, 22), EPS);
    }

    @Test
    public void fullDiamondTenDamageHit() {
        // A = 20, toughness 8: adjusted = 20 - 10 / (2 + 2) = 17.5 -> damage = 10 * 22 / 39.5
        assertEquals(10.0 * 22.0 / 39.5, ArmorMath.damageAfterArmor(p, 10, 20, 8, 0, 0, true), EPS);
    }

    @Test
    public void bigHitPenaltyMatchesVanillaTerm() {
        assertEquals(10.0, ArmorMath.bigHitAdjusted(15, 10, 0), EPS);
        assertEquals(17.5, ArmorMath.bigHitAdjusted(20, 10, 8), EPS);
        // Huge hits floor at A / 5, as in vanilla.
        assertEquals(2.0, ArmorMath.bigHitAdjusted(10, 1000, 0), EPS);
    }

    @Test
    public void toughnessReducesDamageTaken() {
        double without = ArmorMath.damageAfterArmor(p, 10, 20, 0, 0, 0, true);
        double with = ArmorMath.damageAfterArmor(p, 10, 20, 8, 0, 0, true);
        assertTrue(with < without);
    }

    @Test
    public void effectiveHealthGrowsLinearlyWithArmor() {
        double step = 5.0 / p.k();
        for (double a = 0; a <= 200; a += 5) {
            assertEquals(step, ehp(a + 5, p.k()) - ehp(a, p.k()), 1e-9);
        }
        assertEquals(1.0 + 20.0 / 22.0, ArmorMath.effectiveHealthMultiplier(20, 22), EPS);
    }

    @Test
    public void reductionNeverReachesOne() {
        assertTrue(ArmorMath.reduction(1_000_000, p.k()) < 1.0);
    }

    @Test
    public void moreArmorAlwaysMeansLessDamage() {
        double previous = Double.MAX_VALUE;
        for (int a = 0; a <= 100; a++) {
            double d = ArmorMath.damageAfterArmor(p, 10, a, 0, 0, 0, true);
            assertTrue(d < previous);
            previous = d;
        }
    }

    @Test
    public void protectionCountsAsArmorAndIsCapped() {
        // Protection IV on four pieces: 16 EPF * 1.5 = 24 points.
        assertEquals(24.0, ArmorMath.effectiveArmor(p, 0, 16, 0, true), EPS);
        // Diamond + Protection IV: 20 + 24 = 44 points -> 2/3 reduction against a tiny hit.
        assertEquals(2.0 / 3.0, ArmorMath.reduction(ArmorMath.effectiveArmor(p, 20, 16, 0, true), p.k()), EPS);
        // EPF above the cap of 20 does not count (20 * 1.5 = 30).
        assertEquals(30.0, ArmorMath.effectiveArmor(p, 0, 30, 0, true), EPS);
    }

    @Test
    public void resistanceAddsFivePointsPerLevel() {
        assertEquals(20.0, ArmorMath.effectiveArmor(p, 0, 0, 4, true), EPS);
        assertEquals(5.0, ArmorMath.effectiveArmor(p, 0, 0, 1, true), EPS);
    }

    @Test
    public void resistanceFiveIsImmuneAsInVanilla() {
        assertEquals(0.0, ArmorMath.damageAfterArmor(p, 10, 0, 0, 0, 5, true), EPS);
        assertEquals(0.0, ArmorMath.damageAfterArmor(p, 10, 0, 0, 0, 255, true), EPS);
        assertTrue(ArmorMath.damageAfterArmor(p, 10, 0, 0, 0, 4, true) > 0.0);
    }

    @Test
    public void immunityCanBeDisabled() {
        ArmorParams noImmunity = new ArmorParams(22, 1.5, 5, 20, 20, 0);
        assertTrue(ArmorMath.damageAfterArmor(noImmunity, 10, 0, 0, 0, 5, true) > 0.0);
    }

    @Test
    public void nonPlayersAreCappedPlayersAreNot() {
        assertEquals(20.0, ArmorMath.effectiveArmor(p, 50, 0, 0, false), EPS);
        assertEquals(50.0, ArmorMath.effectiveArmor(p, 50, 0, 0, true), EPS);
    }

    @Test
    public void negativeArmorNeverIncreasesDamage() {
        assertEquals(0.0, ArmorMath.effectiveArmor(p, -10, 0, 0, true), EPS);
        assertEquals(10.0, ArmorMath.damageAfterArmor(p, 10, -10, 0, 0, 0, true), EPS);
        // Other sources can still offset negative armor: -10 + 10 * 1.5 = 5.
        assertEquals(5.0, ArmorMath.effectiveArmor(p, -10, 10, 0, true), EPS);
    }

    @Test
    public void armorForReductionIsTheInverseOfReduction() {
        assertEquals(22.0 * 0.4 / 0.6, ArmorMath.armorForReduction(0.4, 22), EPS);
        for (double r = 0.0; r < 0.95; r += 0.05) {
            assertEquals(r, ArmorMath.reduction(ArmorMath.armorForReduction(r, 22), 22), 1e-9);
        }
        try {
            ArmorMath.armorForReduction(1.0, 22);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void brokenInputsNeverProduceNaN() {
        double d = ArmorMath.damageAfterArmor(p, 10, Double.NaN, Double.NaN, Double.NaN, 0, true);
        assertEquals(10.0, d, EPS);
        assertEquals(10.0, ArmorMath.damageAfterArmor(p, 10, Double.POSITIVE_INFINITY * 0, 0, 0, 0, true), EPS);
    }

    @Test
    public void zeroOrNegativeDamageIsReturnedAsIs() {
        assertEquals(0.0, ArmorMath.damageAfterArmor(p, 0, 20, 8, 0, 0, true), EPS);
        assertEquals(-3.0, ArmorMath.damageAfterArmor(p, -3, 20, 8, 0, 0, true), EPS);
    }

    @Test
    public void invalidParametersAreRejected() {
        assertRejected(0, 1.5, 5, 20, 20, 5);
        assertRejected(-1, 1.5, 5, 20, 20, 5);
        assertRejected(Double.NaN, 1.5, 5, 20, 20, 5);
        assertRejected(22, -1, 5, 20, 20, 5);
        assertRejected(22, 1.5, 5, 20, 20, -1);
    }

    private static void assertRejected(double k, double epf, double res, double maxEpf, double cap, int immune) {
        try {
            new ArmorParams(k, epf, res, maxEpf, cap, immune);
            fail("expected IllegalArgumentException for k=" + k);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
