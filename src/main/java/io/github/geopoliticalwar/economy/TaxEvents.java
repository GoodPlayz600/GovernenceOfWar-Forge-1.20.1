package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaxEvents {
    private TaxEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval;
        try {
            interval = Math.multiplyExact(ServerConfig.TAX_COLLECTION_INTERVAL_SECONDS.get(), 1_000L);
        } catch (ArithmeticException overflow) {
            GeopoliticalWarfare.LOGGER.error("Tax collection interval is too large", overflow);
            return;
        }
        TaxSavedData taxes = TaxSavedData.get(event.getServer().overworld());
        if (taxes.nextCollectionAtMillis() == 0L || now >= taxes.nextCollectionAtMillis()) {
            TaxService.collect(event.getServer());
            taxes.setNextCollectionAtMillis(now > Long.MAX_VALUE - interval ? Long.MAX_VALUE : now + interval);
        }
    }
}
