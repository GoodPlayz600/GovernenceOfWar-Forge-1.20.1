package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;

public record PermissionRule(
        PrincipalId subject,
        PermissionNode node,
        PermissionEffect effect
) {
    public PermissionRule {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(effect, "effect");
    }
}
