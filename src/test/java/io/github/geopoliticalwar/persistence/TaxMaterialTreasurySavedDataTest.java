package io.github.geopoliticalwar.persistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxMaterialTreasurySavedDataTest {
    @Test
    void materialBalancesRoundTripAndHonorCapacity() {
        TaxMaterialTreasurySavedData data = new TaxMaterialTreasurySavedData();
        data.deposit("minecraft:diamond", 4L, 10L);
        assertThrows(IllegalArgumentException.class,
                () -> data.deposit("minecraft:diamond", 7L, 10L));

        TaxMaterialTreasurySavedData loaded = TaxMaterialTreasurySavedData.load(data.save(new CompoundTag()));
        assertEquals(4L, loaded.amount("minecraft:diamond"));
        assertEquals(4L, loaded.withdraw("minecraft:diamond", 4L));
        assertEquals(0L, loaded.amount("minecraft:diamond"));
    }
}
