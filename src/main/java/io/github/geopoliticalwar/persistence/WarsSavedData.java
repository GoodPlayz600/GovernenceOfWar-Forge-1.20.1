package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.war.WarGoal;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.domain.war.WarStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;

public final class WarsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_wars";
    private final Map<WarId, WarRecord> wars = new HashMap<>();

    public static WarsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WarsSavedData::load, WarsSavedData::new, DATA_NAME);
    }

    public static WarsSavedData load(CompoundTag tag) {
        WarsSavedData data = new WarsSavedData();
        for (Tag value : tag.getList("wars", Tag.TAG_COMPOUND)) {
            try {
                WarRecord war = WarNbtCodec.read((CompoundTag) value);
                data.wars.put(war.id(), war);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted war", failure);
            }
        }
        return data;
    }

    public void add(WarRecord war) {
        if (wars.containsKey(war.id())) {
            throw new IllegalArgumentException("War already exists");
        }
        wars.put(war.id(), war);
        setDirty();
    }

    public void replace(WarRecord war) {
        if (!wars.containsKey(war.id())) {
            throw new IllegalArgumentException("Unknown war");
        }
        wars.put(war.id(), war);
        setDirty();
    }

    public Optional<WarRecord> get(WarId id) {
        return Optional.ofNullable(wars.get(id));
    }

    public Map<WarId, WarRecord> all() {
        return Map.copyOf(wars);
    }

    public Optional<WarRecord> activeForDimension(DimensionId dimension) {
        return wars.values().stream()
                .filter(war -> war.status() == WarStatus.ACTIVE && war.dimensions().contains(dimension))
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .findFirst();
    }

    public Optional<WarRecord> activeForCapturePoint(UUID point) {
        return wars.values().stream()
                .filter(war -> war.status() == WarStatus.ACTIVE)
                .filter(war -> war.goals().stream().anyMatch(goal -> goal.capturePoint().filter(point::equals).isPresent() && !goal.achieved()))
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .findFirst();
    }

    public Optional<WarRecord> activeForClaim(ClaimId claim) {
        return wars.values().stream()
                .filter(war -> war.status() == WarStatus.ACTIVE)
                .filter(war -> war.goals().stream().anyMatch(goal -> goal.claim().filter(claim::equals).isPresent() && !goal.achieved()))
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .findFirst();
    }

    public boolean referencesClaim(ClaimId claim) {
        return wars.values().stream().anyMatch(war -> war.goals().stream()
                .anyMatch(goal -> goal.claim().filter(claim::equals).isPresent()));
    }

    public boolean referencesCapturePoint(UUID point) {
        return wars.values().stream().anyMatch(war -> war.goals().stream()
                .anyMatch(goal -> goal.capturePoint().filter(point::equals).isPresent()));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag values = new ListTag();
        wars.values().stream()
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .map(WarNbtCodec::write)
                .forEach(values::add);
        tag.put("wars", values);
        return tag;
    }
}
