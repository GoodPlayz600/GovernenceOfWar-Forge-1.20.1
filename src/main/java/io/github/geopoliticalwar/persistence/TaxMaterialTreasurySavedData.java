package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Server-authoritative stockpile for physical tax tender. */
public final class TaxMaterialTreasurySavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_tax_material_treasury";
    private final Map<String, Long> items = new HashMap<>();

    public static TaxMaterialTreasurySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TaxMaterialTreasurySavedData::load,
                TaxMaterialTreasurySavedData::new,
                DATA_NAME
        );
    }

    public static TaxMaterialTreasurySavedData load(CompoundTag tag) {
        TaxMaterialTreasurySavedData data = new TaxMaterialTreasurySavedData();
        for (Tag raw : tag.getList("items", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                long amount = value.getLong("amount");
                if (amount < 0L || value.getString("item").isBlank()) {
                    throw new IllegalArgumentException("Invalid tax material balance");
                }
                data.items.put(value.getString("item"), amount);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid tax material balance", failure);
            }
        }
        return data;
    }

    public long amount(String itemId) {
        return items.getOrDefault(itemId, 0L);
    }

    public long total() {
        return items.values().stream().mapToLong(Long::longValue).sum();
    }

    public void deposit(String itemId, long amount, long capacity) {
        if (amount <= 0L || itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Invalid tax material deposit");
        }
        if (total() > capacity - amount) {
            throw new IllegalArgumentException("Tax material treasury is full");
        }
        long updated = Math.addExact(amount(itemId), amount);
        items.put(itemId, updated);
        setDirty();
    }

    public long withdraw(String itemId, long amount) {
        if (amount <= 0L || amount(itemId) < amount) {
            throw new IllegalArgumentException("Insufficient tax material treasury balance");
        }
        long updated = amount(itemId) - amount;
        if (updated == 0L) {
            items.remove(itemId);
        } else {
            items.put(itemId, updated);
        }
        setDirty();
        return amount;
    }

    public Map<String, Long> all() {
        return Map.copyOf(items);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        items.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putString("item", entry.getKey());
            value.putLong("amount", entry.getValue());
            values.add(value);
        });
        tag.put("items", values);
        return tag;
    }
}
