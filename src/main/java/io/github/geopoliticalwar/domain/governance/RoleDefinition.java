package io.github.geopoliticalwar.domain.governance;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record RoleDefinition(
        String id,
        String displayName,
        String badge,
        Set<String> permissions,
        long wagePerCycle,
        Optional<String> parent
) {
    public RoleDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(parent, "parent");
        id = id.trim().toLowerCase(java.util.Locale.ROOT);
        displayName = displayName.trim();
        badge = badge.trim();
        if (!id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid role ID");
        }
        if (displayName.isEmpty() || wagePerCycle < 0) {
            throw new IllegalArgumentException("Invalid role definition");
        }
        permissions = permissions.stream()
                .map(permission -> permission.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(permission -> !permission.isEmpty())
                .peek(permission -> {
                    if (!permission.matches("[a-z0-9_.:-]+")) {
                        throw new IllegalArgumentException("Invalid role permission: " + permission);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
        parent = parent.map(value -> value.trim().toLowerCase(java.util.Locale.ROOT));
        if (parent.isPresent() && parent.get().equals(id)) {
            throw new IllegalArgumentException("A role cannot be its own parent");
        }
    }
}
