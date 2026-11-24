package io.github.rolflindlc.improvedarmor.core;

/**
 * The flat armor curve. Plain Java with no Minecraft classes.
 *
 * <p>Two steps: the <b>general</b> defense (armor, Protection and Resistance, which work against every hit) and then,
 * on the damage that is left, the <b>specific</b> defense against that damage type (Fire/Blast/Projectile
 * Protection, Feather Falling...), with the same curve:
 *
 * <pre>
 * A_gen     = armor * (1 - penetration) + pointsPerEpf * min(generalEPF, maxEpf) + pointsPerResistance * resistanceLevel
 * A_gen     = max(A_gen / 5, A_gen - damage / (2 + toughness / 4))     (big-hit penalty, as in vanilla)
 * A_spec    = pointsPerEpf * min(specificEPF, maxEpf)
 * damage'   = damage * (1 - A_gen / (A_gen + K)) * (1 - A_spec / (A_spec + K))
 * </pre>
 *
 * Effective health of each step is {@code 1 + A / K}, i.e. linear in armor; the two steps multiply.
 */
public final class ArmorMath {
    private ArmorMath() {
    }

    /**
     * Armor points from every source, before the big-hit penalty.
     *
     * @param armor             value of the armor attribute (equipment, effects, other mods...)
     * @param epf               enchantment protection factor that applies to the damage type
     * @param resistanceLevel   Resistance effect level (amplifier + 1), 0 if none
     * @param player            whether the entity is a player; non-players are capped by {@link ArmorParams#nonPlayerArmorCap()}
     */
    public static double effectiveArmor(ArmorParams p, double armor, double epf, int resistanceLevel, boolean player) {
        double total = sane(armor)
                + Math.min(Math.max(sane(epf), 0.0), p.maxEpf()) * p.pointsPerEpf()
                + Math.max(resistanceLevel, 0) * p.pointsPerResistance();
        total = Math.max(total, 0.0);
        return player ? total : Math.min(total, p.nonPlayerArmorCap());
    }

    /** Vanilla's big-hit penalty: strong hits make armor less effective, toughness softens that. */
    public static double bigHitAdjusted(double effectiveArmor, double damage, double toughness) {
        if (!(effectiveArmor > 0.0)) {
            return 0.0;
        }
        double t = Math.max(sane(toughness), 0.0);
        double d = Math.max(sane(damage), 0.0);
        return Math.max(effectiveArmor / 5.0, effectiveArmor - d / (2.0 + t / 4.0));
    }

    /** Damage reduction in [0, 1) for the given effective armor. */
    public static double reduction(double effectiveArmor, double k) {
        if (!(effectiveArmor > 0.0)) {
            return 0.0;
        }
        return effectiveArmor / (effectiveArmor + k);
    }

    /** Effective-health multiplier ({@code 1 / (1 - reduction)}) for the given effective armor. */
    public static double effectiveHealthMultiplier(double effectiveArmor, double k) {
        return 1.0 + Math.max(effectiveArmor, 0.0) / k;
    }

    /**
     * Armor points needed to reach a given reduction {@code r} in [0, 1). Useful to convert values that
     * were balanced against another curve (e.g. {@code A = K * r / (1 - r)}).
     */
    public static double armorForReduction(double r, double k) {
        if (!(r >= 0.0 && r < 1.0)) {
            throw new IllegalArgumentException("reduction must be in [0, 1), got " + r);
        }
        return k * r / (1.0 - r);
    }

    /** Armor points of the specific defense (Protection that only works against this damage type). */
    public static double specificArmor(ArmorParams p, double specificEpf) {
        return Math.min(Math.max(sane(specificEpf), 0.0), p.maxEpf()) * p.pointsPerEpf();
    }

    /**
     * Damage left after both steps for one hit.
     *
     * @param damage            incoming damage
     * @param armor             armor attribute value (already reduced by the attacker's penetration)
     * @param toughness         armor toughness attribute value
     * @param generalEpf        enchantment protection factor that works against every hit (Protection)
     * @param specificEpf       extra enchantment protection factor that only works against this damage type
     * @param resistanceLevel   Resistance level (amplifier + 1), 0 if none
     * @param player            whether the victim is a player
     */
    public static double damageAfterArmor(ArmorParams p, double damage, double armor, double toughness,
                                          double generalEpf, double specificEpf, int resistanceLevel, boolean player) {
        double afterGeneral = damageAfterArmor(p, damage, armor, toughness, generalEpf, resistanceLevel, player);
        if (!(afterGeneral > 0.0)) {
            return afterGeneral;
        }
        return afterGeneral * (1.0 - reduction(specificArmor(p, specificEpf), p.k()));
    }

    /**
     * Damage left after the general step only (armor, Resistance and general Protection) for one hit.
     *
     * @param damage            incoming damage (already past absorption-independent modifiers)
     * @param armor             armor attribute value
     * @param toughness         armor toughness attribute value
     * @param epf               general enchantment protection factor
     * @param resistanceLevel   Resistance level (amplifier + 1), 0 if none
     * @param player            whether the victim is a player
     */
    public static double damageAfterArmor(ArmorParams p, double damage, double armor, double toughness,
                                          double epf, int resistanceLevel, boolean player) {
        if (!(damage > 0.0)) {
            return damage;
        }
        if (p.immuneResistanceLevel() > 0 && resistanceLevel >= p.immuneResistanceLevel()) {
            return 0.0;
        }
        double a = effectiveArmor(p, armor, epf, resistanceLevel, player);
        double adjusted = bigHitAdjusted(a, damage, toughness);
        return damage * (1.0 - reduction(adjusted, p.k()));
    }

    /** NaN and infinities count as 0 so a broken attribute can never produce NaN damage. */
    private static double sane(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? 0.0 : v;
    }
}
