package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Enforces the narrow gameplay restrictions of an active captive record. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptiveEvents {
    private static int tickCounter;
    private CaptiveEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++tickCounter >= 20) {
            tickCounter = 0;
            CaptiveService.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                || !CaptiveService.isActive(player.serverLevel(), player.getUUID())) {
            return;
        }
        if (event.getState().is(BlockTags.LOGS)
                || event.getState().is(BlockTags.LEAVES)
                || event.getState().is(BlockTags.CROPS)
                || event.getState().is(BlockTags.SAPLINGS)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Captives may not farm or cut down trees."
            ));
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !CaptiveService.isActive(player.serverLevel(), player.getUUID())) {
            return;
        }
        if (event.getPlacedBlock().is(BlockTags.LOGS)
                || event.getPlacedBlock().is(BlockTags.CROPS)
                || event.getPlacedBlock().is(BlockTags.SAPLINGS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !CaptiveService.isActive(level, player.getUUID())) {
            return;
        }
        if (event.getLevel().getBlockState(event.getPos()).is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }
}
