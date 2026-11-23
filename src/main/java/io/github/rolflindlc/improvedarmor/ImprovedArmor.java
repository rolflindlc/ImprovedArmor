package io.github.rolflindlc.improvedarmor;

import com.mojang.logging.LogUtils;
import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.client.ArmorHud;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/** Mod entry point. */
@Mod(ImprovedArmor.MODID)
public class ImprovedArmor {
    public static final String MODID = "improvedarmor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImprovedArmor() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(ServerConfig::onLoad);
        modBus.addListener(ServerConfig::onReload);

        // The damage maths only runs on the server, so clients without the mod may join; clients that
        // have it get the (optional) HUD. Accept any remote version in both directions.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ArmorHud.init(modBus);
        }

        LOGGER.info("Improved Armor loaded");
    }
}
