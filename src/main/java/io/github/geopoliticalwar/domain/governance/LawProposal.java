package io.github.geopoliticalwar.domain.governance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable democratic proposal with a voter snapshot. */
public record LawProposal(
        UUID id,
        NationId nation,
        UUID sponsor,
        PolicyChange change,
        long openedAtMillis,
        long closesAtMillis,
        Set<UUID> eligibleVoters,
        Map<UUID, VoteChoice> votes,
        ProposalStatus status,
        Optional<UUID> enactedLaw,
        Optional<Long> finalizedAtMillis
) {
    public LawProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nation, "nation");
        Objects.requireNonNull(sponsor, "sponsor");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(eligibleVoters, "eligibleVoters");
        Objects.requireNonNull(votes, "votes");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(enactedLaw, "enactedLaw");
        Objects.requireNonNull(finalizedAtMillis, "finalizedAtMillis");
        if (openedAtMillis < 0L || closesAtMillis < openedAtMillis
                || finalizedAtMillis.map(value -> value < closesAtMillis && status != ProposalStatus.EXPIRED).orElse(false)) {
            throw new IllegalArgumentException("Invalid law proposal timestamps");
        }
        Set<UUID> voters = new HashSet<>(eligibleVoters);
        if (!voters.containsAll(votes.keySet())) {
            throw new IllegalArgumentException("A non-eligible player cannot vote");
        }
        eligibleVoters = Set.copyOf(voters);
        votes = Map.copyOf(new HashMap<>(votes));
    }

    public LawProposal vote(UUID voter, VoteChoice choice) {
        if (status != ProposalStatus.OPEN || !eligibleVoters.contains(voter)) {
            throw new IllegalArgumentException("That proposal is not available to this voter");
        }
        Map<UUID, VoteChoice> updated = new HashMap<>(votes);
        updated.put(voter, choice);
        return new LawProposal(id, nation, sponsor, change, openedAtMillis, closesAtMillis,
                eligibleVoters, updated, status, enactedLaw, finalizedAtMillis);
    }

    public LawProposal finalized(ProposalStatus result, Optional<UUID> law, long now) {
        if (status != ProposalStatus.OPEN || result == ProposalStatus.OPEN) {
            throw new IllegalArgumentException("Proposal is already finalized");
        }
        return new LawProposal(id, nation, sponsor, change, openedAtMillis, closesAtMillis,
                eligibleVoters, votes, result, law, Optional.of(now));
    }
}
