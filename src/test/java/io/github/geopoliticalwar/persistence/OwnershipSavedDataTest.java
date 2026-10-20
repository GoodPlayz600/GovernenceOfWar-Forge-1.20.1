package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.economy.OwnershipChangeReason;
import io.github.geopoliticalwar.domain.economy.OwnershipRecord;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnershipSavedDataTest {
    @Test
    void ownershipHistoryRoundTrips() {
        ClaimId claim = ClaimId.random();
        OwnershipRecord record = new OwnershipRecord(
                UUID.randomUUID(), claim,
                new PrincipalId("player:" + UUID.randomUUID()),
                new PrincipalId("nation:" + UUID.randomUUID()),
                OwnershipChangeReason.ADMIN_TRANSFER,
                UUID.randomUUID(), 100L
        );
        OwnershipSavedData data = new OwnershipSavedData();
        data.add(record);

        OwnershipSavedData loaded = OwnershipSavedData.load(data.save(new CompoundTag()));

        assertEquals(java.util.List.of(record), loaded.forClaim(claim));
    }
}
