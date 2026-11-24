package io.github.rolflindlc.improvedarmor.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorMath;
import io.github.rolflindlc.improvedarmor.core.ArmorParams;
import io.github.rolflindlc.improvedarmor.hook.ArmorHooks;
import io.github.rolflindlc.improvedarmor.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * {@code /improvedarmor [target] [damage]}: breakdown of an entity's armor. Without a target it shows your own (anyone
 * can use it); another entity needs permission level 2. {@code damage} is the size of the example hit (5 by default).
 * Texts have English fallbacks so it also reads well with a client that does not have the mod.
 */
public final class ArmorCommand {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record Kind(String key, String fallback, ResourceKey<DamageType> type) {
    }

    private static final Kind[] KINDS = {
            new Kind("fire", "fire", DamageTypes.IN_FIRE), new Kind("explosion", "explosions", DamageTypes.EXPLOSION),
            new Kind("projectile", "projectiles", DamageTypes.ARROW), new Kind("fall", "falls", DamageTypes.FALL),
    };

    private ArmorCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("improvedarmor")
                .executes(c -> show(c.getSource(), c.getSource().getEntityOrException(), 5))
                .then(Commands.argument("target", EntityArgument.entity()).requires(s -> s.hasPermission(2))
                        .executes(c -> show(c.getSource(), EntityArgument.getEntity(c, "target"), 5))
                        .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0.1, 10000))
                                .executes(c -> show(c, DoubleArgumentType.getDouble(c, "damage"))))));
    }

    private static int show(CommandContext<CommandSourceStack> c, double damage) throws CommandSyntaxException {
        return show(c.getSource(), EntityArgument.getEntity(c, "target"), damage);
    }

    private static int show(CommandSourceStack src, Entity e, double damage) {
        if (!(e instanceof LivingEntity target)) {
            src.sendFailure(t("not_living", "%s has no armor.", e.getDisplayName()));
            return 0;
        }
        ArmorParams p = ServerConfig.params();
        DamageSource hit = target.damageSources().generic();
        double[] epf = ArmorHooks.epf(target, hit);
        double armor = target.getAttributeValue(Attributes.ARMOR);
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        MobEffectInstance res = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        int resLevel = res == null ? 0 : res.getAmplifier() + 1;
        double general = ArmorHooks.effectiveArmor(target, hit);
        double after = ArmorHooks.damageAfterArmor(target, hit, damage, 1.0);
        double reduction = damage > 0 ? 1 - after / damage : 0;

        src.sendSuccess(() -> t("header", "Armor of %s (example hit of %s):", target.getDisplayName(), num(damage))
                .withStyle(ChatFormatting.GOLD), false);
        if (!ServerConfig.enabled()) {
            src.sendSuccess(() -> t("disabled", "Improved Armor is turned off in the server config: vanilla rules apply.")
                    .withStyle(ChatFormatting.RED), false);
        }
        src.sendSuccess(() -> t("armor", "Armor: %s (%s) · Toughness: %s", num(armor), pieces(target), num(toughness)), false);
        if (epf[0] > 0) {
            src.sendSuccess(() -> t("protection", "Protection: EPF %s → +%s points", num(epf[0]),
                    num(Math.min(epf[0], p.maxEpf()) * p.pointsPerEpf())), false);
        }
        if (resLevel > 0) {
            src.sendSuccess(() -> t("resistance", "Resistance %s → +%s points", resLevel,
                    num(resLevel * p.pointsPerResistance())), false);
        }
        src.sendSuccess(() -> t("total", "Effective armor: %s → takes away %s%% of the hit (effective health ×%s)",
                num(general), num(reduction * 100), num(ArmorMath.effectiveHealthMultiplier(general, p.k())))
                .withStyle(ChatFormatting.GREEN), false);
        double pen = ModAttributes.penetration(target);
        if (pen > 0) src.sendSuccess(() -> t("penetration", "Own armor penetration: %s%%", num(pen * 100)), false);

        var types = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (Kind k : KINDS) {
            var holder = types.getHolder(k.type());
            if (holder.isEmpty()) continue;
            double specific = ArmorHooks.specificArmor(target, new DamageSource(holder.get()));
            if (specific <= 0) continue;
            double extra = ArmorMath.reduction(specific, p.k());
            src.sendSuccess(() -> t("specific", "Against %s: +%s points → another %s%% off the rest",
                    t("kind." + k.key(), k.fallback()), num(specific), num(extra * 100)).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static MutableComponent pieces(LivingEntity target) {
        StringBuilder sb = new StringBuilder();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            double v = 0;
            if (!stack.isEmpty()) {
                for (AttributeModifier m : stack.getAttributeModifiers(slot).get(Attributes.ARMOR)) {
                    if (m.getOperation() == AttributeModifier.Operation.ADDITION) v += m.getAmount();
                }
            }
            if (sb.length() > 0) sb.append(" / ");
            sb.append(num(v));
        }
        return Component.literal(sb.toString());
    }

    private static MutableComponent t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("command.improvedarmor." + key, fallback, args);
    }

    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
