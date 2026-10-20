package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.EstateListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class EstateSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_estates";
    private final Map<UUID, EstateListing> listings = new HashMap<>();

    public static EstateSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                EstateSavedData::load,
                EstateSavedData::new,
                DATA_NAME
        );
    }

    public static EstateSavedData load(CompoundTag tag) {
        EstateSavedData data = new EstateSavedData();
        for (Tag listingTag : tag.getList("listings", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag listing = (CompoundTag) listingTag;
                EstateListing value = new EstateListing(
                        listing.getUUID("id"),
                        new ClaimId(listing.getUUID("claim")),
                        new AccountId(listing.getString("seller")),
                        listing.getLong("sale_price"),
                        listing.getLong("rent_per_cycle"),
                        listing.getLong("created_at")
                );
                data.listings.put(value.id(), value);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid estate listing", failure);
            }
        }
        return data;
    }

    public void add(EstateListing listing) {
        if (listings.containsKey(listing.id())) {
            throw new IllegalArgumentException("Estate listing already exists");
        }
        if (listings.values().stream().anyMatch(existing -> existing.claim().equals(listing.claim()))) {
            throw new IllegalArgumentException("Claim is already listed");
        }
        listings.put(listing.id(), listing);
        setDirty();
    }

    public Optional<EstateListing> get(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    public boolean remove(UUID id) {
        if (listings.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void removeForClaim(ClaimId claim) {
        listings.entrySet().removeIf(entry -> entry.getValue().claim().equals(claim));
        setDirty();
    }

    public Map<UUID, EstateListing> all() {
        return Map.copyOf(listings);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        listings.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .forEach(listing -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", listing.id());
                    value.putUUID("claim", listing.claim().value());
                    value.putString("seller", listing.seller().value());
                    value.putLong("sale_price", listing.salePrice());
                    value.putLong("rent_per_cycle", listing.rentPerCycle());
                    value.putLong("created_at", listing.createdAtMillis());
                    serialized.add(value);
                });
        tag.put("listings", serialized);
        return tag;
    }
}
