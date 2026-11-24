package io.github.rolflindlc.improvedarmor.registry;

import io.github.rolflindlc.improvedarmor.ImprovedArmor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Attributes added by the mod.
 *
 * <p>{@code improvedarmor:armor_penetration} (0 to 1): fraction of the target's armor points the attacker ignores.
 * Protection and Resistance are not affected. Nothing has it by default; other mods, commands and the API give it.
 * It only matters on the server, so it is not synced to clients.
 */
public final class ModAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ImprovedArmor.MODID);

    public static final RegistryObject<Attribute> ARMOR_PENETRATION = ATTRIBUTES.register("armor_penetration",
            () -> new RangedAttribute("attribute.name.improvedarmor.armor_penetration", 0.0, 0.0, 1.0).setSyncable(false));

    private ModAttributes() {
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ModAttributes::addToEveryone);
    }

    /** Every living entity (players and mobs from any mod) can have armor penetration. */
    private static void addToEveryone(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (!event.has(type, ARMOR_PENETRATION.get())) event.add(type, ARMOR_PENETRATION.get());
        }
    }

    /** Armor penetration of an entity in [0, 1] (0 if it has none). */
    public static double penetration(LivingEntity entity) {
        if (entity == null || !ARMOR_PENETRATION.isPresent()) return 0.0;
        var inst = entity.getAttribute(ARMOR_PENETRATION.get());
        return inst == null ? 0.0 : Math.max(0.0, Math.min(1.0, inst.getValue()));
    }
}
