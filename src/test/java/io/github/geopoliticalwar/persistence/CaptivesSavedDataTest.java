package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.CaptiveRecord;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptivesSavedDataTest {
    @Test
    void captiveRecordRoundTrips() {
        UUID player = UUID.randomUUID();
        CaptiveRecord record = CaptiveRecord.active(player, ClaimId.random(), NationId.random(), NationId.random(), 1L);
        CaptivesSavedData data = new CaptivesSavedData();
        data.set(record);

        CaptivesSavedData loaded = CaptivesSavedData.load(data.save(new CompoundTag()));

        assertEquals(record, loaded.get(player).orElseThrow());
    }
}
