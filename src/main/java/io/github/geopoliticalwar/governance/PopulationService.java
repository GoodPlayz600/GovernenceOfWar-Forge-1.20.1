package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.NationMembership;
import io.github.geopoliticalwar.persistence.NationMembershipSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.PopulationSavedData;
import io.github.geopoliticalwar.persistence.PopulationActivitySavedData;
import io.github.geopoliticalwar.ServerConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Reconciles the effective population of a nation from authoritative membership stores. */
public final class PopulationService {
    private PopulationService() {
    }

    public static long population(ServerLevel level, NationId nation) {
        reconcile(level, nation);
        return PopulationSavedData.get(level).population(nation);
    }

    public static boolean affiliated(ServerLevel level, NationId nation, UUID player) {
        return members(level, nation).contains(player);
    }

    public static long reconcile(ServerLevel level, NationId nation) {
        Set<UUID> members = members(level, nation);
        PopulationSavedData data = PopulationSavedData.get(level);
        data.setPopulation(nation, members.size());
        return members.size();
    }

    public static long activePopulation(ServerLevel level, NationId nation, long now) {
        long seconds = ServerConfig.ACTIVE_POPULATION_WINDOW_SECONDS.get();
        long window = seconds > Long.MAX_VALUE / 1_000L ? Long.MAX_VALUE : seconds * 1_000L;
        long cutoff = now < window ? 0L : now - window;
        PopulationActivitySavedData activity = PopulationActivitySavedData.get(level);
        return members(level, nation).stream()
                .filter(player -> activity.lastSeen(player) >= cutoff)
                .count();
    }

    public static void reconcileAll(ServerLevel level) {
        NationsSavedData nations = NationsSavedData.get(level);
        for (NationId nation : nations.all().keySet()) {
            reconcile(level, nation);
        }
    }

    public static Optional<NationId> nationFor(ServerLevel level, UUID player) {
        return NationMembershipService.nationFor(level, player);
    }

    private static Set<UUID> members(ServerLevel level, NationId nation) {
        Set<UUID> members = new HashSet<>();
        NationMembershipSavedData membershipData = NationMembershipSavedData.get(level);
        NationsSavedData.get(level).get(nation).ifPresent(value -> value.citizens().stream()
                .filter(player -> membershipData.membership(player)
                        .map(membership -> membership.nation().equals(nation))
                        .orElse(true))
                .forEach(members::add));
        membershipData.membershipsFor(nation).stream()
                .map(NationMembership::player)
                .forEach(members::add);
        return members;
    }
}
