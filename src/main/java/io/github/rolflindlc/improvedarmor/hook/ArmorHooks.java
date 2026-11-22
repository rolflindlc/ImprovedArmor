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
 * Glue between Minecraft and the pure formula in {@code core/}. Called from the mixin; keeping the logic
 * here (instead of inside the mixin class) keeps the mixin tiny and this class easy to call from elsewhere.
 */
public final class ArmorHooks {
    private ArmorHooks() {
    }

    /**
     * Replacement for vanilla's armor, Resistance and Protection steps. Mirrors vanilla's rules about what
     * each damage type bypasses: {@code bypasses_armor} skips the armor attributes, {@code bypasses_effects}
     * skips both Resistance and enchantments, and {@code bypasses_resistance} / {@code bypasses_enchantments}
     * skip one layer each.
     */
    public static float damageAfterArmor(LivingEntity entity, DamageSource source, float amount) {
        boolean bypassArmor = source.is(DamageTypeTags.BYPASSES_ARMOR);
        boolean bypassEffects = source.is(DamageTypeTags.BYPASSES_EFFECTS);

        double armor = 0.0;
        double toughness = 0.0;
        if (!bypassArmor) {
            // Not LivingEntity#getArmorValue(): that one floors the attribute to an int.
            armor = entity.getAttributeValue(Attributes.ARMOR);
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

        return (float) ArmorMath.damageAfterArmor(ServerConfig.params(), amount, armor, toughness,
                protection, resistanceLevel, entity instanceof Player);
    }

    /** Whether the damage type is reduced by armor at all (used to decide if armor durability is spent). */
    public static boolean armorApplies(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_ARMOR);
    }
}
