package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.ChunkColumnYClaimIndex;
import io.github.geopoliticalwar.domain.claim.ClaimSpatialIndex;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.claim.BlockBox;
import io.github.geopoliticalwar.domain.claim.BlockPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Objects;

/** Canonical cross-dimension claim storage attached to the Overworld. */
public final class ClaimsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_claims";

    private final Map<ClaimId, Claim> claims = new HashMap<>();
    private final ClaimSpatialIndex spatialIndex = new ChunkColumnYClaimIndex();
    private final Map<UUID, Selection> selections = new HashMap<>();

    public static ClaimsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ClaimsSavedData::load,
                ClaimsSavedData::new,
                DATA_NAME
        );
    }

    public static ClaimsSavedData load(CompoundTag tag) {
        ClaimsSavedData data = new ClaimsSavedData();
        ListTag serializedClaims = tag.getList("claims", Tag.TAG_COMPOUND);
        for (Tag serializedClaim : serializedClaims) {
            try {
                data.add(ClaimNbtCodec.read((CompoundTag) serializedClaim));
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted claim", failure);
            }
        }
        return data;
    }

    public void add(Claim claim) {
        spatialIndex.add(claim);
        claims.put(claim.id(), claim);
        setDirty();
    }

    public void replace(Claim claim) {
        spatialIndex.replace(claim);
        claims.put(claim.id(), claim);
        setDirty();
    }

    public boolean remove(ClaimId id) {
        if (!claims.containsKey(id)) {
            return false;
        }
        spatialIndex.remove(id);
        claims.remove(id);
        setDirty();
        return true;
    }

    public Optional<Claim> get(ClaimId id) {
        return Optional.ofNullable(claims.get(id));
    }

    public Set<ClaimId> containing(DimensionId dimension, long x, long y, long z) {
        return spatialIndex.containing(dimension, x, y, z);
    }

    public Set<ClaimId> intersecting(DimensionId dimension, BlockBox area) {
        return spatialIndex.intersecting(dimension, area);
    }

    public Map<ClaimId, Claim> all() {
        return Map.copyOf(claims);
    }

    public void setSelection(UUID player, int corner, DimensionId dimension, BlockPoint point) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(point, "point");
        if (corner != 1 && corner != 2) {
            throw new IllegalArgumentException("Selection corner must be 1 or 2");
        }

        Selection previous = selections.get(player);
        Selection updated = previous == null
                ? new Selection(dimension, corner == 1 ? Optional.of(point) : Optional.empty(), corner == 2 ? Optional.of(point) : Optional.empty())
                : previous.with(corner, dimension, point);
        selections.put(player, updated);
    }

    public Optional<Selection> selection(UUID player) {
        return Optional.ofNullable(selections.get(player));
    }

    public void clearSelection(UUID player) {
        selections.remove(player);
    }

    public record Selection(DimensionId dimension, Optional<BlockPoint> first, Optional<BlockPoint> second) {
        public Selection {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        private Selection with(int corner, DimensionId newDimension, BlockPoint point) {
            if (!dimension.equals(newDimension)) {
                return new Selection(
                        newDimension,
                        corner == 1 ? Optional.of(point) : Optional.empty(),
                        corner == 2 ? Optional.of(point) : Optional.empty()
                );
            }
            return new Selection(
                    dimension,
                    corner == 1 ? Optional.of(point) : first,
                    corner == 2 ? Optional.of(point) : second
            );
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serializedClaims = new ListTag();
        claims.values().stream()
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .map(ClaimNbtCodec::write)
                .forEach(serializedClaims::add);
        tag.put("claims", serializedClaims);
        return tag;
    }
}
