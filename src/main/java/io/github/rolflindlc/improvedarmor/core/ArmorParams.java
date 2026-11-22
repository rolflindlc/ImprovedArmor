package io.github.rolflindlc.improvedarmor.core;

/**
 * Tunable constants of the armor formula. Immutable and free of Minecraft classes, so it can be
 * unit tested and shared with the public API.
 *
 * @param k                       curve constant: {@code reduction = A / (A + k)}. Higher means less protection.
 * @param pointsPerEpf            armor points granted by one unit of enchantment protection factor (EPF).
 * @param pointsPerResistance     armor points granted by each Resistance level (Resistance I = 1).
 * @param maxEpf                  cap on the EPF that counts (vanilla caps it at 20).
 * @param nonPlayerArmorCap       cap on the effective armor of non-player entities (mobs, bosses).
 * @param immuneResistanceLevel   Resistance level that makes the entity immune (vanilla: 5). 0 disables it.
 */
public record ArmorParams(
        double k,
        double pointsPerEpf,
        double pointsPerResistance,
        double maxEpf,
        double nonPlayerArmorCap,
        int immuneResistanceLevel) {

    public static final double DEFAULT_K = 22.0;
    public static final double DEFAULT_POINTS_PER_EPF = 1.5;
    public static final double DEFAULT_POINTS_PER_RESISTANCE = 5.0;
    public static final double DEFAULT_MAX_EPF = 20.0;
    public static final double DEFAULT_NON_PLAYER_ARMOR_CAP = 20.0;
    public static final int DEFAULT_IMMUNE_RESISTANCE_LEVEL = 5;

    public ArmorParams {
        if (!(k > 0.0) || Double.isInfinite(k)) {
            throw new IllegalArgumentException("k must be a positive finite number, got " + k);
        }
        if (pointsPerEpf < 0.0 || pointsPerResistance < 0.0 || maxEpf < 0.0 || nonPlayerArmorCap < 0.0
                || immuneResistanceLevel < 0) {
            throw new IllegalArgumentException("armor parameters must not be negative");
        }
    }

    /** The values chosen in the design: K = 22, 1.5 points per EPF, 5 per Resistance level, mob cap 20. */
    public static ArmorParams defaults() {
        return new ArmorParams(DEFAULT_K, DEFAULT_POINTS_PER_EPF, DEFAULT_POINTS_PER_RESISTANCE,
                DEFAULT_MAX_EPF, DEFAULT_NON_PLAYER_ARMOR_CAP, DEFAULT_IMMUNE_RESISTANCE_LEVEL);
    }
}
