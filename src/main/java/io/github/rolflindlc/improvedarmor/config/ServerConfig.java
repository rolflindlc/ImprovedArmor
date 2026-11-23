package io.github.rolflindlc.improvedarmor.config;

import io.github.rolflindlc.improvedarmor.ImprovedArmor;
import io.github.rolflindlc.improvedarmor.core.ArmorParams;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Server-side tunables (world config, synced to clients). The values are copied into an immutable
 * {@link ArmorParams} whenever the config is loaded or reloaded, so the damage hot path never touches
 * the config system.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.DoubleValue K;
    private static final ForgeConfigSpec.DoubleValue POINTS_PER_EPF;
    private static final ForgeConfigSpec.DoubleValue POINTS_PER_RESISTANCE;
    private static final ForgeConfigSpec.DoubleValue MAX_EPF;
    private static final ForgeConfigSpec.DoubleValue NON_PLAYER_ARMOR_CAP;
    private static final ForgeConfigSpec.IntValue IMMUNE_RESISTANCE_LEVEL;

    private static volatile boolean enabled = true;
    private static volatile ArmorParams params = ArmorParams.defaults();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Improved Armor: flat armor curve. reduction = A / (A + K)").push("armor");

        ENABLED = b.comment("Master switch. When false, vanilla armor, Resistance and Protection rules apply.")
                .define("enabled", true);
        K = b.comment("Curve constant K. Higher means less protection per armor point.",
                        "Effective health = 1 + A / K, so with K = 18 every 18 armor points add +100% effective health.")
                .defineInRange("k", ArmorParams.DEFAULT_K, 0.1, 1000.0);
        POINTS_PER_EPF = b.comment("Armor points granted by each point of enchantment protection factor (Protection IV on 4 pieces = 16).")
                .defineInRange("pointsPerEnchantmentProtection", ArmorParams.DEFAULT_POINTS_PER_EPF, 0.0, 10.0);
        POINTS_PER_RESISTANCE = b.comment("Armor points granted by each level of the Resistance effect (Resistance I = 1 level).")
                .defineInRange("pointsPerResistanceLevel", ArmorParams.DEFAULT_POINTS_PER_RESISTANCE, 0.0, 50.0);
        MAX_EPF = b.comment("Cap on the enchantment protection factor that counts (vanilla: 20).")
                .defineInRange("maxEnchantmentProtection", ArmorParams.DEFAULT_MAX_EPF, 0.0, 100.0);
        NON_PLAYER_ARMOR_CAP = b.comment("Cap on the effective armor of non-player entities (mobs, bosses).",
                        "Players have no cap. Raise it to make heavily armored bosses tougher.")
                .defineInRange("nonPlayerArmorCap", ArmorParams.DEFAULT_NON_PLAYER_ARMOR_CAP, 0.0, 1000.0);
        IMMUNE_RESISTANCE_LEVEL = b.comment("Resistance level that makes an entity immune to damage (vanilla: 5). 0 disables it.")
                .defineInRange("immuneResistanceLevel", ArmorParams.DEFAULT_IMMUNE_RESISTANCE_LEVEL, 0, 255);

        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {
    }

    /** True unless the master switch is off. */
    public static boolean enabled() {
        return enabled;
    }

    /** Current formula parameters (defaults until the config is loaded). */
    public static ArmorParams params() {
        return params;
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        refresh(event.getConfig().getSpec());
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        refresh(event.getConfig().getSpec());
    }

    private static void refresh(Object spec) {
        if (spec != SPEC) {
            return;
        }
        try {
            enabled = ENABLED.get();
            params = new ArmorParams(
                    K.get(),
                    POINTS_PER_EPF.get(),
                    POINTS_PER_RESISTANCE.get(),
                    MAX_EPF.get(),
                    NON_PLAYER_ARMOR_CAP.get(),
                    IMMUNE_RESISTANCE_LEVEL.get());
            ImprovedArmor.LOGGER.info("Improved Armor config loaded: enabled={}, {}", enabled, params);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ImprovedArmor.LOGGER.error("Invalid Improved Armor config, keeping the previous values", e);
        }
    }
}
