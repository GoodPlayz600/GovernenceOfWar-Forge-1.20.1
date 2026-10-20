package io.github.geopoliticalwar.domain.governance;

public enum MembershipStatus {
    CITIZEN,
    RESIDENT,
    SUBJECT;

    public boolean mayVote() {
        return this == CITIZEN;
    }

    public boolean mayHoldOffice() {
        return this == CITIZEN;
    }
}
