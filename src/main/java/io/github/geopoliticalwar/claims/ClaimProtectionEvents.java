package io.github.geopoliticalwar.claims;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.war.WarDestructionService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Initial peace-state protection hooks for vanilla player and explosion paths. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimProtectionEvents {
    private ClaimProtectionEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (!ClaimAccessService.canBreak(player, (net.minecraft.server.level.ServerLevel) level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }

        boolean strategicSiegePlacement = event.getPlacedBlock().is(io.github.geopoliticalwar.registry.ModContent.SIEGE_ANCHOR.get())
                && ClaimAccessService.canPlaceSiegeAnchor(player, level, event.getPos());
        if (!ClaimAccessService.canPlace(player, level, event.getPos()) && !strategicSiegePlacement) {
            event.setCanceled(true);
            return;
        }

        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace
                && multiPlace.getReplacedBlockSnapshots().stream().anyMatch(snapshot ->
                !ClaimAccessService.canPlace(player, level, snapshot.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (!ClaimAccessService.canInteract(player, level, event.getPos())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        Entity direct = event.getExplosion().getDirectSourceEntity();
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        if (isOperator(direct) || isOperator(indirect)) {
            return;
        }

        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (io.github.geopoliticalwar.war.WarStateService.isWar(level)
                || io.github.geopoliticalwar.war.WarStateService.isRebellion(level)) {
            java.util.Optional<net.minecraft.server.level.ServerPlayer> source = explosionPlayer(direct, indirect);
            affectedBlocks.removeIf(position -> !WarDestructionService.mayExplosionDamage(level, position, source));
            return;
        }
        affectedBlocks.removeIf(position -> ClaimAccessService.isProtected(level, position));
    }

    private static boolean isOperator(Entity entity) {
        return entity instanceof net.minecraft.server.level.ServerPlayer player
                && player.hasPermissions(2);
    }

    private static java.util.Optional<net.minecraft.server.level.ServerPlayer> explosionPlayer(Entity direct, Entity indirect) {
        if (direct instanceof net.minecraft.server.level.ServerPlayer player) {
            return java.util.Optional.of(player);
        }
        if (indirect instanceof net.minecraft.server.level.ServerPlayer player) {
            return java.util.Optional.of(player);
        }
        return java.util.Optional.empty();
    }
}
