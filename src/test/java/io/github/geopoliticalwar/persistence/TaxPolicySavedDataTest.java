package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxPolicySavedDataTest {
    @Test
    void policyRoundTrips() {
        ClaimId claim = ClaimId.random();
        TaxPolicySavedData data = new TaxPolicySavedData();
        data.set(claim, TaxPolicy.item(3L, new CurrencyId("test:credits"), "minecraft:wheat"));

        TaxPolicySavedData loaded = TaxPolicySavedData.load(data.save(new CompoundTag()));

        assertEquals(TaxPolicy.item(3L, new CurrencyId("test:credits"), "minecraft:wheat"), loaded.get(claim).orElseThrow());
    }
}
