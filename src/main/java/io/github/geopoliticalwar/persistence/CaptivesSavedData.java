package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.CaptiveRecord;
import io.github.geopoliticalwar.domain.war.CaptiveState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent captive records; the player UUID is the stable key. */
public final class CaptivesSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_captives";
    private final Map<UUID, CaptiveRecord> captives = new HashMap<>();

    public static CaptivesSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                CaptivesSavedData::load,
                CaptivesSavedData::new,
                DATA_NAME
        );
    }

    public static CaptivesSavedData load(CompoundTag tag) {
        CaptivesSavedData data = new CaptivesSavedData();
        for (Tag raw : tag.getList("captives", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                CaptiveRecord record = new CaptiveRecord(
                        value.getUUID("player"),
                        new ClaimId(value.getUUID("claim")),
                        new NationId(value.getUUID("holding_nation")),
                        new NationId(value.getUUID("former_nation")),
                        CaptiveState.valueOf(value.getString("state")),
                        value.getLong("captured_at"),
                        value.getLong("last_work_at"),
                        value.getLong("worked_seconds"),
                        value.getInt("unrest"),
                        value.getLong("released_at")
                );
                data.captives.put(record.player(), record);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid captive record", failure);
            }
        }
        return data;
    }

    public Optional<CaptiveRecord> get(UUID player) {
        return Optional.ofNullable(captives.get(player));
    }

    public Optional<CaptiveRecord> active(UUID player) {
        return get(player).filter(CaptiveRecord::active);
    }

    public Map<UUID, CaptiveRecord> all() {
        return Map.copyOf(captives);
    }

    public void set(CaptiveRecord record) {
        captives.put(record.player(), record);
        setDirty();
    }

    public void remove(UUID player) {
        if (captives.remove(player) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        captives.values().stream()
                .sorted((first, second) -> first.player().toString().compareTo(second.player().toString()))
                .forEach(record -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("player", record.player());
                    value.putUUID("claim", record.claim().value());
                    value.putUUID("holding_nation", record.holdingNation().value());
                    value.putUUID("former_nation", record.formerNation().value());
                    value.putString("state", record.state().name());
                    value.putLong("captured_at", record.capturedAtMillis());
                    value.putLong("last_work_at", record.lastWorkAtMillis());
                    value.putLong("worked_seconds", record.workedSeconds());
                    value.putInt("unrest", record.unrest());
                    value.putLong("released_at", record.releasedAtMillis());
                    values.add(value);
                });
        tag.put("captives", values);
        return tag;
    }
}
