package io.github.geopoliticalwar.blockentity;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.economy.PropertyStatusSnapshot;
import io.github.geopoliticalwar.menu.EstateBoardMenu;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import io.github.geopoliticalwar.war.WarService;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Optional;
import java.util.UUID;

/** Durable references for non-inventory strategic blocks. */
public final class StrategicLinkBlockEntity extends BlockEntity implements MenuProvider {
    private UUID claim;
    private UUID capturePoint;
    private UUID war;
    private UUID nation;
    private long progressSeconds;
    private long lastTickMillis;

    public StrategicLinkBlockEntity(BlockPos position, BlockState state) {
        super(ModContent.STRATEGIC_LINK_BLOCK_ENTITY.get(), position, state);
    }

    public Optional<UUID> claim() {
        return Optional.ofNullable(claim);
    }

    public Optional<UUID> capturePoint() {
        return Optional.ofNullable(capturePoint);
    }

    public Optional<UUID> war() {
        return Optional.ofNullable(war);
    }

    public Optional<UUID> nation() {
        return Optional.ofNullable(nation);
    }

    public void linkCapturePoint(UUID point) {
        capturePoint = point;
        setChanged();
    }

    public void linkClaim(UUID value) {
        claim = value;
        setChanged();
    }

    public void linkWar(UUID value) {
        war = value;
        setChanged();
    }

    public long progressSeconds() {
        return progressSeconds;
    }

    public boolean tryLinkSiege(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (claim != null && war != null) {
            return true;
        }
        var claims = ClaimsSavedData.get(serverLevel);
        var ids = claims.containing(
                new io.github.geopoliticalwar.domain.claim.DimensionId(level.dimension().location().toString()),
                getBlockPos().getX(),
                getBlockPos().getY(),
                getBlockPos().getZ()
        );
        NationId playerNation = NationMembershipService.nationFor(serverLevel, player.getUUID())
                .orElse(null);
        if (playerNation == null) {
            return false;
        }
        for (var id : ids) {
            Claim target = claims.get(id).orElse(null);
            if (target == null) {
                continue;
            }
            var activeWar = WarService.activeForClaim(serverLevel, target.id()).orElse(null);
            if (activeWar == null || !activeWar.includes(playerNation)) {
                continue;
            }
            boolean attackingGoal = activeWar.goals().stream().anyMatch(goal ->
                    goal.claim().filter(target.id()::equals).isPresent()
                            && goal.beneficiary().equals(playerNation)
                            && activeWar.sideOf(playerNation).map(side -> side == io.github.geopoliticalwar.domain.war.WarSide.ATTACKER).orElse(false)
                            && !goal.achieved()
            );
            if (attackingGoal) {
                claim = target.id().value();
                war = activeWar.id().value();
                progressSeconds = 0L;
                lastTickMillis = System.currentTimeMillis();
                setChanged();
                player.sendSystemMessage(Component.literal("Siege anchor linked to " + target.name() + "."));
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, StrategicLinkBlockEntity link) {
        if (!(level instanceof ServerLevel serverLevel) || !state.is(ModContent.SIEGE_ANCHOR.get())
                || link.claim == null || link.war == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = link.lastTickMillis == 0L ? now : link.lastTickMillis;
        long elapsed = Math.max(0L, Math.min(5L, (now - previous) / 1_000L));
        if (elapsed == 0L) {
            return;
        }
        WarId warId = new WarId(link.war);
        ClaimId claimId = new ClaimId(link.claim);
        var activeWar = WarsSavedData.get(serverLevel).get(warId).orElse(null);
        Claim target = ClaimsSavedData.get(serverLevel).get(claimId).orElse(null);
        if (activeWar == null || !activeWar.isActive() || target == null) {
            link.progressSeconds = 0L;
            link.lastTickMillis = now;
            link.setChanged();
            return;
        }
        NationId beneficiary = activeWar.goals().stream()
                .filter(goal -> goal.claim().filter(claimId::equals).isPresent() && !goal.achieved())
                .map(goal -> goal.beneficiary())
                .findFirst()
                .orElse(null);
        if (beneficiary == null) {
            link.progressSeconds = 0L;
            link.lastTickMillis = now;
            link.setChanged();
            return;
        }
        java.util.Optional<NationId> targetNation = nationOwner(target);
        boolean attackerPresent = false;
        boolean defenderPresent = false;
        boolean neutralPresent = false;
        int radius = ServerConfig.CAPTURE_RADIUS_BLOCKS.get();
        for (ServerPlayer player : serverLevel.players()) {
            double dx = player.getX() - (position.getX() + 0.5D);
            double dy = player.getY() - (position.getY() + 0.5D);
            double dz = player.getZ() - (position.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz > (double) radius * radius) {
                continue;
            }
            if (player.isSpectator()) {
                continue;
            }
            java.util.Optional<NationId> nation = NationMembershipService.nationFor(serverLevel, player.getUUID());
            if (nation.isEmpty() || activeWar.sideOf(nation.get()).isEmpty()) {
                neutralPresent = true;
            } else if (activeWar.sideOf(nation.get()).orElseThrow()
                    == io.github.geopoliticalwar.domain.war.WarSide.ATTACKER) {
                if (nation.get().equals(beneficiary)) {
                    attackerPresent = true;
                }
            } else if (activeWar.sideOf(nation.get()).orElseThrow()
                    == io.github.geopoliticalwar.domain.war.WarSide.DEFENDER) {
                defenderPresent = true;
            }
        }
        if (!attackerPresent || defenderPresent || neutralPresent) {
            link.progressSeconds = 0L;
        } else {
            long updatedProgress = link.progressSeconds > Long.MAX_VALUE - elapsed
                    ? Long.MAX_VALUE
                    : link.progressSeconds + elapsed;
            link.progressSeconds = Math.min(ServerConfig.SIEGE_HOLD_SECONDS.get(), updatedProgress);
            if (link.progressSeconds >= ServerConfig.SIEGE_HOLD_SECONDS.get()) {
                try {
                    WarService.markClaimConquered(serverLevel, warId, claimId, beneficiary);
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("Siege anchor completed the conquest of " + target.name() + "."),
                            false
                    );
                    link.progressSeconds = 0L;
                } catch (RuntimeException failure) {
                    GeopoliticalWarfare.LOGGER.warn("Siege anchor could not complete conquest", failure);
                    link.progressSeconds = 0L;
                }
            }
        }
        link.lastTickMillis = now;
        link.setChanged();
    }

    private static java.util.Optional<NationId> nationOwner(Claim claim) {
        String value = claim.owner().value();
        if (!value.startsWith("nation:")) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new NationId(java.util.UUID.fromString(value.substring("nation:".length()))));
        } catch (IllegalArgumentException invalid) {
            return java.util.Optional.empty();
        }
    }

    public void linkNation(UUID value) {
        nation = value;
        setChanged();
    }

    public String linkSummary() {
        if (claim != null) {
            return "claim=" + claim;
        }
        if (war != null) {
            return "war=" + war;
        }
        if (nation != null) {
            return "nation=" + nation;
        }
        return "unlinked";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.geopoliticalwar.estate_board");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (level instanceof ServerLevel serverLevel
                && getBlockState().is(ModContent.ESTATE_BOARD.get())) {
            return new EstateBoardMenu(id, inventory, PropertyStatusSnapshot.at(serverLevel, getBlockPos()), getBlockPos());
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (claim != null) {
            tag.putUUID("claim", claim);
        }
        if (capturePoint != null) {
            tag.putUUID("capture_point", capturePoint);
        }
        if (war != null) {
            tag.putUUID("war", war);
        }
        if (nation != null) {
            tag.putUUID("nation", nation);
        }
        tag.putLong("progress_seconds", progressSeconds);
        tag.putLong("last_tick", lastTickMillis);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        claim = tag.hasUUID("claim") ? tag.getUUID("claim") : null;
        capturePoint = tag.hasUUID("capture_point") ? tag.getUUID("capture_point") : null;
        war = tag.hasUUID("war") ? tag.getUUID("war") : null;
        nation = tag.hasUUID("nation") ? tag.getUUID("nation") : null;
        progressSeconds = tag.getLong("progress_seconds");
        lastTickMillis = tag.getLong("last_tick");
    }
}
