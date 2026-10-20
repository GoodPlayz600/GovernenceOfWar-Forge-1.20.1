package io.github.geopoliticalwar;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import io.github.geopoliticalwar.compat.war.WarModAdapters;
import io.github.geopoliticalwar.registry.ModContent;
import io.github.geopoliticalwar.governance.PopulationService;
import org.slf4j.Logger;

/**
 * Common entry point for Geopolitical Warfare.
 *
 * <p>The base mod intentionally has no hard dependency on a warfare or
 * minimap mod. Optional integrations are detected at runtime and will be
 * registered in their own side-safe modules as those systems are added.</p>
 */
@Mod(GeopoliticalWarfare.MOD_ID)
public final class GeopoliticalWarfare {
    public static final String MOD_ID = "geopoliticalwar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GeopoliticalWarfare(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::commonSetup);
        ModContent.register(context.getModEventBus());
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(
                ModConfig.Type.SERVER,
                ServerConfig.SPEC,
                "conquest_config.toml"
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info(
                    "{} {} initialized for Minecraft {}",
                    ServerConfig.MOD_NAME,
                    ServerConfig.MOD_VERSION,
                    ServerConfig.MINECRAFT_VERSION
            );
            WarModAdapters.initialize();
        });
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        PopulationService.reconcileAll(event.getServer().overworld());
        LOGGER.info(
                "Geopolitical Warfare server systems ready; optional integrations: Superb Warfare={}, Xaero's Minimap={}",
                ModList.get().isLoaded("superbwarfare"),
                ModList.get().isLoaded("xaerominimap")
        );
    }
}
