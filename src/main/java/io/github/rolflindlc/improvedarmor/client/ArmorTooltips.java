package io.github.rolflindlc.improvedarmor.client;

import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorMath;
import io.github.rolflindlc.improvedarmor.core.ArmorParams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tooltip of armor pieces (client). Always one line: how much of an ordinary hit the piece takes away for you (if you
 * wear it) or how your reduction would change if you put it on. With Shift: its armor and toughness, the points of
 * each protecting enchantment and the extra defense against each damage type (applied after the general one).
 */
public final class ArmorTooltips {
    private record Kind(String key, String fallback, ResourceKey<DamageType> type) {
    }

    private static final Kind[] KINDS = {
            new Kind("fire", "fire", DamageTypes.IN_FIRE), new Kind("explosion", "explosions", DamageTypes.EXPLOSION),
            new Kind("projectile", "projectiles", DamageTypes.ARROW), new Kind("fall", "falls", DamageTypes.FALL),
    };

    private ArmorTooltips() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity() != null ? event.getEntity() : Minecraft.getInstance().player;
        if (player == null || stack.isEmpty() || !ClientConfig.TOOLTIPS.get() || !ServerConfig.enabled()) return;
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return;
        try {
            add(event.getToolTip(), player, stack, slot);
        } catch (RuntimeException ignored) {
            // Never break a tooltip because of us.
        }
    }

    private static void add(List<Component> lines, Player player, ItemStack stack, EquipmentSlot slot) {
        ArmorParams p = ServerConfig.params();
        DamageSource hit = player.damageSources().generic();
        double itemArmor = additions(stack, slot, Attributes.ARMOR);
        double itemTough = additions(stack, slot, Attributes.ARMOR_TOUGHNESS);
        double itemGen = epf(stack, hit);
        if (itemArmor <= 0 && itemGen <= 0 && specificTotal(player, stack) <= 0) return;

        double playerGen = 0;
        for (ItemStack s : player.getArmorSlots()) playerGen += epf(s, hit);
        MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        double resPts = res == null ? 0 : (res.getAmplifier() + 1) * p.pointsPerResistance();
        double now = Math.max(0, player.getAttributeValue(Attributes.ARMOR)) + Math.min(playerGen, p.maxEpf()) * p.pointsPerEpf() + resPts;

        ItemStack worn = player.getItemBySlot(slot);
        boolean wearing = worn == stack;
        double delta;
        if (wearing) {
            double without = now - itemArmor - (Math.min(playerGen, p.maxEpf()) - Math.min(Math.max(0, playerGen - itemGen), p.maxEpf())) * p.pointsPerEpf();
            delta = ArmorMath.reduction(now, p.k()) - ArmorMath.reduction(Math.max(0, without), p.k());
        } else {
            double curArmor = worn.isEmpty() ? 0 : additions(worn, slot, Attributes.ARMOR);
            double curGen = worn.isEmpty() ? 0 : epf(worn, hit);
            double gen = Math.max(0, playerGen - curGen + itemGen);
            double with = now - curArmor + itemArmor + (Math.min(gen, p.maxEpf()) - Math.min(playerGen, p.maxEpf())) * p.pointsPerEpf();
            delta = ArmorMath.reduction(Math.max(0, with), p.k()) - ArmorMath.reduction(now, p.k());
        }
        String pct = (delta >= 0 ? "+" : "") + num(delta * 100);
        ChatFormatting color = delta > 0.0005 ? ChatFormatting.GREEN : delta < -0.0005 ? ChatFormatting.RED : ChatFormatting.GRAY;
        lines.add(wearing
                ? t("worn", "Takes away %s%% of an ordinary hit for you", pct).withStyle(color)
                : t("equip", "If you wear it: %s%% damage reduction", pct).withStyle(color));

        if (!Screen.hasShiftDown()) {
            lines.add(t("more", "Shift: details").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(t("armor", "Armor +%s · Toughness +%s", num(itemArmor), num(itemTough)).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            int g = e.getKey().getDamageProtection(e.getValue(), hit);
            if (g > 0) {
                lines.add(t("enchant", "%s: +%s armor points", e.getKey().getFullname(e.getValue()), num(Math.min(g, p.maxEpf()) * p.pointsPerEpf()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        var types = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (Kind k : KINDS) {
            var holder = types.getHolder(k.type());
            if (holder.isEmpty()) continue;
            double specific = Math.max(0, epf(stack, new DamageSource(holder.get())) - itemGen);
            if (specific <= 0) continue;
            double pts = ArmorMath.specificArmor(p, specific);
            lines.add(t("specific", "Against %s: +%s points (another %s%% off after the general defense)",
                    t("kind." + k.key(), k.fallback()), num(pts), num(ArmorMath.reduction(pts, p.k()) * 100)).withStyle(ChatFormatting.AQUA));
        }
    }

    private static double specificTotal(Player player, ItemStack stack) {
        var types = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        double gen = epf(stack, player.damageSources().generic());
        double sum = 0;
        for (Kind k : KINDS) {
            var holder = types.getHolder(k.type());
            if (holder.isPresent()) sum += Math.max(0, epf(stack, new DamageSource(holder.get())) - gen);
        }
        return sum;
    }

    private static double epf(ItemStack stack, DamageSource source) {
        if (stack.isEmpty()) return 0;
        int sum = 0;
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            sum += e.getKey().getDamageProtection(e.getValue(), source);
        }
        return sum;
    }

    private static double additions(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        double v = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(slot).get(attribute)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) v += m.getAmount();
        }
        return v;
    }

    private static MutableComponent t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("tooltip.improvedarmor." + key, fallback, args);
    }

    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
