package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.governance.EnactedLaw;
import io.github.geopoliticalwar.domain.governance.LawOrigin;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.PolicyChange;
import io.github.geopoliticalwar.domain.governance.PolicyCategory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovernmentSavedDataTest {
    @Test
    void enactedLawAndActivePolicyRoundTripAlongsideLegacyEmptyElections() {
        GovernmentSavedData data = new GovernmentSavedData();
        NationId nation = NationId.random();
        EnactedLaw law = new EnactedLaw(
                UUID.randomUUID(), nation, LawOrigin.DECREE, UUID.randomUUID(), Optional.empty(),
                PolicyChange.payroll(5_000), 100L
        );
        data.setEnactedLaw(law);
        data.setPolicy(nation, PolicyCategory.PAYROLL,
                new io.github.geopoliticalwar.domain.governance.PolicyState(law.id(), law.change(), 100L, 0L));

        GovernmentSavedData loaded = GovernmentSavedData.load(data.save(new CompoundTag()));

        assertEquals(law, loaded.enactedLaw(law.id()).orElseThrow());
        assertEquals(law.change(), loaded.policy(nation, PolicyCategory.PAYROLL).orElseThrow().change());
    }

    @Test
    void oldElectionOnlyDataLoads() {
        GovernmentSavedData loaded = GovernmentSavedData.load(new CompoundTag());
        assertEquals(java.util.Map.of(), loaded.enactedLaws());
    }

    @Test
    void futureSchemaIsRejectedInsteadOfSilentlyDowngraded() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 3);

        assertThrows(IllegalArgumentException.class, () -> GovernmentSavedData.load(future));
    }
}
