package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GovernanceEvents {
    private GovernanceEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            io.github.geopoliticalwar.persistence.NationMembershipSavedData.get(event.getServer().overworld())
                    .expireRequests(System.currentTimeMillis());
            ElectionService.tick(event.getServer());
        }
    }
}
