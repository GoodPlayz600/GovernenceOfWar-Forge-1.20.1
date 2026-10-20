package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.BlockPoint;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.CapturePoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CapturePointsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_capture_points";
    private final Map<UUID, CapturePoint> points = new HashMap<>();

    public static CapturePointsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                CapturePointsSavedData::load,
                CapturePointsSavedData::new,
                DATA_NAME
        );
    }

    public static CapturePointsSavedData load(CompoundTag tag) {
        CapturePointsSavedData data = new CapturePointsSavedData();
        for (Tag pointTag : tag.getList("points", Tag.TAG_COMPOUND)) {
            try {
                    CompoundTag point = (CompoundTag) pointTag;
                Optional<NationId> owner = point.hasUUID("owner")
                        ? Optional.of(new NationId(point.getUUID("owner")))
                        : Optional.empty();
                CapturePoint value = new CapturePoint(
                        point.getUUID("id"),
                        point.getString("name"),
                        new DimensionId(point.getString("dimension")),
                        new BlockPoint(point.getLong("x"), point.getLong("y"), point.getLong("z")),
                        point.getInt("radius"),
                        owner,
                        point.getLong("progress_seconds"),
                        point.getLong("last_tick")
                );
                data.points.put(value.id(), value);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid capture point", failure);
            }
        }
        return data;
    }

    public void add(CapturePoint point) {
        if (points.containsKey(point.id())) {
            throw new IllegalArgumentException("Capture point already exists");
        }
        points.put(point.id(), point);
        setDirty();
    }

    public void replace(CapturePoint point) {
        points.put(point.id(), point);
        setDirty();
    }

    public boolean remove(UUID id) {
        if (points.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public Map<UUID, CapturePoint> all() {
        return Map.copyOf(points);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        points.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .forEach(point -> {
                    CompoundTag value = new CompoundTag();
                    value.putInt("schema", 1);
                    value.putUUID("id", point.id());
                    value.putString("name", point.name());
                    value.putString("dimension", point.dimension().value());
                    value.putLong("x", point.center().x());
                    value.putLong("y", point.center().y());
                    value.putLong("z", point.center().z());
                    value.putInt("radius", point.radius());
                    point.owner().ifPresent(owner -> value.putUUID("owner", owner.value()));
                    value.putLong("progress_seconds", point.progressSeconds());
                    value.putLong("last_tick", point.lastTickMillis());
                    serialized.add(value);
                });
        tag.put("points", serialized);
        return tag;
    }
}
