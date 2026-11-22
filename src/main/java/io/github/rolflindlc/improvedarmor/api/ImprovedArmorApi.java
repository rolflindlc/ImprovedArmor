package io.github.rolflindlc.improvedarmor.api;

import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorMath;
import io.github.rolflindlc.improvedarmor.hook.ArmorHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Small public API for other mods. It is meant to be called <b>optionally</b>: check that the mod
 * {@code improvedarmor} is loaded first, and call it through reflection (or a soft-dependency bridge) so
 * your mod keeps working without it. Method names and signatures are stable within {@link #API_VERSION}.
 *
 * <p>All methods are static and thread-safe. {@link #isActive()} is false when the server config turns the
 * mod off; in that case vanilla rules apply and callers should use their vanilla maths.
 */
public final class ImprovedArmorApi {
    /** Bumped only when a method is removed or changes meaning. */
    public static final int API_VERSION = 1;

    private ImprovedArmorApi() {
    }

    /** True while the flat armor curve is applied (the master switch of the server config is on). */
    public static boolean isActive() {
        return ServerConfig.enabled();
    }

    /** Curve constant K: {@code reduction = A / (A + K)}. */
    public static double k() {
        return ServerConfig.params().k();
    }

    /**
     * Damage the entity would take from one hit after armor, Resistance and Protection, exactly as the
     * mixin computes it. {@code armorScale} multiplies the armor attribute (1 = untouched, 0.7 = pretend
     * the attacker ignores 30% of it); toughness, Resistance and Protection are not scaled.
     */
    public static double damageAfterArmor(LivingEntity entity, DamageSource source, double damage, double armorScale) {
        return ArmorHooks.damageAfterArmor(entity, source, damage, armorScale);
    }

    /** Same as {@link #damageAfterArmor(LivingEntity, DamageSource, double, double)} with the armor untouched. */
    public static double damageAfterArmor(LivingEntity entity, DamageSource source, double damage) {
        return ArmorHooks.damageAfterArmor(entity, source, damage, 1.0);
    }

    /** Armor points from every source (attribute, Protection, Resistance) that apply against this source. */
    public static double effectiveArmor(LivingEntity entity, DamageSource source) {
        return ArmorHooks.effectiveArmor(entity, source);
    }

    /** Damage reduction in [0, 1) for a hit of the given size, including the big-hit penalty. */
    public static double reduction(LivingEntity entity, DamageSource source, double damage) {
        if (!(damage > 0.0)) {
            return 0.0;
        }
        return 1.0 - ArmorHooks.damageAfterArmor(entity, source, damage, 1.0) / damage;
    }

    /** Armor points that give a reduction {@code r} in [0, 1) against small hits: {@code K * r / (1 - r)}. */
    public static double armorForReduction(double reduction) {
        return ArmorMath.armorForReduction(reduction, k());
    }
}
