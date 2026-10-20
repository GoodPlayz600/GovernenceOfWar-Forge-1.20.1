package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InflationEvents {
    private InflationEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                InflationService.tick(event.getServer(), System.currentTimeMillis());
            } catch (ArithmeticException overflow) {
                GeopoliticalWarfare.LOGGER.error("Inflation interval is too large", overflow);
            }
        }
    }
}
