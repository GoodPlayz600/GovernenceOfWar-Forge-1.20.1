package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxObligation;
import io.github.geopoliticalwar.domain.economy.TaxType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public final class TaxSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_taxes";
    private final Map<ClaimId, TaxObligation> obligations = new HashMap<>();
    private long nextCollectionAtMillis;

    public static TaxSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TaxSavedData::load,
                TaxSavedData::new,
                DATA_NAME
        );
    }

    public static TaxSavedData load(CompoundTag tag) {
        TaxSavedData data = new TaxSavedData();
        for (Tag obligationTag : tag.getList("obligations", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag obligation = (CompoundTag) obligationTag;
                TaxObligation value = new TaxObligation(
                        new ClaimId(obligation.getUUID("claim")),
                        new AccountId(obligation.getString("debtor")),
                        new CurrencyId(obligation.getString("currency")),
                        obligation.getLong("amount"),
                        obligation.getLong("assessed_at"),
                        obligation.getLong("due_at"),
                        obligation.getInt("attempts"),
                        obligation.contains("type") ? TaxType.valueOf(obligation.getString("type")) : TaxType.PROPERTY,
                        new AccountId(obligation.contains("recipient") ? obligation.getString("recipient") : "system:tax")
                );
                data.obligations.put(value.claim(), value);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid tax obligation", failure);
            }
        }
        data.nextCollectionAtMillis = tag.getLong("next_collection_at_millis");
        return data;
    }

    public TaxObligation get(ClaimId claim) {
        return obligations.get(claim);
    }

    public void set(TaxObligation obligation) {
        obligations.put(obligation.claim(), obligation);
        setDirty();
    }

    public void remove(ClaimId claim) {
        if (obligations.remove(claim) != null) {
            setDirty();
        }
    }

    public Map<ClaimId, TaxObligation> all() {
        return Map.copyOf(obligations);
    }

    public long nextCollectionAtMillis() {
        return nextCollectionAtMillis;
    }

    public void setNextCollectionAtMillis(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Tax collection timestamp cannot be negative");
        }
        nextCollectionAtMillis = value;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        obligations.values().stream()
                .sorted((first, second) -> first.claim().value().toString().compareTo(second.claim().value().toString()))
                .forEach(obligation -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("claim", obligation.claim().value());
                    value.putString("debtor", obligation.debtor().value());
                    value.putString("currency", obligation.currency().value());
                    value.putLong("amount", obligation.amount());
                    value.putLong("assessed_at", obligation.assessedAtMillis());
                    value.putLong("due_at", obligation.dueAtMillis());
                    value.putInt("attempts", obligation.attempts());
                    value.putString("type", obligation.type().name());
                    value.putString("recipient", obligation.recipient().value());
                    serialized.add(value);
                });
        tag.put("obligations", serialized);
        tag.putLong("next_collection_at_millis", nextCollectionAtMillis);
        return tag;
    }
}
