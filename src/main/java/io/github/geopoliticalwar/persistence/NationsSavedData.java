package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent nation and citizenship registry. */
public final class NationsSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_nations";
    private final Map<NationId, Nation> nations = new HashMap<>();

    public static NationsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                NationsSavedData::load,
                NationsSavedData::new,
                DATA_NAME
        );
    }

    public static NationsSavedData load(CompoundTag tag) {
        NationsSavedData data = new NationsSavedData();
        for (Tag nationTag : tag.getList("nations", Tag.TAG_COMPOUND)) {
            try {
                Nation nation = NationNbtCodec.read((CompoundTag) nationTag);
                data.nations.put(nation.id(), nation);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted nation", failure);
            }
        }
        return data;
    }

    public Nation create(String name, UUID leader, RegimeType regime, String color) {
        if (nationFor(leader).isPresent()) {
            throw new IllegalArgumentException("Player already belongs to a nation");
        }
        if (nations.values().stream().anyMatch(nation -> nation.name().equalsIgnoreCase(name.trim()))) {
            throw new IllegalArgumentException("Nation name is already in use");
        }
        long now = System.currentTimeMillis();
        long cycleMillis = Math.multiplyExact(ServerConfig.ELECTION_CYCLE_SECONDS.get(), 1_000L);
        Nation nation = Nation.create(
                NationId.random(),
                name,
                leader,
                regime,
                color,
                now,
                cycleMillis
        );
        nations.put(nation.id(), nation);
        setDirty();
        return nation;
    }

    public void replace(Nation nation) {
        nations.put(nation.id(), nation);
        setDirty();
    }

    public Optional<Nation> get(NationId id) {
        return Optional.ofNullable(nations.get(id));
    }

    public Optional<Nation> nationFor(UUID player) {
        return nations.values().stream().filter(nation -> nation.isCitizen(player)).findFirst();
    }

    public void join(NationId id, UUID player) {
        Nation nation = nations.get(id);
        if (nation == null) {
            throw new IllegalArgumentException("Unknown nation");
        }
        if (nationFor(player).isPresent()) {
            throw new IllegalArgumentException("Player already belongs to a nation");
        }
        nations.put(id, nation.addCitizen(player));
        setDirty();
    }

    public void leave(ServerLevel level, UUID player) {
        Nation nation = nationFor(player).orElseThrow(() -> new IllegalArgumentException("Player is not in a nation"));
        nations.put(nation.id(), nation.removeCitizen(player));
        RolesSavedData.get(level).removeNationAssignments(player, nation.id());
        setDirty();
    }

    public void transferCitizen(UUID player, NationId destination) {
        Nation current = nationFor(player).orElse(null);
        Nation target = nations.get(destination);
        if (target == null) {
            throw new IllegalArgumentException("Unknown destination nation");
        }
        if (current != null && current.id().equals(destination)) {
            return;
        }
        if (current != null) {
            removeCitizenForOccupation(player);
        }
        if (!target.isCitizen(player)) {
            nations.put(destination, target.addCitizen(player));
        }
        setDirty();
    }

    public void removeCitizenForOccupation(UUID player) {
        Nation current = nationFor(player).orElse(null);
        if (current == null) {
            return;
        }
        if (current.isLeader(player)) {
            UUID successor = current.citizens().stream()
                    .filter(candidate -> !candidate.equals(player))
                    .findFirst()
                    .orElse(null);
            if (successor == null) {
                return;
            }
            nations.put(current.id(), current.withLeader(successor, current.nextElectionAtMillis()).removeCitizen(player));
            setDirty();
            return;
        }
        nations.put(current.id(), current.removeCitizen(player));
        setDirty();
    }

    public Map<NationId, Nation> all() {
        return Map.copyOf(nations);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serialized = new ListTag();
        nations.values().stream()
                .sorted((first, second) -> first.id().value().toString().compareTo(second.id().value().toString()))
                .map(NationNbtCodec::write)
                .forEach(serialized::add);
        tag.put("nations", serialized);
        return tag;
    }
}
