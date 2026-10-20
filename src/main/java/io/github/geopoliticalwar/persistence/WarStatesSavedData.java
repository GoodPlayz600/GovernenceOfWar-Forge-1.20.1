package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.war.WarPhase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent per-dimension warfare phase registry. */
public final class WarStatesSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_war_states";

    private final Map<DimensionId, WarPhase> phases = new HashMap<>();

    public static WarStatesSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                WarStatesSavedData::load,
                WarStatesSavedData::new,
                DATA_NAME
        );
    }

    public static WarStatesSavedData load(CompoundTag tag) {
        WarStatesSavedData data = new WarStatesSavedData();
        ListTag serialized = tag.getList("phases", Tag.TAG_COMPOUND);
        for (Tag entryTag : serialized) {
            CompoundTag entry = (CompoundTag) entryTag;
            data.phases.put(
                    new DimensionId(entry.getString("dimension")),
                    WarPhase.valueOf(entry.getString("phase"))
            );
        }
        return data;
    }

    public WarPhase phase(ServerLevel level) {
        return phases.getOrDefault(dimension(level), WarPhase.PEACE);
    }

    public void setPhase(ServerLevel level, WarPhase phase) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(phase, "phase");
        phases.put(dimension(level), phase);
        setDirty();
    }

    public Map<DimensionId, WarPhase> all() {
        return Map.copyOf(phases);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        phases.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((first, second) -> first.value().compareTo(second.value())))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putString("dimension", entry.getKey().value());
                    value.putString("phase", entry.getValue().name());
                    serialized.add(value);
                });
        tag.put("phases", serialized);
        return tag;
    }

    private static DimensionId dimension(ServerLevel level) {
        return new DimensionId(level.dimension().location().toString());
    }
}
