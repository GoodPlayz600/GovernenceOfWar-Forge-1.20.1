package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.war.CaptureDifficultyPolicy;
import io.github.geopoliticalwar.domain.war.CapturePoint;
import io.github.geopoliticalwar.domain.war.CapturePresence;
import io.github.geopoliticalwar.domain.war.CaptureProgressPolicy;
import io.github.geopoliticalwar.domain.war.WarSide;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.governance.PopulationService;
import io.github.geopoliticalwar.persistence.CapturePointsSavedData;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Occupy-and-hold capture processing for physical capture zones. */
public final class CaptureService {
    private CaptureService() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        CapturePointsSavedData data = CapturePointsSavedData.get(overworld);
        long now = System.currentTimeMillis();
        for (CapturePoint point : data.all().values()) {
            ServerLevel level = levelFor(server, point);
            if (level == null) {
                continue;
            }

            long lastTick = point.lastTickMillis() == 0L ? now : point.lastTickMillis();
            if (now < lastTick) {
                data.replace(point.withProgress(point.progressSeconds(), now));
                continue;
            }
            long elapsedSeconds = Math.max(0L, Math.min(5L, (now - lastTick) / 1_000L));
            var activeWar = WarService.activeForCapturePoint(server.overworld(), point.id());
            if (activeWar.isEmpty()) {
                data.replace(point.withProgress(
                        CaptureProgressPolicy.decay(point.progressSeconds(), elapsedSeconds, ServerConfig.CAPTURE_DECAY_PER_SECOND.get()),
                        now
                ));
                continue;
            }
            var beneficiary = activeWar.get().goals().stream()
                    .filter(goal -> goal.capturePoint().filter(point.id()::equals).isPresent() && !goal.achieved())
                    .map(goal -> goal.beneficiary())
                    .findFirst();
            if (elapsedSeconds == 0L) {
                if (point.lastTickMillis() == 0L) {
                    data.replace(point.withProgress(point.progressSeconds(), now));
                }
                continue;
            }

            Set<NationId> attackers = new HashSet<>();
            Set<NationId> defenders = new HashSet<>();
            int neutralPlayers = 0;
            boolean beneficiaryPresent = false;
            int activeDefenderPlayers = 0;
            int activeAttackerPlayers = 0;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !point.contains(player.getX(), player.getY(), player.getZ())) {
                    continue;
                }
                Optional<NationId> nation = NationMembershipService.nationFor(overworld, player.getUUID());
                if (nation.isEmpty() || activeWar.get().sideOf(nation.get()).isEmpty()) {
                    neutralPlayers++;
                    continue;
                }
                if (activeWar.get().sideOf(nation.get()).orElseThrow() == WarSide.ATTACKER) {
                    attackers.add(nation.get());
                    activeAttackerPlayers++;
                    if (beneficiary.filter(nation.get()::equals).isPresent()) {
                        beneficiaryPresent = true;
                    }
                } else {
                    defenders.add(nation.get());
                    activeDefenderPlayers++;
                }
            }

            CapturePresence presence = new CapturePresence(attackers, defenders, neutralPlayers, beneficiaryPresent);
            long baseRequired = beneficiary.map(value -> activeWar.get().goals().stream()
                    .filter(goal -> goal.capturePoint().filter(point.id()::equals).isPresent())
                    .mapToLong(goal -> goal.requiredScore())
                    .findFirst().orElse(ServerConfig.CAPTURE_HOLD_SECONDS.get()))
                    .orElse(ServerConfig.CAPTURE_HOLD_SECONDS.get());
            Claim localClaim = ClaimsSavedData.get(overworld).containing(
                            new DimensionId(point.dimension().value()),
                            point.center().x(), point.center().y(), point.center().z()
                    ).stream()
                    .map(ClaimsSavedData.get(overworld)::get)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(null);
            long defenderPopulation = point.owner()
                    .map(owner -> PopulationService.activePopulation(overworld, owner, now))
                    .orElse(0L);
            long claimVolume = localClaim == null ? 0L : localClaim.volume().volume();
            long required = CaptureDifficultyPolicy.requiredSeconds(
                    baseRequired, claimVolume, defenderPopulation, activeDefenderPlayers, activeAttackerPlayers
            );
            long progress = presence.beneficiaryPresent() && !presence.contested()
                    ? CaptureProgressPolicy.advance(point.progressSeconds(), elapsedSeconds, required)
                    : CaptureProgressPolicy.decay(point.progressSeconds(), elapsedSeconds, ServerConfig.CAPTURE_DECAY_PER_SECOND.get());
            NationId attacker = beneficiary.orElse(null);
            if (attacker != null && progress >= required) {
                CapturePoint captured = point.capturedBy(attacker, now);
                try {
                    WarService.markCapturePointCaptured(overworld, point.id(), attacker);
                    data.replace(captured);
                } catch (RuntimeException invalidCapture) {
                    GeopoliticalWarfare.LOGGER.warn("Rejected capture point transition", invalidCapture);
                    data.replace(point.withProgress(0L, now));
                    continue;
                }
                level.players().forEach(player -> player.sendSystemMessage(
                        Component.literal("Capture point " + point.name() + " was captured by " + attacker.value())
                ));
            } else {
                data.replace(point.withProgress(progress, now));
            }
        }
    }

    private static ServerLevel levelFor(MinecraftServer server, CapturePoint point) {
        ResourceLocation location = ResourceLocation.tryParse(point.dimension().value());
        if (location == null) {
            GeopoliticalWarfare.LOGGER.warn("Invalid capture point dimension: {}", point.dimension().value());
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }
}
