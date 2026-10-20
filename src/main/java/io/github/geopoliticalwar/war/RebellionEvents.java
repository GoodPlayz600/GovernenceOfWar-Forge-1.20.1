package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RebellionEvents {
    private static int tickCounter;

    private RebellionEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            RebellionService.tick(event.getServer());
        }
    }
}
