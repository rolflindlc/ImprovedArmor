package io.github.rolflindlc.improvedarmor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.rolflindlc.improvedarmor.ImprovedArmor;
import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorBar;
import io.github.rolflindlc.improvedarmor.core.ArmorParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Armor HUD (client only, optional). Replaces the vanilla armor bar with one where every point shows where it comes
 * from, in this order:
 * <ol>
 *   <li>each worn piece: armor icon with the color of its texture (border left out) and one shade per slot;</li>
 *   <li>armor from anything else (class bonuses, other mods' modifiers): white armor icon;</li>
 *   <li>accessories in Curios slots: an amulet with the item's color and a gold rim;</li>
 *   <li>Protection-type enchantments: an enchanted book (one color per enchantment) with the moving enchantment shine;</li>
 *   <li>potion effects (Resistance, and effects that add the armor attribute): a bottle with the effect's color.</li>
 * </ol>
 * Points of enchantments and Resistance use the same conversion as the server formula, so the bar is the effective
 * armor against a normal hit. Up to {@code maxRows} rows of 20 points are stacked; past that the bar collapses to one
 * proportional row and the total is printed next to it.
 *
 * <p>Above the bar, a row of small icons shows the protections that only work against one kind of damage (fire,
 * explosions, projectiles, falls and Iron's Spellbooks magic and schools) with the extra armor points they are worth,
 * from highest to lowest, 4 per row. Iron's resistances (percentages) are converted to the armor points that would
 * give the same reduction on top of your current armor.
 */
public final class ArmorHud {
    private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation ICONS = new ResourceLocation(ImprovedArmor.MODID, "textures/gui/hud_icons.png");
    private static final int TEX = 64;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Icon of a source in the bar. */
    enum Kind { ARMOR, AMULET, BOOK, POTION }

    /** One colored part of the bar. */
    record Source(Kind kind, int color, double points) {
    }

    /** One small icon of the specific-protections row: (u, v) in hud_icons.png and extra armor points. */
    record Specific(int u, int v, double points) {
    }

    /** Vanilla protections that only work against one kind of damage, with their icon. */
    private record VanillaType(ResourceKey<DamageType> type, int u) {
    }

    private static final VanillaType[] VANILLA_TYPES = {
            new VanillaType(DamageTypes.IN_FIRE, 0), new VanillaType(DamageTypes.EXPLOSION, 8),
            new VanillaType(DamageTypes.ARROW, 16), new VanillaType(DamageTypes.FALL, 24),
    };

    /** Iron's Spellbooks resistances (attribute ids) and their icon (u, v); the first one is general magic. */
    private static final String[][] IRONS = {
            {"spell_resist", "32", "18"}, {"fire_magic_resist", "40", "18"}, {"ice_magic_resist", "48", "18"},
            {"lightning_magic_resist", "56", "18"}, {"holy_magic_resist", "0", "26"}, {"ender_magic_resist", "8", "26"},
            {"blood_magic_resist", "16", "26"}, {"evocation_magic_resist", "24", "26"}, {"nature_magic_resist", "32", "26"},
            {"eldritch_magic_resist", "40", "26"},
    };

    private ArmorHud() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener((RegisterGuiOverlaysEvent e) ->
                e.registerAbove(VanillaGuiOverlay.ARMOR_LEVEL.id(), "armor_bar", ArmorHud::render));
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> ArmorColors.clear()));
        modBus.addListener((ModConfigEvent.Reloading e) -> ArmorColors.clear());
        MinecraftForge.EVENT_BUS.addListener(ArmorHud::onOverlayPre);
    }

    private static boolean active() {
        return ClientConfig.HUD_ENABLED.get() && ServerConfig.enabled();
    }

    /** Hides the vanilla armor bar while ours is drawn. */
    private static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) && active()) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ bar sources

    static List<Source> sources(Player p) {
        ArmorParams params = ServerConfig.params();
        List<Source> out = new ArrayList<>();
        double attribute = Math.max(0, p.getAttributeValue(Attributes.ARMOR));

        // Worn pieces, accessories and effects: their own armor additions (multipliers end up in "other").
        double[] pieces = new double[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = p.getItemBySlot(SLOTS[i]);
            if (!stack.isEmpty()) pieces[i] = Math.max(0, additions(stack.getAttributeModifiers(SLOTS[i]).get(Attributes.ARMOR)));
        }
        List<CuriosArmor.Piece> curios = List.of();
        if (ModList.get().isLoaded("curios")) {
            try {
                curios = CuriosArmor.pieces(p);
            } catch (RuntimeException | LinkageError e) {
                ImprovedArmor.LOGGER.debug("Improved Armor: Curios armor failed", e);
            }
        }
        Map<MobEffect, Double> effectArmor = new LinkedHashMap<>();
        for (MobEffectInstance inst : p.getActiveEffects()) {
            AttributeModifier m = inst.getEffect().getAttributeModifiers().get(Attributes.ARMOR);
            if (m != null && m.getOperation() == AttributeModifier.Operation.ADDITION) {
                double v = inst.getEffect().getAttributeModifierValue(inst.getAmplifier(), m);
                if (v > 0) effectArmor.merge(inst.getEffect(), v, Double::sum);
            }
        }
        double known = 0;
        for (double v : pieces) known += v;
        for (CuriosArmor.Piece c : curios) known += c.armor();
        for (double v : effectArmor.values()) known += v;
        // If something lowers the attribute below what those sources give, they shrink to fit.
        double scale = known > attribute && known > 0 ? attribute / known : 1;

        for (int i = 0; i < SLOTS.length; i++) {
            if (pieces[i] <= 0) continue;
            out.add(new Source(Kind.ARMOR, ArmorColors.shade(ArmorColors.piece(p.getItemBySlot(SLOTS[i])), i), pieces[i] * scale));
        }
        double other = attribute - known * scale;
        if (other > 0.01) out.add(new Source(Kind.ARMOR, ArmorColors.OTHER, other));
        for (CuriosArmor.Piece c : curios) out.add(new Source(Kind.AMULET, ArmorColors.piece(c.stack()), c.armor() * scale));

        // Enchantments that protect against an ordinary hit, grouped by enchantment, with the EPF cap.
        Map<Enchantment, Double> epf = epfByEnchantment(p, p.damageSources().generic());
        double epfSum = epf.values().stream().mapToDouble(Double::doubleValue).sum();
        double epfScale = epfSum > params.maxEpf() && epfSum > 0 ? params.maxEpf() / epfSum : 1;
        for (Map.Entry<Enchantment, Double> e : epf.entrySet()) {
            int color = ArmorColors.override(ForgeRegistries.ENCHANTMENTS.getKey(e.getKey())).orElse(ArmorColors.ENCHANTMENT);
            out.add(new Source(Kind.BOOK, color, e.getValue() * epfScale * params.pointsPerEpf()));
        }

        // Effects: armor attribute from effects, then Resistance through the flat curve.
        for (Map.Entry<MobEffect, Double> e : effectArmor.entrySet()) {
            out.add(new Source(Kind.POTION, effectColor(e.getKey()), e.getValue() * scale));
        }
        MobEffectInstance resistance = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) {
            out.add(new Source(Kind.POTION, effectColor(MobEffects.DAMAGE_RESISTANCE),
                    (resistance.getAmplifier() + 1) * params.pointsPerResistance()));
        }
        return out;
    }

    private static double additions(Iterable<AttributeModifier> modifiers) {
        double v = 0;
        for (AttributeModifier m : modifiers) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) v += m.getAmount();
        }
        return v;
    }

    private static int effectColor(MobEffect effect) {
        return ArmorColors.override(ForgeRegistries.MOB_EFFECTS.getKey(effect)).orElse(effect.getColor() & 0xFFFFFF);
    }

    private static Map<Enchantment, Double> epfByEnchantment(Player p, DamageSource source) {
        Map<Enchantment, Double> epf = new LinkedHashMap<>();
        for (ItemStack stack : p.getArmorSlots()) {
            for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                int v = e.getKey().getDamageProtection(e.getValue(), source);
                if (v > 0) epf.merge(e.getKey(), (double) v, Double::sum);
            }
        }
        return epf;
    }

    private static double cappedEpf(Player p, DamageSource source, double cap) {
        return Math.min(cap, epfByEnchantment(p, source).values().stream().mapToDouble(Double::doubleValue).sum());
    }

    // ------------------------------------------------------------------ specific protections

    static List<Specific> specifics(Player p, double currentArmor) {
        ArmorParams params = ServerConfig.params();
        List<Specific> out = new ArrayList<>();
        var types = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        double generic = cappedEpf(p, p.damageSources().generic(), params.maxEpf());
        for (VanillaType t : VANILLA_TYPES) {
            var holder = types.getHolder(t.type());
            if (holder.isEmpty()) continue;
            double extra = (cappedEpf(p, new DamageSource(holder.get()), params.maxEpf()) - generic) * params.pointsPerEpf();
            if (extra >= 0.5) out.add(new Specific(t.u(), 18, extra));
        }
        // Iron's Spellbooks: damage multiplier 2 - softcap(resist); converted to the extra armor with the same effect.
        double base = currentArmor + params.k();
        for (String[] row : IRONS) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", row[0]));
            AttributeInstance inst = attribute == null ? null : p.getAttribute(attribute);
            if (inst == null) continue;
            double multiplier = 2 - ironsSoftCap(inst.getValue());
            if (multiplier >= 1 || multiplier <= 0) continue;
            double extra = base / multiplier - base;
            if (extra >= 0.5) out.add(new Specific(Integer.parseInt(row[1]), Integer.parseInt(row[2]), extra));
        }
        out.sort(Comparator.comparingDouble(Specific::points).reversed());
        return out;
    }

    /** Iron's Spellbooks' resistance soft cap (150 %, tending to 200 %). */
    static double ironsSoftCap(double x) {
        return x <= 1.75 ? x : 1 / (-16 * (x - 1.5)) + 2;
    }

    // ------------------------------------------------------------------ drawing

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !active() || !(mc.getCameraEntity() instanceof Player p)) {
            return;
        }
        List<Source> sources;
        List<Specific> specifics;
        double[] points;
        try {
            sources = sources(p);
            points = new double[sources.size()];
            double sum = 0;
            for (int i = 0; i < points.length; i++) {
                points[i] = sources.get(i).points();
                sum += Math.max(0, points[i]);
            }
            specifics = specifics(p, sum);
        } catch (RuntimeException e) {
            ImprovedArmor.LOGGER.debug("Improved Armor: HUD sources failed", e);
            return;
        }
        ArmorBar.Layout layout = ArmorBar.layout(points, ClientConfig.MAX_ROWS.get());

        gui.setupOverlayRenderState(true, false);
        RenderSystem.enableBlend();
        int left = width / 2 - 91;
        int bottomTop = height - gui.leftHeight;
        int glint = glintFrame();
        int[] owners = layout.owners();
        if (owners.length > 0) {
            for (int row = 0; row < layout.rows(); row++) {
                int y = bottomTop - row * 10;
                for (int icon = 0; icon < 10; icon++) {
                    int a = row * ArmorBar.POINTS_PER_ROW + icon * 2;
                    int x = left + icon * 8;
                    if (a >= owners.length) {
                        // Empty icons only on the first row, as vanilla.
                        if (row == 0) g.blit(VANILLA_ICONS, x, y, 16, 9, 9, 9);
                        continue;
                    }
                    Source first = sources.get(owners[a]);
                    boolean half = a + 1 >= owners.length;
                    if (half && first.kind() == Kind.ARMOR) g.blit(VANILLA_ICONS, x, y, 16, 9, 9, 9);
                    drawPart(g, first, x, y, 0, 5, glint);
                    if (!half) drawPart(g, sources.get(owners[a + 1]), x + 5, y, 5, 4, glint);
                }
            }
            if (layout.compact() || ClientConfig.SHOW_NUMBER.get()) {
                int top = bottomTop - (layout.rows() - 1) * 10;
                String text = layout.compact() ? "x" + layout.total() : String.valueOf(layout.total());
                g.drawString(mc.font, text, left + 82, top + 1, 0xFFFFFF, true);
            }
        }
        int used = 10 * layout.rows();

        // Specific protections: rows of 4 above the bar, highest first.
        if (!specifics.isEmpty()) {
            int barTop = bottomTop - (layout.rows() - 1) * 10;
            for (int i = 0; i < specifics.size(); i++) {
                Specific s = specifics.get(i);
                int x = left + (i % 4) * 21;
                int y = barTop - 9 * (i / 4 + 1);
                g.blit(ICONS, x, y, s.u(), s.v(), 8, 8, TEX, TEX);
                g.pose().pushPose();
                g.pose().translate(x + 9, y + 2, 0);
                g.pose().scale(.75f, .75f, 1f);
                g.drawString(mc.font, "+" + Math.round(s.points()), 0, 0, 0xFFFFFF, true);
                g.pose().popPose();
            }
            used += 9 * ((specifics.size() + 3) / 4);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gui.leftHeight += used;
    }

    /**
     * Draws columns [{@code from}, {@code from + w}) of the icon of a source at x (already offset by {@code from}).
     * Tinted layers take the source color; rims, glass and pages keep theirs; books get the moving shine.
     */
    private static void drawPart(GuiGraphics g, Source s, int x, int y, int from, int w, int glint) {
        switch (s.kind()) {
            case ARMOR -> tinted(g, VANILLA_ICONS, 256, s.color(), x, y, 34 + from, 9, w);
            case AMULET -> {
                tinted(g, ICONS, TEX, s.color(), x, y, from, 0, w);
                g.blit(ICONS, x, y, 9 + from, 0, w, 9, TEX, TEX);
            }
            case POTION -> {
                tinted(g, ICONS, TEX, s.color(), x, y, 18 + from, 0, w);
                g.blit(ICONS, x, y, 27 + from, 0, w, 9, TEX, TEX);
            }
            case BOOK -> {
                tinted(g, ICONS, TEX, s.color(), x, y, 36 + from, 0, w);
                g.blit(ICONS, x, y, 45 + from, 0, w, 9, TEX, TEX);
                if (glint >= 0) g.blit(ICONS, x, y, glint * 9 + from, 9, w, 9, TEX, TEX);
            }
        }
    }

    /** Frame of the book shine (0-6), or -1 during the pause between sweeps. */
    private static int glintFrame() {
        long t = (System.currentTimeMillis() / 70) % 22;
        return t < 7 ? (int) t : -1;
    }

    private static void tinted(GuiGraphics g, ResourceLocation tex, int size, int rgb, int x, int y, int u, int v, int w) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        g.blit(tex, x, y, u, v, w, 9, size, size);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
