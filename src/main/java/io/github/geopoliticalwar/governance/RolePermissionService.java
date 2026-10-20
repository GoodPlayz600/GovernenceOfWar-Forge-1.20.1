package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Shared server-side evaluation of global and nation-scoped role permissions. */
public final class RolePermissionService {
    private RolePermissionService() {
    }

    public static boolean has(
            ServerLevel level,
            ServerPlayer player,
            String requested,
            Optional<NationId> expectedNation
    ) {
        String node = requested.trim().toLowerCase(java.util.Locale.ROOT);
        RolesSavedData roles = RolesSavedData.get(level);
        Optional<NationId> playerNation = NationMembershipService.nationFor(level, player.getUUID());
        if (expectedNation.isPresent()
                && !NationMembershipService.mayHoldOffice(level, player.getUUID(), expectedNation.get())) {
            return false;
        }
        return roles.assignments(player.getUUID()).stream()
                .filter(assignment -> assignment.nation().isEmpty()
                        || assignment.nation().equals(expectedNation)
                                && assignment.nation().equals(playerNation))
                .anyMatch(assignment -> grants(roles, assignment.roleId(), node));
    }

    public static Set<String> effectivePermissions(RolesSavedData roles, String roleId) {
        Set<String> permissions = new HashSet<>();
        collect(roles, roleId, permissions, new HashSet<>());
        return Set.copyOf(permissions);
    }

    private static boolean grants(RolesSavedData roles, String roleId, String requested) {
        return effectivePermissions(roles, roleId).stream().anyMatch(granted ->
                granted.equals(requested)
                        || requested.startsWith(granted + ".")
                        || granted.equals("claim.manage") && requested.startsWith("claim.")
                        || granted.equals("war.manage") && requested.startsWith("war."));
    }

    private static void collect(
            RolesSavedData roles,
            String roleId,
            Set<String> permissions,
            Set<String> visiting
    ) {
        if (!visiting.add(roleId)) {
            return;
        }
        RoleDefinition definition = roles.definition(roleId).orElse(null);
        if (definition == null) {
            visiting.remove(roleId);
            return;
        }
        permissions.addAll(definition.permissions());
        definition.parent().ifPresent(parent -> collect(roles, parent, permissions, visiting));
        visiting.remove(roleId);
    }
}
