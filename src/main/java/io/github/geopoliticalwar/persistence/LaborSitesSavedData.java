package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.LaborSite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persisted labor-site output buffers. */
public final class LaborSitesSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_labor_sites";
    private final Map<UUID, LaborSite> sites = new HashMap<>();

    public static LaborSitesSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                LaborSitesSavedData::load,
                LaborSitesSavedData::new,
                DATA_NAME
        );
    }

    public static LaborSitesSavedData load(CompoundTag tag) {
        LaborSitesSavedData data = new LaborSitesSavedData();
        for (Tag raw : tag.getList("sites", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                LaborSite site = new LaborSite(
                        value.getUUID("id"),
                        new ClaimId(value.getUUID("claim")),
                        new NationId(value.getUUID("controller")),
                        value.getString("item"),
                        value.getLong("output_per_cycle"),
                        value.getLong("buffered"),
                        value.getLong("last_output_at")
                );
                data.sites.put(site.id(), site);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid labor site", failure);
            }
        }
        return data;
    }

    public Optional<LaborSite> get(UUID id) {
        return Optional.ofNullable(sites.get(id));
    }

    public Map<UUID, LaborSite> all() {
        return Map.copyOf(sites);
    }

    public void add(LaborSite site) {
        if (sites.values().stream().anyMatch(value -> value.claim().equals(site.claim())
                && value.itemId().equals(site.itemId()))) {
            throw new IllegalArgumentException("That claim already has a labor site for this output");
        }
        sites.put(site.id(), site);
        setDirty();
    }

    public void set(LaborSite site) {
        if (!sites.containsKey(site.id())) {
            throw new IllegalArgumentException("Unknown labor site");
        }
        sites.put(site.id(), site);
        setDirty();
    }

    public void remove(UUID id) {
        if (sites.remove(id) != null) {
            setDirty();
        }
    }

    public void removeForClaim(ClaimId claim) {
        if (sites.values().removeIf(site -> site.claim().equals(claim))) {
            setDirty();
        }
    }

    public void produceForClaim(ClaimId claim, long captiveCount, long now, long capacity) {
        if (captiveCount <= 0L) {
            return;
        }
        sites.values().stream()
                .filter(site -> site.claim().equals(claim))
                .toList()
                .forEach(site -> set(site.produce(
                        Math.multiplyExact(site.outputPerCycle(), captiveCount), capacity, now
                )));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        ListTag values = new ListTag();
        sites.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .forEach(site -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", site.id());
                    value.putUUID("claim", site.claim().value());
                    value.putUUID("controller", site.controller().value());
                    value.putString("item", site.itemId());
                    value.putLong("output_per_cycle", site.outputPerCycle());
                    value.putLong("buffered", site.bufferedOutput());
                    value.putLong("last_output_at", site.lastOutputAtMillis());
                    values.add(value);
                });
        tag.put("sites", values);
        return tag;
    }
}
