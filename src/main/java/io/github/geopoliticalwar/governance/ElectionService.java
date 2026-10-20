package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.Election;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import io.github.geopoliticalwar.persistence.GovernmentSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-side democratic election workflow. */
public final class ElectionService {
    private ElectionService() {
    }

    public static Election open(ServerLevel level, Nation nation, long now) {
        if (nation.regime() != RegimeType.DEMOCRACY) {
            throw new IllegalArgumentException("Dictatorships do not use elections");
        }
        GovernmentSavedData government = GovernmentSavedData.get(level);
        if (government.election(nation.id()).filter(election -> election.isOpen(now)).isPresent()) {
            throw new IllegalArgumentException("An election is already open");
        }
        government.election(nation.id()).ifPresent(closed -> {
            if (!closed.isOpen(now)) {
                government.removeElection(nation.id());
            }
        });

        long window = Math.multiplyExact(ServerConfig.ELECTION_VOTING_WINDOW_SECONDS.get(), 1_000L);
        Election election = Election.open(nation.id(), now, Math.addExact(now, window));
        government.setElection(election);
        return election;
    }

    public static Election stand(ServerLevel level, Nation nation, UUID candidate, long now) {
        Election election = currentElection(level, nation.id())
                .filter(value -> value.isOpen(now))
                .orElseThrow(() -> new IllegalArgumentException("No election is currently open"));
        if (!NationMembershipService.mayHoldOffice(level, candidate, nation.id())) {
            throw new IllegalArgumentException("Only citizens may stand for election");
        }
        Election updated = election.addCandidate(candidate);
        GovernmentSavedData.get(level).setElection(updated);
        return updated;
    }

    public static Election vote(ServerLevel level, Nation nation, UUID voter, UUID candidate, long now) {
        Election election = currentElection(level, nation.id())
                .filter(value -> value.isOpen(now))
                .orElseThrow(() -> new IllegalArgumentException("No election is currently open"));
        if (!NationMembershipService.status(level, voter)
                .filter(io.github.geopoliticalwar.domain.governance.MembershipStatus::mayVote)
                .isPresent()
                || !NationMembershipService.nationFor(level, voter).filter(nation.id()::equals).isPresent()) {
            throw new IllegalArgumentException("Only citizens may vote");
        }
        Election updated = election.castVote(voter, candidate);
        GovernmentSavedData.get(level).setElection(updated);
        return updated;
    }

    public static Optional<Nation> finalizeIfReady(ServerLevel level, Nation nation, long now) {
        GovernmentSavedData government = GovernmentSavedData.get(level);
        Optional<Election> current = government.election(nation.id());
        if (current.isEmpty() || current.get().isOpen(now)) {
            return Optional.empty();
        }

        Election election = current.get();
        Set<UUID> eligible = nation.citizens().stream()
                .filter(candidate -> NationMembershipService.mayHoldOffice(level, candidate, nation.id()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        election = election.retainEligible(eligible);
        long nextElection = Math.addExact(now, Math.multiplyExact(ServerConfig.ELECTION_CYCLE_SECONDS.get(), 1_000L));
        Nation updated = nation;
        if (!election.candidates().isEmpty()) {
            updated = nation.withLeader(election.winner(), nextElection);
        } else {
            updated = nation.withNextElection(nextElection);
        }
        NationsSavedData.get(level).replace(updated);
        government.removeElection(nation.id());
        return Optional.of(updated);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        NationsSavedData nations = NationsSavedData.get(level);
        long now = System.currentTimeMillis();
        for (Nation nation : nations.all().values()) {
            if (nation.regime() != RegimeType.DEMOCRACY) {
                continue;
            }
            Optional<Election> election = GovernmentSavedData.get(level).election(nation.id());
            if (election.isPresent()) {
                finalizeIfReady(level, nation, now);
            } else if (now >= nation.nextElectionAtMillis()) {
                open(level, nation, now);
            }
        }
    }

    private static Optional<Election> currentElection(ServerLevel level, NationId nation) {
        return GovernmentSavedData.get(level).election(nation);
    }
}
