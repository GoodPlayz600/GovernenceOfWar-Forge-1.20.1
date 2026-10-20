package io.github.geopoliticalwar.domain.governance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A server-authoritative nation election with one vote per voter. */
public record Election(
        NationId nation,
        long openedAtMillis,
        long closesAtMillis,
        Set<UUID> candidates,
        Map<UUID, UUID> votes
) {
    public Election {
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(votes, "votes");
        if (openedAtMillis >= closesAtMillis) {
            throw new IllegalArgumentException("Election must have a positive voting window");
        }
        candidates = Set.copyOf(candidates);
        votes = Map.copyOf(votes);
    }

    public static Election open(NationId nation, long openedAtMillis, long closesAtMillis) {
        return new Election(nation, openedAtMillis, closesAtMillis, Set.of(), Map.of());
    }

    public boolean isOpen(long now) {
        return now >= openedAtMillis && now < closesAtMillis;
    }

    public Election addCandidate(UUID player) {
        Set<UUID> updated = new HashSet<>(candidates);
        updated.add(player);
        return new Election(nation, openedAtMillis, closesAtMillis, updated, votes);
    }

    public Election castVote(UUID voter, UUID candidate) {
        if (!candidates.contains(candidate)) {
            throw new IllegalArgumentException("Candidate is not registered");
        }
        Map<UUID, UUID> updated = new HashMap<>(votes);
        updated.put(voter, candidate);
        return new Election(nation, openedAtMillis, closesAtMillis, candidates, updated);
    }

    public Election retainEligible(Set<UUID> eligible) {
        Set<UUID> updatedCandidates = new HashSet<>(candidates);
        updatedCandidates.retainAll(eligible);
        Map<UUID, UUID> updatedVotes = new HashMap<>();
        votes.forEach((voter, candidate) -> {
            if (eligible.contains(voter) && updatedCandidates.contains(candidate)) {
                updatedVotes.put(voter, candidate);
            }
        });
        return new Election(nation, openedAtMillis, closesAtMillis, updatedCandidates, updatedVotes);
    }

    public UUID winner() {
        return candidates.stream()
                .sorted((first, second) -> first.toString().compareTo(second.toString()))
                .reduce((current, candidate) -> voteCount(candidate) > voteCount(current) ? candidate : current)
                .orElseThrow(() -> new IllegalStateException("Election has no candidates"));
    }

    public int voteCount(UUID candidate) {
        return (int) votes.values().stream().filter(candidate::equals).count();
    }
}
