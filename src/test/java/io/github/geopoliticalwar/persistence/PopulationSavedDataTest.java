package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.governance.NationId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PopulationSavedDataTest {
    @Test
    void populationRoundTripsAsLong() {
        NationId nation = NationId.random();
        PopulationSavedData data = new PopulationSavedData();
        data.setPopulation(nation, (long) Integer.MAX_VALUE + 500L);

        PopulationSavedData loaded = PopulationSavedData.load(data.save(new CompoundTag()));

        assertEquals((long) Integer.MAX_VALUE + 500L, loaded.population(nation));
    }

    @Test
    void negativePopulationIsRejected() {
        PopulationSavedData data = new PopulationSavedData();
        assertThrows(IllegalArgumentException.class, () -> data.setPopulation(NationId.random(), -1L));
    }
}
