package io.github.rolflindlc.improvedarmor;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Mod entry point. Registration of config, events and the HUD is added by later milestones. */
@Mod(ImprovedArmor.MODID)
public class ImprovedArmor {
    public static final String MODID = "improvedarmor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImprovedArmor() {
        LOGGER.info("Improved Armor loaded");
    }
}
