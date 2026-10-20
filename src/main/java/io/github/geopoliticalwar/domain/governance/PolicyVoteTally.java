package io.github.geopoliticalwar.domain.governance;

public record PolicyVoteTally(int eligibleVoters, int forVotes, int againstVotes) {
    public PolicyVoteTally {
        if (eligibleVoters < 0 || forVotes < 0 || againstVotes < 0
                || forVotes + againstVotes > eligibleVoters) {
            throw new IllegalArgumentException("Invalid policy vote tally");
        }
    }

    public boolean hasQuorum(int quorumBps) {
        if (quorumBps < 0 || quorumBps > 10_000) {
            throw new IllegalArgumentException("Invalid quorum");
        }
        return eligibleVoters > 0 && (long) (forVotes + againstVotes) * 10_000L
                >= (long) eligibleVoters * quorumBps;
    }

    public boolean passes(int quorumBps, int passBps) {
        if (!hasQuorum(quorumBps) || forVotes <= againstVotes || forVotes + againstVotes == 0) {
            return false;
        }
        return (long) forVotes * 10_000L
                >= (long) (forVotes + againstVotes) * passBps;
    }
}
