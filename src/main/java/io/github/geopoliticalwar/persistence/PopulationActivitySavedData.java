package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Last-seen timestamps used to distinguish nominal population from active defense. */
public final class PopulationActivitySavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_population_activity";
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public static PopulationActivitySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PopulationActivitySavedData::load,
                PopulationActivitySavedData::new,
                DATA_NAME
        );
    }

    public static PopulationActivitySavedData load(CompoundTag tag) {
        PopulationActivitySavedData data = new PopulationActivitySavedData();
        for (Tag raw : tag.getList("players", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                long timestamp = value.getLong("last_seen");
                if (timestamp < 0L) {
                    throw new IllegalArgumentException("Last-seen timestamp cannot be negative");
                }
                data.lastSeen.put(value.getUUID("player"), timestamp);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid population activity record", failure);
            }
        }
        return data;
    }

    public long lastSeen(UUID player) {
        return lastSeen.getOrDefault(player, 0L);
    }

    public void touch(UUID player, long now) {
        if (now < 0L) {
            throw new IllegalArgumentException("Activity timestamp cannot be negative");
        }
        Long previous = lastSeen.put(player, now);
        if (previous == null || previous.longValue() != now) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        lastSeen.entrySet().stream()
                .sorted((first, second) -> first.getKey().toString().compareTo(second.getKey().toString()))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("player", entry.getKey());
                    value.putLong("last_seen", entry.getValue());
                    values.add(value);
                });
        tag.put("players", values);
        return tag;
    }
}
