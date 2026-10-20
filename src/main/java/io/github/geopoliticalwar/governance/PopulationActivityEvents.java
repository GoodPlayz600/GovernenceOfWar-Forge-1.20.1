package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.persistence.PopulationActivitySavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Records online activity for population-sensitive defense and capture policy. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PopulationActivityEvents {
    private static int tickCounter;

    private PopulationActivityEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PopulationActivitySavedData.get(player.serverLevel()).touch(player.getUUID(), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter < 100) {
            return;
        }
        tickCounter = 0;
        long now = System.currentTimeMillis();
        PopulationActivitySavedData data = PopulationActivitySavedData.get(event.getServer().overworld());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            data.touch(player.getUUID(), now);
        }
    }
}
