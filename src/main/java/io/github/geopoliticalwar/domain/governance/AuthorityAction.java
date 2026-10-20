package io.github.geopoliticalwar.domain.governance;

/** Nation-scoped government actions whose authority differs by regime. */
public enum AuthorityAction {
    MANAGE_MEMBERSHIP("nation.membership.manage"),
    MANAGE_ROLES("nation.roles.manage"),
    MANAGE_CLAIMS("claim.manage"),
    MANAGE_TREASURY("economy.treasury"),
    MANAGE_TRADE("economy.trade"),
    MANAGE_WAR("war.manage"),
    MANAGE_POLICIES("nation.policies.manage"),
    CHANGE_REGIME("nation.regime.change"),
    MANAGE_ELECTION("nation.election.manage");

    private final String permission;

    AuthorityAction(String permission) {
        this.permission = permission;
    }

    public String permission() {
        return permission;
    }
}
