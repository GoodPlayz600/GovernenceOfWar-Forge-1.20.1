package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persisted population counts used by capacity, taxation, and government policy. */
public final class PopulationSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_population";
    private final Map<NationId, Long> populations = new HashMap<>();

    public static PopulationSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PopulationSavedData::load,
                PopulationSavedData::new,
                DATA_NAME
        );
    }

    public static PopulationSavedData load(CompoundTag tag) {
        PopulationSavedData data = new PopulationSavedData();
        for (Tag raw : tag.getList("populations", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                long population = value.getLong("population");
                if (population < 0L) {
                    throw new IllegalArgumentException("Population cannot be negative");
                }
                data.populations.put(new NationId(value.getUUID("nation")), population);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted population", failure);
            }
        }
        return data;
    }

    public long population(NationId nation) {
        return populations.getOrDefault(nation, 0L);
    }

    public boolean contains(NationId nation) {
        return populations.containsKey(nation);
    }

    public void setPopulation(NationId nation, long population) {
        if (population < 0L) {
            throw new IllegalArgumentException("Population cannot be negative");
        }
        Long previous = populations.put(nation, population);
        if (previous == null || previous.longValue() != population) {
            setDirty();
        }
    }

    public void adjustPopulation(NationId nation, long delta) {
        long updated = Math.addExact(population(nation), delta);
        if (updated < 0L) {
            throw new IllegalArgumentException("Population cannot become negative");
        }
        setPopulation(nation, updated);
    }

    public Map<NationId, Long> all() {
        return Map.copyOf(populations);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        populations.entrySet().stream()
                .sorted((first, second) -> first.getKey().value().toString()
                        .compareTo(second.getKey().value().toString()))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("nation", entry.getKey().value());
                    value.putLong("population", entry.getValue());
                    values.add(value);
                });
        tag.put("populations", values);
        return tag;
    }
}
