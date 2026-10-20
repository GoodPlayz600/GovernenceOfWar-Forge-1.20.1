package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.RebellionRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RebellionsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_rebellions";
    private final Map<NationId, RebellionRecord> rebellions = new HashMap<>();

    public static RebellionsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                RebellionsSavedData::load,
                RebellionsSavedData::new,
                DATA_NAME
        );
    }

    public static RebellionsSavedData load(CompoundTag tag) {
        RebellionsSavedData data = new RebellionsSavedData();
        for (Tag rebellionTag : tag.getList("rebellions", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag rebellion = (CompoundTag) rebellionTag;
                HashSet<UUID> cells = new HashSet<>();
                for (Tag cellTag : rebellion.getList("cells", Tag.TAG_COMPOUND)) {
                    cells.add(((CompoundTag) cellTag).getUUID("player"));
                }
                RebellionRecord value = new RebellionRecord(
                        new NationId(rebellion.getUUID("nation")),
                        rebellion.getInt("momentum"),
                        cells,
                        rebellion.getLong("last_update"),
                        rebellion.getBoolean("civil_war")
                );
                data.rebellions.put(value.nation(), value);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid rebellion record", failure);
            }
        }
        return data;
    }

    public Optional<RebellionRecord> get(NationId nation) {
        return Optional.ofNullable(rebellions.get(nation));
    }

    public void set(RebellionRecord record) {
        rebellions.put(record.nation(), record);
        setDirty();
    }

    public Map<NationId, RebellionRecord> all() {
        return Map.copyOf(rebellions);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        rebellions.values().stream()
                .sorted((first, second) -> first.nation().value().toString().compareTo(second.nation().value().toString()))
                .forEach(record -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("nation", record.nation().value());
                    value.putInt("momentum", record.momentum());
                    value.putLong("last_update", record.lastMomentumUpdateMillis());
                    value.putBoolean("civil_war", record.civilWar());
                    ListTag cells = new ListTag();
                    record.rebelCells().stream().sorted().forEach(player -> {
                        CompoundTag cell = new CompoundTag();
                        cell.putUUID("player", player);
                        cells.add(cell);
                    });
                    value.put("cells", cells);
                    serialized.add(value);
                });
        tag.put("rebellions", serialized);
        return tag;
    }
}
