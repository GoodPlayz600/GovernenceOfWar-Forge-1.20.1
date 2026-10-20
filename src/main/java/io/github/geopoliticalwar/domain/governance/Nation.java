package io.github.geopoliticalwar.domain.governance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable nation snapshot used by the persistent governance store. */
public record Nation(
        NationId id,
        String name,
        UUID leader,
        RegimeType regime,
        String color,
        Set<UUID> citizens,
        Map<UUID, Set<String>> roles,
        long createdAtMillis,
        long nextElectionAtMillis
) {
    public Nation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(leader, "leader");
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(citizens, "citizens");
        Objects.requireNonNull(roles, "roles");
        name = name.trim();
        color = color.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nation name cannot be empty");
        }
        if (color.isEmpty()) {
            throw new IllegalArgumentException("Nation color cannot be empty");
        }
        if (!citizens.contains(leader)) {
            throw new IllegalArgumentException("Nation leader must be a citizen");
        }

        citizens = Set.copyOf(citizens);
        Map<UUID, Set<String>> immutableRoles = new HashMap<>();
        roles.forEach((player, playerRoles) -> immutableRoles.put(player, Set.copyOf(playerRoles)));
        roles = Map.copyOf(immutableRoles);
    }

    public static Nation create(
            NationId id,
            String name,
            UUID leader,
            RegimeType regime,
            String color,
            long now,
            long electionCycleMillis
    ) {
        return new Nation(
                id,
                name,
                leader,
                regime,
                color,
                Set.of(leader),
                Map.of(leader, Set.of("leader")),
                now,
                now + electionCycleMillis
        );
    }

    public boolean isCitizen(UUID player) {
        return citizens.contains(player);
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public Nation addCitizen(UUID player) {
        Set<UUID> updated = new HashSet<>(citizens);
        updated.add(player);
        return new Nation(id, name, leader, regime, color, updated, roles, createdAtMillis, nextElectionAtMillis);
    }

    public Nation removeCitizen(UUID player) {
        if (leader.equals(player)) {
            throw new IllegalArgumentException("The leader must transfer leadership before leaving");
        }
        Set<UUID> updated = new HashSet<>(citizens);
        updated.remove(player);
        Map<UUID, Set<String>> updatedRoles = new HashMap<>(roles);
        updatedRoles.remove(player);
        return new Nation(id, name, leader, regime, color, updated, updatedRoles, createdAtMillis, nextElectionAtMillis);
    }

    public Nation withLeader(UUID newLeader, long nextElection) {
        if (!citizens.contains(newLeader)) {
            throw new IllegalArgumentException("New leader must be a citizen");
        }
        Map<UUID, Set<String>> updatedRoles = new HashMap<>(roles);
        updatedRoles.remove(leader);
        updatedRoles.put(newLeader, Set.of("leader"));
        return new Nation(id, name, newLeader, regime, color, citizens, updatedRoles, createdAtMillis, nextElection);
    }

    public Nation withRegime(RegimeType newRegime) {
        return new Nation(id, name, leader, newRegime, color, citizens, roles, createdAtMillis, nextElectionAtMillis);
    }

    public Nation withNextElection(long nextElection) {
        return new Nation(id, name, leader, regime, color, citizens, roles, createdAtMillis, nextElection);
    }
}
