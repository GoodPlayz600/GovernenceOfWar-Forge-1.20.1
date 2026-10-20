package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Schedules persisted proposal and government-policy work from server ticks. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PolicyEvents {
    private static int ticks;

    private PolicyEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) {
            return;
        }
        ticks = 0;
        GovernmentPolicyService.tick(event.getServer(), System.currentTimeMillis());
    }
}
