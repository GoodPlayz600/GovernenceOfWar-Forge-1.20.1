package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class OccupationsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_occupations";
    private final Map<ClaimId, OccupationRecord> occupations = new HashMap<>();

    public static OccupationsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(OccupationsSavedData::load, OccupationsSavedData::new, DATA_NAME);
    }

    public static OccupationsSavedData load(CompoundTag tag) {
        OccupationsSavedData data = new OccupationsSavedData();
        for (Tag value : tag.getList("occupations", Tag.TAG_COMPOUND)) {
            try {
                OccupationRecord occupation = OccupationNbtCodec.read((CompoundTag) value);
                data.occupations.put(occupation.claim(), occupation);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted occupation", failure);
            }
        }
        return data;
    }

    public Optional<OccupationRecord> get(ClaimId claim) {
        return Optional.ofNullable(occupations.get(claim));
    }

    public void set(OccupationRecord occupation) {
        occupations.put(occupation.claim(), occupation);
        setDirty();
    }

    public boolean remove(ClaimId claim) {
        if (occupations.remove(claim) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public Map<ClaimId, OccupationRecord> all() {
        return Map.copyOf(occupations);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag values = new ListTag();
        occupations.values().stream()
                .sorted((first, second) -> first.claim().value().toString().compareTo(second.claim().value().toString()))
                .map(OccupationNbtCodec::write)
                .forEach(values::add);
        tag.put("occupations", values);
        return tag;
    }
}
