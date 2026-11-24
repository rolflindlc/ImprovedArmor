package io.github.rolflindlc.improvedarmor.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Client-only settings of the armor HUD ({@code improvedarmor-client.toml}). */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    static final ForgeConfigSpec.IntValue MAX_ROWS;
    static final ForgeConfigSpec.BooleanValue SHOW_NUMBER;
    static final ForgeConfigSpec.BooleanValue TOOLTIPS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> COLOR_OVERRIDES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Improved Armor HUD: replaces the vanilla armor bar with one where every point is colored by where it comes from")
                .push("hud");
        HUD_ENABLED = b.comment("Draw the colored armor bar. When false, the vanilla bar is shown.")
                .define("enabled", true);
        MAX_ROWS = b.comment("Rows of 20 points stacked before the bar collapses to one proportional row plus the total number.")
                .defineInRange("maxRows", 3, 1, 10);
        SHOW_NUMBER = b.comment("Always print the total armor points next to the bar (it is always printed once the bar collapses).")
                .define("showNumber", false);
        TOOLTIPS = b.comment("Show on armor pieces how much damage they take away (Shift for details).")
                .define("tooltips", true);
        COLOR_OVERRIDES = b.comment("Color of armor pieces, enchantments or effects, overriding the automatic one.",
                        "Format: \"<registry id>=#RRGGBB\", e.g. \"minecraft:iron_chestplate=#D8D8D8\",",
                        "\"minecraft:protection=#B57CFF\" (enchantment) or \"minecraft:resistance=#9146F0\" (effect).")
                .defineListAllowEmpty("colorOverrides", List.of(), o -> o instanceof String s && s.contains("=#"));
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
