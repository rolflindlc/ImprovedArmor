package io.github.rolflindlc.improvedarmor.hook;

import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorMath;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Glue between Minecraft and the pure formula in {@code core/}. Called from the mixin and from the public
 * API; keeping the logic here (instead of inside the mixin class) keeps the mixin tiny.
 */
public final class ArmorHooks {
    private ArmorHooks() {
    }

    /** Everything the formula needs to know about one entity being hit by one damage source. */
    private record Inputs(double armor, double toughness, double protection, int resistanceLevel, boolean player) {
    }

    /**
     * Mirrors vanilla's rules about what each damage type bypasses: {@code bypasses_armor} skips the armor
     * attributes, {@code bypasses_effects} skips both Resistance and enchantments, and
     * {@code bypasses_resistance} / {@code bypasses_enchantments} skip one layer each.
     *
     * @param armorScale multiplies the armor attribute (1 = unchanged); used to simulate ignoring part of it
     */
    private static Inputs inputs(LivingEntity entity, DamageSource source, double armorScale) {
        boolean bypassArmor = source.is(DamageTypeTags.BYPASSES_ARMOR);
        boolean bypassEffects = source.is(DamageTypeTags.BYPASSES_EFFECTS);

        double armor = 0.0;
        double toughness = 0.0;
        if (!bypassArmor) {
            // Not LivingEntity#getArmorValue(): that one floors the attribute to an int.
            armor = entity.getAttributeValue(Attributes.ARMOR) * Math.max(armorScale, 0.0);
            toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        }

        int resistanceLevel = 0;
        double protection = 0.0;
        if (!bypassEffects) {
            if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (resistance != null) {
                    resistanceLevel = resistance.getAmplifier() + 1;
                }
            }
            if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                protection = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), source);
            }
        }
        return new Inputs(armor, toughness, protection, resistanceLevel, entity instanceof Player);
    }

    /** Damage left after armor, Resistance and Protection, replacing vanilla's armor and magic steps. */
    public static double damageAfterArmor(LivingEntity entity, DamageSource source, double amount, double armorScale) {
        Inputs in = inputs(entity, source, armorScale);
        return ArmorMath.damageAfterArmor(ServerConfig.params(), amount, in.armor, in.toughness,
                in.protection, in.resistanceLevel, in.player);
    }

    /** Float variant used by the mixin, with the armor attribute untouched. */
    public static float damageAfterArmor(LivingEntity entity, DamageSource source, float amount) {
        return (float) damageAfterArmor(entity, source, (double) amount, 1.0);
    }

    /** Armor points from every source (before the big-hit penalty) that apply against this damage source. */
    public static double effectiveArmor(LivingEntity entity, DamageSource source) {
        Inputs in = inputs(entity, source, 1.0);
        return ArmorMath.effectiveArmor(ServerConfig.params(), in.armor, in.protection, in.resistanceLevel, in.player);
    }

    /** Whether the damage type is reduced by armor at all (used to decide if armor durability is spent). */
    public static boolean armorApplies(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_ARMOR);
    }
}
