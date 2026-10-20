package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NationNbtCodec {
    private NationNbtCodec() {
    }

    public static CompoundTag write(Nation nation) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putUUID("id", nation.id().value());
        tag.putString("name", nation.name());
        tag.putUUID("leader", nation.leader());
        tag.putString("regime", nation.regime().name());
        tag.putString("color", nation.color());
        tag.putLong("created_at", nation.createdAtMillis());
        tag.putLong("next_election_at", nation.nextElectionAtMillis());

        ListTag citizens = new ListTag();
        nation.citizens().stream()
                .sorted()
                .forEach(player -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("player", player);
                    citizens.add(entry);
                });
        tag.put("citizens", citizens);

        ListTag roles = new ListTag();
        nation.roles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag roleEntry = new CompoundTag();
                    roleEntry.putUUID("player", entry.getKey());
                    ListTag roleNames = new ListTag();
                    entry.getValue().stream().sorted().forEach(role -> roleNames.add(net.minecraft.nbt.StringTag.valueOf(role)));
                    roleEntry.put("roles", roleNames);
                    roles.add(roleEntry);
                });
        tag.put("roles", roles);
        return tag;
    }

    public static Nation read(CompoundTag tag) {
        int schema = tag.contains("schema") ? tag.getInt("schema") : 0;
        if (schema > 1) {
            throw new IllegalArgumentException("Unsupported nation schema: " + schema);
        }
        Set<UUID> citizens = new HashSet<>();
        for (Tag citizenTag : tag.getList("citizens", Tag.TAG_COMPOUND)) {
            citizens.add(((CompoundTag) citizenTag).getUUID("player"));
        }

        Map<UUID, Set<String>> roles = new HashMap<>();
        for (Tag roleTag : tag.getList("roles", Tag.TAG_COMPOUND)) {
            CompoundTag roleEntry = (CompoundTag) roleTag;
            Set<String> roleNames = new HashSet<>();
            for (Tag roleName : roleEntry.getList("roles", Tag.TAG_STRING)) {
                roleNames.add(roleName.getAsString());
            }
            roles.put(roleEntry.getUUID("player"), Set.copyOf(roleNames));
        }

        return new Nation(
                new NationId(tag.getUUID("id")),
                tag.getString("name"),
                tag.getUUID("leader"),
                RegimeType.valueOf(tag.getString("regime")),
                tag.getString("color"),
                citizens,
                roles,
                tag.getLong("created_at"),
                tag.getLong("next_election_at")
        );
    }
}
