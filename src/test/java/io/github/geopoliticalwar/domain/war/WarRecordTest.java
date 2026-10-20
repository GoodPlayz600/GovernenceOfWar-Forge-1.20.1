package io.github.geopoliticalwar.domain.war;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarRecordTest {
    @Test
    void aNationCannotJoinBothSidesOrBeDuplicated() {
        NationId attacker = NationId.random();
        NationId defender = NationId.random();
        WarRecord war = WarRecord.declare(new DimensionId("minecraft:overworld"), attacker, defender, 1L);

        assertThrows(IllegalArgumentException.class, () -> war.addParticipant(attacker, WarSide.DEFENDER, 2L));
        assertThrows(IllegalArgumentException.class, () -> war.addParticipant(defender, WarSide.ATTACKER, 2L));
    }

    @Test
    void goalBeneficiaryMustBeParticipantAndProgressIsCapped() {
        NationId attacker = NationId.random();
        NationId defender = NationId.random();
        WarRecord war = WarRecord.declare(new DimensionId("minecraft:overworld"), attacker, defender, 1L);
        WarGoal goal = WarGoal.conquestClaim(UUID.randomUUID(), attacker, ClaimId.random());

        assertEquals(1L, goal.advance(99L).score());
        assertThrows(IllegalArgumentException.class, () -> war.addGoal(
                WarGoal.conquestClaim(UUID.randomUUID(), NationId.random(), ClaimId.random())
        ));
        assertEquals(WarStatus.ACTIVE, war.activate(2L).status());
    }

    @Test
    void goalIdsMustBeUniqueEvenWhenTheGoalPayloadDiffers() {
        NationId attacker = NationId.random();
        NationId defender = NationId.random();
        WarRecord war = WarRecord.declare(new DimensionId("minecraft:overworld"), attacker, defender, 1L);
        UUID goalId = UUID.randomUUID();
        WarGoal first = WarGoal.conquestClaim(goalId, attacker, ClaimId.random());
        WarGoal second = WarGoal.conquestClaim(goalId, attacker, ClaimId.random());

        assertThrows(IllegalArgumentException.class, () -> war.addGoal(first).addGoal(second));
    }
}
