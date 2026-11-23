package io.github.rolflindlc.improvedarmor.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.rolflindlc.improvedarmor.ImprovedArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Colors of the HUD sources. Armor pieces get the average color of their item texture (so armor from any mod gets a
 * sensible color with no setup), dyed leather its dye; the config can override any id. Results are cached per item
 * and cleared when the config or the resource packs change.
 */
public final class ArmorColors {
    /** Points that come from the armor attribute but not from the worn pieces (class bonuses, effects, other mods). */
    public static final int OTHER = 0xFFFFFF;
    /** Fallback for enchantments without an override. */
    public static final int ENCHANTMENT = 0xB57CFF;

    private static final Map<Item, Integer> TEXTURE_CACHE = new HashMap<>();
    private static Map<ResourceLocation, Integer> overrides;
    private static Object overridesSource;

    private ArmorColors() {
    }

    public static void clear() {
        TEXTURE_CACHE.clear();
        overrides = null;
    }

    /** Override from the config for this id, if any. */
    public static Optional<Integer> override(ResourceLocation id) {
        Object source = ClientConfig.COLOR_OVERRIDES.get();
        if (overrides == null || overridesSource != source) {
            overridesSource = source;
            overrides = new HashMap<>();
            for (String entry : ClientConfig.COLOR_OVERRIDES.get()) {
                int at = entry.indexOf("=#");
                ResourceLocation key = at > 0 ? ResourceLocation.tryParse(entry.substring(0, at).trim()) : null;
                if (key == null) continue;
                try {
                    overrides.put(key, Integer.parseInt(entry.substring(at + 2).trim(), 16) & 0xFFFFFF);
                } catch (NumberFormatException e) {
                    ImprovedArmor.LOGGER.warn("Improved Armor: bad color override '{}'", entry);
                }
            }
        }
        return id == null ? Optional.empty() : Optional.ofNullable(overrides.get(id));
    }

    /** Color of a worn piece. */
    public static int piece(ItemStack stack) {
        Optional<Integer> o = override(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (o.isPresent()) return o.get();
        if (stack.getItem() instanceof DyeableLeatherItem dyed) return dyed.getColor(stack) & 0xFFFFFF;
        return TEXTURE_CACHE.computeIfAbsent(stack.getItem(), item -> averageTextureColor(stack));
    }

    /**
     * Average color of the item texture's opaque pixels, leaving out its border (opaque pixels next to a transparent one
     * or to the edge of the image), which is usually a dark outline that muddies the color. Thin textures with no inner
     * pixels fall back to every opaque pixel. Slightly brightened so dark sets stay visible.
     */
    private static int averageTextureColor(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        try {
            TextureAtlasSprite sprite = mc.getItemRenderer().getModel(stack, null, null, 0).getParticleIcon();
            ResourceLocation name = sprite.contents().name();
            ResourceLocation file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
            Optional<net.minecraft.server.packs.resources.Resource> res = mc.getResourceManager().getResource(file);
            if (res.isEmpty()) return OTHER;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth();
                int h = Math.min(img.getHeight(), w); // first frame of animated textures
                long[] inner = new long[4];
                long[] all = new long[4];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = img.getPixelRGBA(x, y); // NativeImage stores ABGR
                        if (!opaque(abgr)) continue;
                        add(all, abgr);
                        boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1
                                || !opaque(img.getPixelRGBA(x - 1, y)) || !opaque(img.getPixelRGBA(x + 1, y))
                                || !opaque(img.getPixelRGBA(x, y - 1)) || !opaque(img.getPixelRGBA(x, y + 1));
                        if (!border) add(inner, abgr);
                    }
                }
                long[] use = inner[3] > 0 ? inner : all;
                if (use[3] == 0) return OTHER;
                return brighten((int) (use[0] / use[3]), (int) (use[1] / use[3]), (int) (use[2] / use[3]));
            }
        } catch (Exception | LinkageError e) {
            ImprovedArmor.LOGGER.debug("Improved Armor: no texture color for {}", stack, e);
            return OTHER;
        }
    }

    private static boolean opaque(int abgr) {
        return (abgr >>> 24) >= 128;
    }

    private static void add(long[] sum, int abgr) {
        sum[0] += abgr & 0xFF;
        sum[1] += (abgr >> 8) & 0xFF;
        sum[2] += (abgr >> 16) & 0xFF;
        sum[3]++;
    }

    /** Scales the color so its brightest channel is at least 0xC0 (the icons are tinted, dark tints look black). */
    static int brighten(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        if (max > 0 && max < 0xC0) {
            double f = 0xC0 / (double) max;
            r = (int) Math.min(255, r * f);
            g = (int) Math.min(255, g * f);
            b = (int) Math.min(255, b * f);
        } else if (max == 0) {
            r = g = b = 0x60;
        }
        return (r << 16) | (g << 8) | b;
    }

    /** One shade per slot so pieces of the same set can be told apart: helmet lighter, boots darker. */
    public static int shade(int rgb, int slotFromTop) {
        double f = switch (slotFromTop) {
            case 0 -> 1.12;
            case 1 -> 1.0;
            case 2 -> 0.88;
            default -> 0.76;
        };
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
