package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RoleAssignment(UUID player, String roleId, Optional<NationId> nation) {
    public RoleAssignment {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(nation, "nation");
        roleId = roleId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!roleId.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid role ID");
        }
    }
}
