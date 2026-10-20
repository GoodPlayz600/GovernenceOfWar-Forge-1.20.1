package io.github.geopoliticalwar.domain.claim;

import java.util.Objects;

/** A player, nation, role, or group identity used by the authorization layer. */
public record PrincipalId(String value) {
    public PrincipalId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Principal ID cannot be empty");
        }
        int separator = value.indexOf(':');
        if (separator > 0) {
            String prefix = value.substring(0, separator).toLowerCase(java.util.Locale.ROOT);
            String subject = value.substring(separator + 1).trim();
            if (subject.isEmpty()) {
                throw new IllegalArgumentException("Principal subject cannot be empty");
            }
            value = prefix + ":" + (prefix.equals("role") ? subject.toLowerCase(java.util.Locale.ROOT) : subject);
        }
    }
}
