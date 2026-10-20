package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RolesSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_roles";
    private final Map<String, RoleDefinition> definitions = new HashMap<>();
    private final Map<UUID, Set<RoleAssignment>> assignments = new HashMap<>();

    public static RolesSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                RolesSavedData::load,
                RolesSavedData::new,
                DATA_NAME
        );
    }

    public static RolesSavedData load(CompoundTag tag) {
        RolesSavedData data = new RolesSavedData();
        for (Tag definitionTag : tag.getList("definitions", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag definition = (CompoundTag) definitionTag;
                Set<String> permissions = new HashSet<>();
                for (Tag permission : definition.getList("permissions", Tag.TAG_STRING)) {
                    permissions.add(permission.getAsString());
                }
                RoleDefinition value = new RoleDefinition(
                        definition.getString("id"),
                        definition.getString("display_name"),
                        definition.getString("badge"),
                        permissions,
                        definition.getLong("wage"),
                        definition.contains("parent")
                                ? Optional.of(definition.getString("parent"))
                                : Optional.empty()
                );
                data.definitions.put(value.id(), value);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid role definition", failure);
            }
        }
        for (Tag assignmentTag : tag.getList("assignments", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag assignment = (CompoundTag) assignmentTag;
                UUID player = assignment.getUUID("player");
                Optional<NationId> nation = assignment.hasUUID("nation")
                        ? Optional.of(new NationId(assignment.getUUID("nation")))
                        : Optional.empty();
                data.assignments.computeIfAbsent(player, ignored -> new HashSet<>()).add(
                        new RoleAssignment(player, assignment.getString("role"), nation)
                );
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted role assignment", failure);
            }
        }
        return data;
    }

    public void define(RoleDefinition definition) {
        definitions.put(definition.id(), definition);
        setDirty();
    }

    public Optional<RoleDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Map<String, RoleDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    public void assign(RoleAssignment assignment) {
        String roleId = assignment.roleId().trim().toLowerCase(java.util.Locale.ROOT);
        if (!definitions.containsKey(roleId)) {
            throw new IllegalArgumentException("Unknown role: " + roleId);
        }
        RoleAssignment normalized = new RoleAssignment(assignment.player(), roleId, assignment.nation());
        assignments.computeIfAbsent(normalized.player(), ignored -> new HashSet<>()).add(normalized);
        setDirty();
    }

    public Set<RoleAssignment> assignments(UUID player) {
        return Set.copyOf(assignments.getOrDefault(player, Set.of()));
    }

    public List<RoleAssignment> allAssignments() {
        return assignments.values().stream()
                .flatMap(Set::stream)
                .sorted((first, second) -> {
                    int playerOrder = first.player().compareTo(second.player());
                    if (playerOrder != 0) {
                        return playerOrder;
                    }
                    int roleOrder = first.roleId().compareTo(second.roleId());
                    if (roleOrder != 0) {
                        return roleOrder;
                    }
                    return first.nation().map(NationId::value).orElse(new UUID(0L, 0L))
                            .compareTo(second.nation().map(NationId::value).orElse(new UUID(0L, 0L)));
                })
                .toList();
    }

    public boolean unassign(UUID player, String roleId, Optional<NationId> nation) {
        Set<RoleAssignment> current = assignments.get(player);
        if (current == null) {
            return false;
        }
        String normalizedRole = roleId.trim().toLowerCase(java.util.Locale.ROOT);
        boolean changed = current.removeIf(assignment -> assignment.roleId().equals(normalizedRole)
                && assignment.nation().equals(nation));
        if (current.isEmpty()) {
            assignments.remove(player);
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void removeNationAssignments(UUID player, NationId nation) {
        Set<RoleAssignment> current = assignments.get(player);
        if (current == null) {
            return;
        }
        if (current.removeIf(assignment -> assignment.nation().filter(nation::equals).isPresent())) {
            if (current.isEmpty()) {
                assignments.remove(player);
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serializedDefinitions = new ListTag();
        definitions.values().stream().sorted((first, second) -> first.id().compareTo(second.id())).forEach(definition -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", definition.id());
            value.putString("display_name", definition.displayName());
            value.putString("badge", definition.badge());
            value.putLong("wage", definition.wagePerCycle());
            definition.parent().ifPresent(parent -> value.putString("parent", parent));
            ListTag permissions = new ListTag();
            definition.permissions().stream().sorted().forEach(permission -> permissions.add(net.minecraft.nbt.StringTag.valueOf(permission)));
            value.put("permissions", permissions);
            serializedDefinitions.add(value);
        });
        tag.put("definitions", serializedDefinitions);

        ListTag serializedAssignments = new ListTag();
        assignments.values().stream().flatMap(Set::stream).forEach(assignment -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("player", assignment.player());
            value.putString("role", assignment.roleId());
            assignment.nation().ifPresent(nation -> value.putUUID("nation", nation.value()));
            serializedAssignments.add(value);
        });
        tag.put("assignments", serializedAssignments);
        return tag;
    }
}
