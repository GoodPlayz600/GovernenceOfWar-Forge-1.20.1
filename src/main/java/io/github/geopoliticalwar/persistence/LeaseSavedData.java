package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.Lease;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LeaseSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_leases";
    private final Map<UUID, Lease> leases = new HashMap<>();

    public static LeaseSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(LeaseSavedData::load, LeaseSavedData::new, DATA_NAME);
    }

    public static LeaseSavedData load(CompoundTag tag) {
        LeaseSavedData data = new LeaseSavedData();
        for (Tag raw : tag.getList("leases", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                Lease lease = new Lease(
                        value.getUUID("id"),
                        new ClaimId(value.getUUID("claim")),
                        new AccountId(value.getString("landlord")),
                        new AccountId(value.getString("tenant")),
                        new CurrencyId(value.getString("currency")),
                        value.getLong("rent"),
                        value.getLong("next_due"),
                        value.getInt("missed")
                );
                data.leases.put(lease.id(), lease);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid lease", failure);
            }
        }
        return data;
    }

    public void add(Lease lease) {
        if (leases.values().stream().anyMatch(existing -> existing.claim().equals(lease.claim()))) {
            throw new IllegalArgumentException("Claim already has a lease");
        }
        leases.put(lease.id(), lease);
        setDirty();
    }

    public void replace(Lease lease) {
        if (!leases.containsKey(lease.id())) {
            throw new IllegalArgumentException("Unknown lease");
        }
        leases.put(lease.id(), lease);
        setDirty();
    }

    public Optional<Lease> get(UUID id) {
        return Optional.ofNullable(leases.get(id));
    }

    public Optional<Lease> forClaim(ClaimId claim) {
        return leases.values().stream().filter(lease -> lease.claim().equals(claim)).findFirst();
    }

    public boolean remove(UUID id) {
        if (leases.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void removeForClaim(ClaimId claim) {
        if (leases.values().removeIf(lease -> lease.claim().equals(claim))) {
            setDirty();
        }
    }

    public Map<UUID, Lease> all() {
        return Map.copyOf(leases);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag values = new ListTag();
        leases.values().stream().sorted((first, second) -> first.id().toString().compareTo(second.id().toString())).forEach(lease -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", lease.id());
            value.putUUID("claim", lease.claim().value());
            value.putString("landlord", lease.landlord().value());
            value.putString("tenant", lease.tenant().value());
            value.putString("currency", lease.currency().value());
            value.putLong("rent", lease.rentPerCycle());
            value.putLong("next_due", lease.nextDueAtMillis());
            value.putInt("missed", lease.missedPayments());
            values.add(value);
        });
        tag.put("leases", values);
        return tag;
    }
}
