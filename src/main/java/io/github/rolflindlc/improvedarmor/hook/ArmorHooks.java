package io.github.rolflindlc.improvedarmor.hook;

import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorMath;
import io.github.rolflindlc.improvedarmor.registry.ModAttributes;
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
    private record Inputs(double armor, double toughness, double generalEpf, double specificEpf, int resistanceLevel,
                          boolean player) {
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
            // The attacker's armor penetration ignores part of the armor points (not Protection nor Resistance).
            double penetration = source.getEntity() instanceof LivingEntity attacker && attacker != entity
                    ? ModAttributes.penetration(attacker) : 0.0;
            armor = entity.getAttributeValue(Attributes.ARMOR) * Math.max(armorScale, 0.0) * (1.0 - penetration);
            toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        }

        int resistanceLevel = 0;
        double protection = 0.0;
        double specific = 0.0;
        if (!bypassEffects) {
            if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (resistance != null) {
                    resistanceLevel = resistance.getAmplifier() + 1;
                }
            }
            if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                // General = what also protects against an ordinary hit (Protection); specific = the rest (Fire,
                // Blast, Projectile Protection, Feather Falling...), applied afterwards on the damage that is left.
                double total = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), source);
                double general = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), entity.damageSources().generic());
                protection = Math.min(total, general);
                specific = Math.max(0.0, total - protection);
            }
        }
        return new Inputs(armor, toughness, protection, specific, resistanceLevel, entity instanceof Player);
    }

    /** Damage left after armor, Resistance and Protection, replacing vanilla's armor and magic steps. */
    public static double damageAfterArmor(LivingEntity entity, DamageSource source, double amount, double armorScale) {
        Inputs in = inputs(entity, source, armorScale);
        return ArmorMath.damageAfterArmor(ServerConfig.params(), amount, in.armor, in.toughness,
                in.generalEpf, in.specificEpf, in.resistanceLevel, in.player);
    }

    /** Float variant used by the mixin, with the armor attribute untouched. */
    public static float damageAfterArmor(LivingEntity entity, DamageSource source, float amount) {
        return (float) damageAfterArmor(entity, source, (double) amount, 1.0);
    }

    /** General armor points (armor, Protection, Resistance; before the big-hit penalty) against this damage source. */
    public static double effectiveArmor(LivingEntity entity, DamageSource source) {
        Inputs in = inputs(entity, source, 1.0);
        return ArmorMath.effectiveArmor(ServerConfig.params(), in.armor, in.generalEpf, in.resistanceLevel, in.player);
    }

    /** Specific armor points (only against this damage type), applied after the general ones. */
    public static double specificArmor(LivingEntity entity, DamageSource source) {
        return ArmorMath.specificArmor(ServerConfig.params(), inputs(entity, source, 1.0).specificEpf);
    }

    /** General and specific enchantment protection factors of the entity against this source: {general, specific}. */
    public static double[] epf(LivingEntity entity, DamageSource source) {
        Inputs in = inputs(entity, source, 1.0);
        return new double[] {in.generalEpf, in.specificEpf};
    }

    /** Whether the damage type is reduced by armor at all (used to decide if armor durability is spent). */
    public static boolean armorApplies(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_ARMOR);
    }
}
