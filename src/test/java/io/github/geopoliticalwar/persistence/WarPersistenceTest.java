package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import io.github.geopoliticalwar.domain.war.OccupationRights;
import io.github.geopoliticalwar.domain.war.WarGoal;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.domain.war.WarStatus;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarPersistenceTest {
    @Test
    void warAndOccupationRecordsRoundTripThroughNbt() {
        NationId attacker = NationId.random();
        NationId defender = NationId.random();
        WarRecord war = WarRecord.declare(new DimensionId("minecraft:overworld"), attacker, defender, 10L)
                .addGoal(WarGoal.conquestClaim(java.util.UUID.randomUUID(), attacker, ClaimId.random()))
                .activate(20L);
        WarRecord loadedWar = WarNbtCodec.read(WarNbtCodec.write(war));

        assertEquals(WarStatus.ACTIVE, loadedWar.status());
        assertEquals(war.participants(), loadedWar.participants());
        assertEquals(war.goals(), loadedWar.goals());

        OccupationRecord occupation = new OccupationRecord(
                ClaimId.random(),
                war.id(),
                attacker,
                new PrincipalId("player:former"),
                OccupationRights.RESIDENT,
                30L,
                40L,
                Optional.empty()
        );
        OccupationRecord loadedOccupation = OccupationNbtCodec.read(OccupationNbtCodec.write(occupation));
        assertEquals(occupation, loadedOccupation);
    }

    @Test
    void oldEmptyWarTagIsRejectedWithoutCreatingAnUnsafeRecord() {
        assertEquals(0, WarsSavedData.load(new CompoundTag()).all().size());
    }
}
