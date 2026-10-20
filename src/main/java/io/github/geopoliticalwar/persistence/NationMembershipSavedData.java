package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.AdmissionPolicy;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.NationMembership;
import io.github.geopoliticalwar.domain.governance.NationRequest;
import io.github.geopoliticalwar.domain.governance.NationRequestState;
import io.github.geopoliticalwar.domain.governance.NationRequestType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent admission, residency, and membership-request state. */
public final class NationMembershipSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_memberships";
    private final Map<NationId, AdmissionPolicy> policies = new HashMap<>();
    private final Map<UUID, NationMembership> memberships = new HashMap<>();
    private final Map<UUID, NationRequest> requests = new HashMap<>();

    public static NationMembershipSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                NationMembershipSavedData::load,
                NationMembershipSavedData::new,
                DATA_NAME
        );
    }

    public static NationMembershipSavedData load(CompoundTag tag) {
        NationMembershipSavedData data = new NationMembershipSavedData();
        for (Tag raw : tag.getList("policies", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                data.policies.put(
                        new NationId(value.getUUID("nation")),
                        AdmissionPolicy.valueOf(value.getString("policy"))
                );
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid nation admission policy", failure);
            }
        }
        for (Tag raw : tag.getList("memberships", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                NationMembership membership = new NationMembership(
                        new NationId(value.getUUID("nation")),
                        value.getUUID("player"),
                        MembershipStatus.valueOf(value.getString("status")),
                        value.getLong("granted_at"),
                        value.getUUID("granted_by")
                );
                data.memberships.put(membership.player(), membership);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid nation membership", failure);
            }
        }
        for (Tag raw : tag.getList("requests", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                NationRequest request = new NationRequest(
                        value.getUUID("id"),
                        new NationId(value.getUUID("nation")),
                        value.getUUID("player"),
                        NationRequestType.valueOf(value.getString("type")),
                        MembershipStatus.valueOf(value.getString("requested_status")),
                        NationRequestState.valueOf(value.getString("state")),
                        value.getLong("created_at"),
                        value.getLong("expires_at"),
                        value.getUUID("actor")
                );
                data.requests.put(request.id(), request);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid nation request", failure);
            }
        }
        return data;
    }

    public AdmissionPolicy policy(NationId nation) {
        return policies.getOrDefault(nation, AdmissionPolicy.OPEN);
    }

    public void setPolicy(NationId nation, AdmissionPolicy policy) {
        policies.put(nation, policy);
        setDirty();
    }

    public Optional<NationMembership> membership(UUID player) {
        return Optional.ofNullable(memberships.get(player));
    }

    public Map<UUID, NationMembership> memberships() {
        return Map.copyOf(memberships);
    }

    public Set<NationMembership> membershipsFor(NationId nation) {
        return memberships.values().stream()
                .filter(membership -> membership.nation().equals(nation))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void setMembership(NationMembership membership) {
        memberships.put(membership.player(), membership);
        setDirty();
    }

    public void removeMembership(UUID player) {
        if (memberships.remove(player) != null) {
            setDirty();
        }
    }

    public Optional<NationRequest> request(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    public Map<UUID, NationRequest> requests() {
        return Map.copyOf(requests);
    }

    public void addRequest(NationRequest request) {
        if (requests.values().stream().anyMatch(existing -> existing.pending(request.createdAtMillis())
                && existing.nation().equals(request.nation())
                && existing.player().equals(request.player())
                && existing.type() == request.type())) {
            throw new IllegalArgumentException("An equivalent request is already pending");
        }
        requests.put(request.id(), request);
        setDirty();
    }

    public void replaceRequest(NationRequest request) {
        if (!requests.containsKey(request.id())) {
            throw new IllegalArgumentException("Unknown nation request");
        }
        requests.put(request.id(), request);
        setDirty();
    }

    public void expireRequests(long now) {
        boolean changed = false;
        for (Map.Entry<UUID, NationRequest> entry : requests.entrySet()) {
            NationRequest request = entry.getValue();
            if (request.state() == NationRequestState.PENDING && now >= request.expiresAtMillis()) {
                entry.setValue(request.withState(NationRequestState.EXPIRED));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag policiesTag = new ListTag();
        policies.entrySet().stream()
                .sorted((first, second) -> first.getKey().value().toString().compareTo(second.getKey().value().toString()))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("nation", entry.getKey().value());
                    value.putString("policy", entry.getValue().name());
                    policiesTag.add(value);
                });
        tag.put("policies", policiesTag);

        ListTag membershipsTag = new ListTag();
        memberships.values().stream()
                .sorted((first, second) -> first.player().compareTo(second.player()))
                .forEach(membership -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("nation", membership.nation().value());
                    value.putUUID("player", membership.player());
                    value.putString("status", membership.status().name());
                    value.putLong("granted_at", membership.grantedAtMillis());
                    value.putUUID("granted_by", membership.grantedBy());
                    membershipsTag.add(value);
                });
        tag.put("memberships", membershipsTag);

        ListTag requestsTag = new ListTag();
        requests.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .forEach(request -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", request.id());
                    value.putUUID("nation", request.nation().value());
                    value.putUUID("player", request.player());
                    value.putString("type", request.type().name());
                    value.putString("requested_status", request.requestedStatus().name());
                    value.putString("state", request.state().name());
                    value.putLong("created_at", request.createdAtMillis());
                    value.putLong("expires_at", request.expiresAtMillis());
                    value.putUUID("actor", request.actor());
                    requestsTag.add(value);
                });
        tag.put("requests", requestsTag);
        return tag;
    }
}
