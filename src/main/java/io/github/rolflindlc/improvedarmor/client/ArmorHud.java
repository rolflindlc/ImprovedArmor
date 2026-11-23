package io.github.rolflindlc.improvedarmor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.rolflindlc.improvedarmor.ImprovedArmor;
import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.core.ArmorBar;
import io.github.rolflindlc.improvedarmor.core.ArmorParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Armor HUD (client only, optional). Replaces the vanilla armor bar with one where every point is tinted by where it
 * comes from, in this order:
 * <ol>
 *   <li>each worn piece (head, chest, legs, feet), with the color of its texture and one shade per slot;</li>
 *   <li>armor from anything else (class bonuses, other mods' attribute modifiers), white;</li>
 *   <li>each enchantment that protects against ordinary hits (Protection), one color per enchantment;</li>
 *   <li>each effect that adds armor in the flat curve (Resistance), with the effect's color.</li>
 * </ol>
 * The points of enchantments and effects use the same conversion as the server formula, so the bar shows the
 * effective armor against a normal hit. Up to {@code maxRows} rows of 20 points are stacked; past that the bar
 * collapses to one proportional row and the total is printed next to it.
 */
public final class ArmorHud {
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorHud() {
    }

    /** One colored part of the bar. */
    record Source(int color, double points) {
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

    // ------------------------------------------------------------------ sources

    static List<Source> sources(Player p) {
        ArmorParams params = ServerConfig.params();
        List<Source> out = new ArrayList<>();

        // 1. Worn pieces: their own armor modifiers (additions; multipliers end up in "other").
        double[] pieces = new double[SLOTS.length];
        double piecesSum = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = p.getItemBySlot(SLOTS[i]);
            if (stack.isEmpty()) continue;
            double v = 0;
            for (AttributeModifier m : stack.getAttributeModifiers(SLOTS[i]).get(Attributes.ARMOR)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) v += m.getAmount();
            }
            pieces[i] = Math.max(0, v);
            piecesSum += pieces[i];
        }
        double attribute = Math.max(0, p.getAttributeValue(Attributes.ARMOR));
        // If something lowers the attribute below what the pieces give, the pieces shrink to fit.
        double pieceScale = piecesSum > attribute && piecesSum > 0 ? attribute / piecesSum : 1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (pieces[i] <= 0) continue;
            out.add(new Source(ArmorColors.shade(ArmorColors.piece(p.getItemBySlot(SLOTS[i])), i), pieces[i] * pieceScale));
        }

        // 2. Armor from anything else.
        double other = attribute - piecesSum * pieceScale;
        if (other > 0.01) out.add(new Source(ArmorColors.OTHER, other));

        // 3. Enchantments that protect against an ordinary hit, grouped by enchantment, with the EPF cap.
        DamageSource hit = p.damageSources().generic();
        Map<Enchantment, Double> epf = new LinkedHashMap<>();
        double epfSum = 0;
        for (ItemStack stack : p.getArmorSlots()) {
            for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                int v = e.getKey().getDamageProtection(e.getValue(), hit);
                if (v <= 0) continue;
                epf.merge(e.getKey(), (double) v, Double::sum);
                epfSum += v;
            }
        }
        double epfScale = epfSum > params.maxEpf() && epfSum > 0 ? params.maxEpf() / epfSum : 1;
        for (Map.Entry<Enchantment, Double> e : epf.entrySet()) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e.getKey());
            int color = ArmorColors.override(id).orElse(ArmorColors.ENCHANTMENT);
            out.add(new Source(color, e.getValue() * epfScale * params.pointsPerEpf()));
        }

        // 4. Resistance.
        MobEffectInstance resistance = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) {
            int color = ArmorColors.override(ForgeRegistries.MOB_EFFECTS.getKey(MobEffects.DAMAGE_RESISTANCE))
                    .orElse(MobEffects.DAMAGE_RESISTANCE.getColor() & 0xFFFFFF);
            out.add(new Source(color, (resistance.getAmplifier() + 1) * params.pointsPerResistance()));
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !active() || !(mc.getCameraEntity() instanceof Player p)) {
            return;
        }
        List<Source> sources;
        try {
            sources = sources(p);
        } catch (RuntimeException e) {
            ImprovedArmor.LOGGER.debug("Improved Armor: HUD sources failed", e);
            return;
        }
        double[] points = new double[sources.size()];
        for (int i = 0; i < points.length; i++) points[i] = sources.get(i).points();
        ArmorBar.Layout layout = ArmorBar.layout(points, ClientConfig.MAX_ROWS.get());

        gui.setupOverlayRenderState(true, false);
        RenderSystem.enableBlend();
        int left = width / 2 - 91;
        int bottomTop = height - gui.leftHeight;
        int[] owners = layout.owners();
        if (owners.length > 0) {
            for (int row = 0; row < layout.rows(); row++) {
                int y = bottomTop - row * 10;
                for (int icon = 0; icon < 10; icon++) {
                    int a = row * ArmorBar.POINTS_PER_ROW + icon * 2;
                    int x = left + icon * 8;
                    if (a >= owners.length) {
                        // Empty icons only on the first row, as vanilla.
                        if (row == 0) g.blit(ICONS, x, y, 16, 9, 9, 9);
                        continue;
                    }
                    if (a + 1 >= owners.length) g.blit(ICONS, x, y, 16, 9, 9, 9);
                    tinted(g, sources.get(owners[a]).color(), x, y, 34, 5);
                    if (a + 1 < owners.length) tinted(g, sources.get(owners[a + 1]).color(), x + 5, y, 39, 4);
                }
            }
            if (layout.compact() || ClientConfig.SHOW_NUMBER.get()) {
                int top = bottomTop - (layout.rows() - 1) * 10;
                String text = layout.compact() ? "x" + layout.total() : String.valueOf(layout.total());
                g.drawString(mc.font, text, left + 82, top + 1, 0xFFFFFF, true);
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gui.leftHeight += 10 * layout.rows();
    }

    /** Part of the full armor icon (u, width) tinted with the source color. */
    private static void tinted(GuiGraphics g, int rgb, int x, int y, int u, int w) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        g.blit(ICONS, x, y, u, 9, w, 9);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
