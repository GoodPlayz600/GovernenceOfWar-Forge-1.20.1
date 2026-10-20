package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.CaptiveRecord;
import io.github.geopoliticalwar.domain.war.CaptiveState;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.persistence.CaptivesSavedData;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-authoritative fictional captive, labor, liberation, and rebellion mechanics. */
public final class CaptiveService {
    private CaptiveService() {
    }

    public static CaptiveRecord capture(
            ServerLevel level,
            ServerPlayer actor,
            ServerPlayer target,
            ClaimId claimId,
            long now
    ) {
        if (!ServerConfig.CAPTIVE_FORCED_LABOR_ENABLED.get()) {
            throw new IllegalArgumentException("Forced-labor mechanics are disabled by server configuration");
        }
        if (actor.getUUID().equals(target.getUUID())) {
            throw new IllegalArgumentException("You cannot capture yourself");
        }
        if (target.serverLevel() != level
                || !actor.serverLevel().equals(level)
                || !level.dimension().equals(target.serverLevel().dimension())) {
            throw new IllegalArgumentException("The captor and target must be in the same dimension");
        }
        Claim claim = ClaimsSavedData.get(level).get(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown claim"));
        if (!claim.dimension().value().equals(level.dimension().location().toString())) {
            throw new IllegalArgumentException("The occupied claim is in another dimension");
        }
        if (!claim.volume().contains(target.blockPosition().getX(), target.blockPosition().getY(), target.blockPosition().getZ())) {
            throw new IllegalArgumentException("The target must be inside the occupied claim");
        }
        OccupationRecord occupation = activeOccupation(level, claimId);
        NationId holding = occupation.occupyingNation();
        if (!actor.hasPermissions(2)
                && !NationMembershipService.mayHoldOffice(level, actor.getUUID(), holding)) {
            throw new IllegalArgumentException("Only the occupying nation may capture people here");
        }
        if (!occupation.formerPopulation().contains(target.getUUID())) {
            throw new IllegalArgumentException("Only people belonging to the occupied population can be captured");
        }
        CaptivesSavedData data = CaptivesSavedData.get(level);
        long activeAtClaim = data.all().values().stream()
                .filter(value -> value.active() && value.claim().equals(claimId))
                .count();
        if (activeAtClaim >= ServerConfig.CAPTIVE_MAX_PER_CLAIM.get()) {
            throw new IllegalArgumentException("This claim has reached its captive capacity");
        }
        if (data.active(target.getUUID()).isPresent()) {
            throw new IllegalArgumentException("That person is already captive");
        }
        NationId former = occupation.formerPopulation().contains(target.getUUID())
                ? occupationFormerNation(claim)
                : holding;
        CaptiveRecord record = CaptiveRecord.active(target.getUUID(), claimId, holding, former, now);
        data.set(record);
        target.sendSystemMessage(Component.literal("You have been captured and assigned compulsory civic labor."));
        actor.sendSystemMessage(Component.literal("Captured " + target.getGameProfile().getName() + "."));
        return record;
    }

    public static CaptiveRecord liberate(ServerLevel level, UUID player, UUID actor, long now) {
        CaptivesSavedData data = CaptivesSavedData.get(level);
        CaptiveRecord record = data.active(player)
                .orElseThrow(() -> new IllegalArgumentException("That person is not an active captive"));
        if (!canLiberate(level, record, actor)) {
            throw new IllegalArgumentException("Only another nation, the former civilians, or an operator may liberate this captive");
        }
        CaptiveRecord updated = record.transition(CaptiveState.LIBERATED, now);
        data.set(updated);
        notifyPlayer(level, player, "You have been liberated.");
        return updated;
    }

    public static CaptiveRecord rebel(ServerLevel level, UUID player, UUID actor, long now) {
        CaptivesSavedData data = CaptivesSavedData.get(level);
        CaptiveRecord record = data.active(player)
                .orElseThrow(() -> new IllegalArgumentException("That person is not an active captive"));
        if (!actor.equals(player) && !isFormerCivilian(level, record, actor)) {
            throw new IllegalArgumentException("Only the captive or former civilians may organize a captive rebellion");
        }
        CaptiveRecord updated = record.transition(CaptiveState.REBELLED, now);
        data.set(updated);
        NationsSavedData.get(level).get(record.formerNation()).ifPresent(nation ->
                RebellionService.addPressure(level, nation, actor, ServerConfig.CAPTIVE_REBELLION_MOMENTUM.get(), now)
        );
        notifyPlayer(level, player, "You rebelled and escaped compulsory labor.");
        return updated;
    }

    public static boolean isActive(ServerLevel level, UUID player) {
        return CaptivesSavedData.get(level).active(player).isPresent();
    }

    public static boolean isActiveAt(ServerLevel level, UUID player, ClaimId claim) {
        return CaptivesSavedData.get(level).active(player)
                .filter(record -> record.claim().equals(claim))
                .isPresent();
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CaptivesSavedData data = CaptivesSavedData.get(level);
        long now = System.currentTimeMillis();
        for (CaptiveRecord record : data.all().values()) {
            if (!record.active()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(record.player());
            if (player == null) {
                continue;
            }
            Claim claim = ClaimsSavedData.get(level).get(record.claim()).orElse(null);
            OccupationRecord occupation = claim == null ? null : OccupationsSavedData.get(level).get(record.claim())
                    .filter(value -> value.settledAtMillis().isEmpty()
                            && value.occupyingNation().equals(record.holdingNation()))
                    .orElse(null);
            if (claim == null || occupation == null) {
                data.set(record.transition(CaptiveState.LIBERATED, now));
                notifyPlayer(level, record.player(), "Your captive order ended because the occupation ended.");
                continue;
            }
            long last = record.lastWorkAtMillis() == 0L ? now : record.lastWorkAtMillis();
            long interval = ServerConfig.CAPTIVE_WORK_INTERVAL_SECONDS.get();
            long elapsed = Math.max(0L, (now - last) / 1_000L);
            if (elapsed < interval) {
                continue;
            }
            boolean atWork = player.serverLevel().dimension().location().toString().equals(claim.dimension().value())
                    && claim.volume().contains(
                    player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ()
            );
            int unrest = atWork ? 0 : ServerConfig.CAPTIVE_UNREST_PER_INTERVAL.get();
            CaptiveRecord updated = record.worked(interval, unrest, now);
            if (atWork) {
                io.github.geopoliticalwar.economy.LaborSiteService.produceForCaptive(level, record.claim(), now);
            }
            if (updated.unrest() >= ServerConfig.CAPTIVE_UNREST_LIMIT.get()) {
                data.set(updated.transition(CaptiveState.REBELLED, now));
                NationsSavedData.get(level).get(record.formerNation()).ifPresent(nation ->
                        RebellionService.addPressure(level, nation, record.player(),
                                ServerConfig.CAPTIVE_REBELLION_MOMENTUM.get(), now)
                );
                notifyPlayer(level, record.player(), "Your unrest triggered a captive rebellion.");
            } else {
                data.set(updated);
                if (!atWork) {
                    player.displayClientMessage(Component.literal("Report to the occupied claim to perform compulsory labor."), true);
                }
            }
        }
    }

    private static boolean canLiberate(ServerLevel level, CaptiveRecord record, UUID actor) {
        if (level.getServer().getPlayerList().getPlayer(actor) != null
                && level.getServer().getPlayerList().getPlayer(actor).hasPermissions(2)) {
            return true;
        }
        if (isFormerCivilian(level, record, actor)) {
            return true;
        }
        return NationMembershipService.nationFor(level, actor)
                .filter(nation -> !nation.equals(record.holdingNation()))
                .isPresent();
    }

    private static boolean isFormerCivilian(ServerLevel level, CaptiveRecord record, UUID actor) {
        OccupationRecord occupation = OccupationsSavedData.get(level).get(record.claim()).orElse(null);
        return occupation != null && occupation.formerPopulation().contains(actor);
    }

    private static OccupationRecord activeOccupation(ServerLevel level, ClaimId claim) {
        return OccupationsSavedData.get(level).get(claim)
                .filter(value -> value.settledAtMillis().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("The claim is not under active occupation"));
    }

    private static NationId occupationFormerNation(Claim claim) {
        String owner = claim.owner().value();
        if (!owner.startsWith("nation:")) {
            throw new IllegalArgumentException("The occupied claim has no former nation");
        }
        return new NationId(UUID.fromString(owner.substring("nation:".length())));
    }

    private static void notifyPlayer(ServerLevel level, UUID player, String message) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        if (target != null) {
            target.sendSystemMessage(Component.literal(message));
        }
    }
}
