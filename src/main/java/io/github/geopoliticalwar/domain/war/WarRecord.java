package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record WarRecord(
        WarId id,
        Set<DimensionId> dimensions,
        Set<WarParticipant> participants,
        Set<WarGoal> goals,
        WarStatus status,
        long declaredAtMillis,
        long startedAtMillis,
        long resolvedAtMillis,
        Optional<NationId> victor
) {
    public WarRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimensions, "dimensions");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(goals, "goals");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(victor, "victor");
        if (dimensions.isEmpty() || declaredAtMillis < 0 || startedAtMillis < 0 || resolvedAtMillis < 0) {
            throw new IllegalArgumentException("Invalid war record");
        }
        if (participants.size() < 2) {
            throw new IllegalArgumentException("A war needs at least two participants");
        }
        Map<NationId, WarSide> sides = new HashMap<>();
        participants.forEach(participant -> {
            if (sides.putIfAbsent(participant.nation(), participant.side()) != null) {
                throw new IllegalArgumentException("A nation cannot be listed twice in a war");
            }
        });
        if (!sides.containsValue(WarSide.ATTACKER) || !sides.containsValue(WarSide.DEFENDER)) {
            throw new IllegalArgumentException("A war needs an attacker and defender");
        }
        Set<UUID> goalIds = new HashSet<>();
        goals.forEach(goal -> {
            if (!goalIds.add(goal.id()) || !sides.containsKey(goal.beneficiary())) {
                throw new IllegalArgumentException("Invalid or duplicate war goal");
            }
        });
        if (status == WarStatus.RESOLVED && victor.isEmpty()) {
            throw new IllegalArgumentException("Resolved wars need a victor");
        }
        dimensions = Set.copyOf(dimensions);
        participants = Set.copyOf(participants);
        goals = Set.copyOf(goals);
    }

    public static WarRecord declare(DimensionId dimension, NationId attacker, NationId defender, long now) {
        if (attacker.equals(defender)) {
            throw new IllegalArgumentException("A nation cannot declare war on itself");
        }
        return new WarRecord(
                WarId.random(),
                Set.of(dimension),
                Set.of(
                        new WarParticipant(attacker, WarSide.ATTACKER, now),
                        new WarParticipant(defender, WarSide.DEFENDER, now)
                ),
                Set.of(),
                WarStatus.DECLARED,
                now,
                0L,
                0L,
                Optional.empty()
        );
    }

    public boolean includes(NationId nation) {
        return participants.stream().anyMatch(participant -> participant.nation().equals(nation));
    }

    public Optional<WarSide> sideOf(NationId nation) {
        return participants.stream()
                .filter(participant -> participant.nation().equals(nation))
                .map(WarParticipant::side)
                .findFirst();
    }

    public boolean isActive() {
        return status == WarStatus.ACTIVE;
    }

    public WarRecord addDimension(DimensionId dimension) {
        if (status != WarStatus.DECLARED) {
            throw new IllegalArgumentException("War dimensions are immutable after activation");
        }
        Set<DimensionId> updated = new HashSet<>(dimensions);
        updated.add(dimension);
        return new WarRecord(id, updated, participants, goals, status, declaredAtMillis, startedAtMillis, resolvedAtMillis, victor);
    }

    public WarRecord addParticipant(NationId nation, WarSide side, long now) {
        if (status != WarStatus.DECLARED) {
            throw new IllegalArgumentException("Participants can only be configured before activation");
        }
        if (includes(nation)) {
            throw new IllegalArgumentException("Nation is already a participant");
        }
        Set<WarParticipant> updated = new HashSet<>(participants);
        updated.add(new WarParticipant(nation, side, now));
        return new WarRecord(id, dimensions, updated, goals, status, declaredAtMillis, startedAtMillis, resolvedAtMillis, victor);
    }

    public WarRecord addGoal(WarGoal goal) {
        if (status != WarStatus.DECLARED) {
            throw new IllegalArgumentException("Goals can only be configured before activation");
        }
        Set<WarGoal> updated = new HashSet<>(goals);
        if (updated.stream().anyMatch(existing -> existing.id().equals(goal.id()))) {
            throw new IllegalArgumentException("War goal already exists");
        }
        updated.add(goal);
        return new WarRecord(id, dimensions, participants, updated, status, declaredAtMillis, startedAtMillis, resolvedAtMillis, victor);
    }

    public WarRecord withGoal(WarGoal goal) {
        Set<WarGoal> updated = new HashSet<>(goals);
        if (!updated.removeIf(existing -> existing.id().equals(goal.id()))) {
            throw new IllegalArgumentException("Unknown war goal");
        }
        updated.add(goal);
        return new WarRecord(id, dimensions, participants, updated, status, declaredAtMillis, startedAtMillis, resolvedAtMillis, victor);
    }

    public WarRecord activate(long now) {
        if (status != WarStatus.DECLARED) {
            throw new IllegalArgumentException("Only declared wars can be activated");
        }
        return new WarRecord(id, dimensions, participants, goals, WarStatus.ACTIVE, declaredAtMillis, now, 0L, Optional.empty());
    }

    public WarRecord resolve(NationId winningNation, long now) {
        if (status != WarStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active wars can be resolved");
        }
        if (!includes(winningNation)) {
            throw new IllegalArgumentException("Victor must be a participant");
        }
        return new WarRecord(id, dimensions, participants, goals, WarStatus.RESOLVED, declaredAtMillis, startedAtMillis, now, Optional.of(winningNation));
    }
}
