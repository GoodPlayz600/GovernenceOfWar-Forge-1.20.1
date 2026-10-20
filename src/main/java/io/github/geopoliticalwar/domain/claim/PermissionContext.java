package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;
import java.util.Set;

public record PermissionContext(Set<PrincipalId> subjects) {
    public PermissionContext {
        Objects.requireNonNull(subjects, "subjects");
        subjects = Set.copyOf(subjects);
    }
}
