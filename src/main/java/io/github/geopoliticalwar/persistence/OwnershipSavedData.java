package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.economy.OwnershipChangeReason;
import io.github.geopoliticalwar.domain.economy.OwnershipRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded ownership history for estate and conquest audit views. */
public final class OwnershipSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_ownership";
    private static final int MAX_HISTORY_PER_CLAIM = 64;
    private final Map<ClaimId, List<OwnershipRecord>> history = new HashMap<>();

    public static OwnershipSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                OwnershipSavedData::load,
                OwnershipSavedData::new,
                DATA_NAME
        );
    }

    public static OwnershipSavedData load(CompoundTag tag) {
        OwnershipSavedData data = new OwnershipSavedData();
        for (Tag raw : tag.getList("history", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                OwnershipRecord record = new OwnershipRecord(
                        value.getUUID("id"),
                        new ClaimId(value.getUUID("claim")),
                        new PrincipalId(value.getString("previous")),
                        new PrincipalId(value.getString("new")),
                        OwnershipChangeReason.valueOf(value.getString("reason")),
                        value.getUUID("actor"),
                        value.getLong("changed_at")
                );
                data.history.computeIfAbsent(record.claim(), ignored -> new ArrayList<>()).add(record);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid ownership history record", failure);
            }
        }
        return data;
    }

    public void add(OwnershipRecord record) {
        List<OwnershipRecord> records = history.computeIfAbsent(record.claim(), ignored -> new ArrayList<>());
        records.add(record);
        while (records.size() > MAX_HISTORY_PER_CLAIM) {
            records.remove(0);
        }
        setDirty();
    }

    public List<OwnershipRecord> forClaim(ClaimId claim) {
        return List.copyOf(history.getOrDefault(claim, List.of()));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        history.values().stream().flatMap(List::stream)
                .sorted((first, second) -> first.changedAtMillis() == second.changedAtMillis()
                        ? first.id().toString().compareTo(second.id().toString())
                        : Long.compare(first.changedAtMillis(), second.changedAtMillis()))
                .forEach(record -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", record.id());
                    value.putUUID("claim", record.claim().value());
                    value.putString("previous", record.previousOwner().value());
                    value.putString("new", record.newOwner().value());
                    value.putString("reason", record.reason().name());
                    value.putUUID("actor", record.actor());
                    value.putLong("changed_at", record.changedAtMillis());
                    values.add(value);
                });
        tag.put("history", values);
        return tag;
    }
}
