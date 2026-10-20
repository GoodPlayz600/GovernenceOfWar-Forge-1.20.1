package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;
import java.util.Optional;

/** A server-owned volumetric plot or territory record. */
public record Claim(
        ClaimId id,
        String name,
        DimensionId dimension,
        BlockBox volume,
        PrincipalId owner,
        Optional<ClaimId> parent,
        ZoneType zone,
        PermissionPolicy permissions
) {
    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(permissions, "permissions");

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Claim name cannot be empty");
        }

        if (parent.isPresent() && parent.get().equals(id)) {
            throw new IllegalArgumentException("A claim cannot be its own parent");
        }
    }

    public static Claim root(
            ClaimId id,
            String name,
            DimensionId dimension,
            BlockBox volume,
            PrincipalId owner,
            ZoneType zone
    ) {
        return new Claim(id, name, dimension, volume, owner, Optional.empty(), zone, PermissionPolicy.empty());
    }

    public Claim withOwner(PrincipalId newOwner) {
        return new Claim(id, name, dimension, volume, newOwner, parent, zone, permissions);
    }

    public Claim withOwnerClearingPermissions(PrincipalId newOwner) {
        return new Claim(id, name, dimension, volume, newOwner, parent, zone, PermissionPolicy.empty());
    }

    public Claim withPermissions(PermissionPolicy newPermissions) {
        return new Claim(id, name, dimension, volume, owner, parent, zone, newPermissions);
    }
}
