package io.github.geopoliticalwar.domain.claim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Canonical lowercase dotted permission path. */
public record PermissionNode(String value) implements Comparable<PermissionNode> {
    public PermissionNode {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Invalid permission node: " + value);
        }
    }

    public boolean isAncestorOrSelfOf(PermissionNode other) {
        Objects.requireNonNull(other, "other");
        return other.value.equals(value) || other.value.startsWith(value + ".");
    }

    public List<PermissionNode> ancestorsIncludingSelf() {
        List<PermissionNode> result = new ArrayList<>();
        String current = value;
        while (true) {
            result.add(new PermissionNode(current));
            int separator = current.lastIndexOf('.');
            if (separator < 0) {
                break;
            }
            current = current.substring(0, separator);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public int compareTo(PermissionNode other) {
        return value.compareTo(other.value);
    }
}
