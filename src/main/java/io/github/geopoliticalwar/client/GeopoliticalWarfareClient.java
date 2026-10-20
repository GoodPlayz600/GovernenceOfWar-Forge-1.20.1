package io.github.geopoliticalwar.client;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.RegisterMenuScreensEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class GeopoliticalWarfareClient {
    private GeopoliticalWarfareClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.MINTING_PRESS_MENU.get(), MintingPressScreen::new);
        event.register(ModContent.ESTATE_BOARD_MENU.get(), EstateBoardScreen::new);
    }
}
