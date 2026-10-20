package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.BlockBox;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.claim.PermissionEffect;
import io.github.geopoliticalwar.domain.claim.PermissionNode;
import io.github.geopoliticalwar.domain.claim.PermissionPolicy;
import io.github.geopoliticalwar.domain.claim.PermissionRule;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.claim.ZoneType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Versioned NBT boundary for the Minecraft-independent claim model. */
public final class ClaimNbtCodec {
    private static final int CURRENT_SCHEMA = 1;

    private ClaimNbtCodec() {
    }

    public static CompoundTag write(Claim claim) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", CURRENT_SCHEMA);
        tag.putUUID("id", claim.id().value());
        tag.putString("name", claim.name());
        tag.putString("dimension", claim.dimension().value());
        tag.putString("owner", claim.owner().value());
        tag.putString("zone", claim.zone().name());
        claim.parent().ifPresent(parent -> tag.putUUID("parent", parent.value()));

        CompoundTag volume = new CompoundTag();
        volume.putLong("min_x", claim.volume().minX());
        volume.putLong("min_y", claim.volume().minY());
        volume.putLong("min_z", claim.volume().minZ());
        volume.putLong("max_x", claim.volume().maxXExclusive());
        volume.putLong("max_y", claim.volume().maxYExclusive());
        volume.putLong("max_z", claim.volume().maxZExclusive());
        tag.put("volume", volume);

        ListTag permissions = new ListTag();
        for (PermissionRule rule : claim.permissions().rules()) {
            CompoundTag permission = new CompoundTag();
            permission.putString("subject", rule.subject().value());
            permission.putString("node", rule.node().value());
            permission.putString("effect", rule.effect().name());
            permissions.add(permission);
        }
        tag.put("permissions", permissions);
        return tag;
    }

    public static Claim read(CompoundTag tag) {
        int schema = tag.contains("schema") ? tag.getInt("schema") : 0;
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported claim schema: " + schema);
        }

        CompoundTag volume = tag.getCompound("volume");
        BlockBox box = new BlockBox(
                volume.getLong("min_x"),
                volume.getLong("min_y"),
                volume.getLong("min_z"),
                volume.getLong("max_x"),
                volume.getLong("max_y"),
                volume.getLong("max_z")
        );

        Set<PermissionRule> rules = new HashSet<>();
        ListTag permissions = tag.getList("permissions", Tag.TAG_COMPOUND);
        for (Tag permissionTag : permissions) {
            CompoundTag permission = (CompoundTag) permissionTag;
            rules.add(new PermissionRule(
                    new PrincipalId(permission.getString("subject")),
                    new PermissionNode(permission.getString("node")),
                    PermissionEffect.valueOf(permission.getString("effect"))
            ));
        }

        Optional<ClaimId> parent = tag.hasUUID("parent")
                ? Optional.of(new ClaimId(tag.getUUID("parent")))
                : Optional.empty();

        return new Claim(
                new ClaimId(readUuid(tag, "id")),
                tag.contains("name") ? tag.getString("name") : "Unnamed Claim",
                new DimensionId(tag.getString("dimension")),
                box,
                new PrincipalId(tag.getString("owner")),
                parent,
                ZoneType.valueOf(tag.getString("zone")),
                new PermissionPolicy(rules)
        );
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("Missing claim UUID: " + key);
        }
        return tag.getUUID(key);
    }
}
