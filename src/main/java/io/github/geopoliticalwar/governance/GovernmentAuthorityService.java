package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Central regime-aware authority policy for nation administration. */
public final class GovernmentAuthorityService {
    private GovernmentAuthorityService() {
    }

    public static boolean can(ServerLevel level, Nation nation, ServerPlayer actor, AuthorityAction action) {
        if (actor.hasPermissions(2)) {
            return true;
        }
        if (!NationMembershipService.mayHoldOffice(level, actor.getUUID(), nation.id())) {
            return false;
        }
        if (nation.isLeader(actor.getUUID())) {
            return true;
        }
        if (action == AuthorityAction.CHANGE_REGIME || action == AuthorityAction.MANAGE_ELECTION) {
            return false;
        }
        if (action == AuthorityAction.MANAGE_POLICIES) {
            return nation.regime() == RegimeType.DICTATORSHIP && nation.isLeader(actor.getUUID());
        }
        if (action == AuthorityAction.MANAGE_ROLES && nation.regime() == RegimeType.DICTATORSHIP) {
            return false;
        }
        return RolePermissionService.has(level, actor, action.permission(), java.util.Optional.of(nation.id()))
                || action == AuthorityAction.MANAGE_ROLES
                && RolePermissionService.has(level, actor, "nation.membership.manage", java.util.Optional.of(nation.id()));
    }

    public static void require(ServerLevel level, Nation nation, ServerPlayer actor, AuthorityAction action) {
        if (!can(level, nation, actor, action)) {
            throw new IllegalArgumentException("Your government role cannot perform " + action.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
