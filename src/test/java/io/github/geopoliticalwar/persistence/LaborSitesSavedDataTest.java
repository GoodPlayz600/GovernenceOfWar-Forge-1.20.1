package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.LaborSite;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaborSitesSavedDataTest {
    @Test
    void laborOutputIsBoundedAndPersists() {
        LaborSite site = new LaborSite(UUID.randomUUID(), ClaimId.random(), NationId.random(),
                "minecraft:stone", 3L, 0L, 100L);
        LaborSitesSavedData data = new LaborSitesSavedData();
        data.add(site);
        data.produceForClaim(site.claim(), 2L, 200L, 5L);

        LaborSitesSavedData loaded = LaborSitesSavedData.load(data.save(new CompoundTag()));

        assertEquals(5L, loaded.get(site.id()).orElseThrow().bufferedOutput());
    }
}
