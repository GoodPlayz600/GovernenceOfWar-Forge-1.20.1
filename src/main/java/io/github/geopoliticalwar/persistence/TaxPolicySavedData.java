package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Persisted per-claim property-tax policy overrides. */
public final class TaxPolicySavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_tax_policies";
    private final Map<ClaimId, TaxPolicy> policies = new HashMap<>();

    public static TaxPolicySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TaxPolicySavedData::load,
                TaxPolicySavedData::new,
                DATA_NAME
        );
    }

    public static TaxPolicySavedData load(CompoundTag tag) {
        TaxPolicySavedData data = new TaxPolicySavedData();
        for (Tag raw : tag.getList("policies", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                TaxMode mode = TaxMode.valueOf(value.getString("mode"));
                TaxPolicy policy = new TaxPolicy(
                        mode,
                        value.getLong("units_per_block"),
                        new CurrencyId(value.getString("currency")),
                        value.getString("item")
                );
                data.policies.put(new ClaimId(value.getUUID("claim")), policy);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid tax policy", failure);
            }
        }
        return data;
    }

    public Optional<TaxPolicy> get(ClaimId claim) {
        return Optional.ofNullable(policies.get(claim));
    }

    public void set(ClaimId claim, TaxPolicy policy) {
        policies.put(claim, policy);
        setDirty();
    }

    public void remove(ClaimId claim) {
        if (policies.remove(claim) != null) {
            setDirty();
        }
    }

    public Map<ClaimId, TaxPolicy> all() {
        return Map.copyOf(policies);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        policies.entrySet().stream()
                .sorted((first, second) -> first.getKey().value().toString()
                        .compareTo(second.getKey().value().toString()))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("claim", entry.getKey().value());
                    value.putString("mode", entry.getValue().mode().name());
                    value.putLong("units_per_block", entry.getValue().unitsPerBlock());
                    value.putString("currency", entry.getValue().currency().value());
                    value.putString("item", entry.getValue().itemId());
                    values.add(value);
                });
        tag.put("policies", values);
        return tag;
    }
}
