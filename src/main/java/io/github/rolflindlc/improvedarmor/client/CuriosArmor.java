package io.github.rolflindlc.improvedarmor.client;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Armor given by accessories in Curios slots. Only loaded when Curios is installed (see {@link ArmorHud}), so the mod
 * does not need Curios at runtime.
 */
final class CuriosArmor {
    record Piece(ItemStack stack, double armor) {
    }

    private CuriosArmor() {
    }

    static List<Piece> pieces(Player p) {
        List<Piece> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(p).ifPresent(inv -> inv.getCurios().forEach((id, handler) -> {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                SlotContext ctx = new SlotContext(id, p, i, false, true);
                UUID uuid = UUID.nameUUIDFromBytes((id + ":" + i).getBytes(StandardCharsets.UTF_8));
                double armor = 0;
                for (AttributeModifier m : CuriosApi.getAttributeModifiers(ctx, uuid, stack).get(Attributes.ARMOR)) {
                    if (m.getOperation() == AttributeModifier.Operation.ADDITION) armor += m.getAmount();
                }
                if (armor > 0) out.add(new Piece(stack, armor));
            }
        }));
        return out;
    }
}
