package io.github.geopoliticalwar.claims;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.claim.PermissionContext;
import io.github.geopoliticalwar.domain.claim.PermissionDecision;
import io.github.geopoliticalwar.domain.claim.PermissionNode;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.governance.RolePermissionService;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import io.github.geopoliticalwar.domain.war.OccupationRights;
import io.github.geopoliticalwar.war.WarStateService;
import io.github.geopoliticalwar.war.WarAccessService;
import io.github.geopoliticalwar.war.CaptiveService;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/** Server-side protection decisions for the current peace-state foundation. */
public final class ClaimAccessService {
    private ClaimAccessService() {
    }

    public static boolean canModify(ServerPlayer player, ServerLevel level, BlockPos position) {
        return canBreak(player, level, position);
    }

    public static boolean canBreak(ServerPlayer player, ServerLevel level, BlockPos position) {
        if (!WarStateService.claimsEnforceProtection(level)) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }

        ClaimsSavedData claims = ClaimsSavedData.get(level);
        Set<ClaimId> containing = claims.containing(
                dimension(level),
                position.getX(),
                position.getY(),
                position.getZ()
        );
        if (containing.isEmpty()) {
            return true;
        }

        return containing.stream()
                .map(claims::get)
                .flatMap(java.util.Optional::stream)
                .allMatch(claim -> canModifyClaim(player, level, claim, true));
    }

    public static boolean canPlace(ServerPlayer player, ServerLevel level, BlockPos position) {
        if (!WarStateService.claimsEnforceProtection(level)) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        Set<ClaimId> containing = claims.containing(
                dimension(level), position.getX(), position.getY(), position.getZ()
        );
        return containing.isEmpty() || containing.stream()
                .map(claims::get)
                .flatMap(java.util.Optional::stream)
                .allMatch(claim -> canModifyClaim(player, level, claim, false));
    }

    public static boolean isProtected(ServerLevel level, BlockPos position) {
        if (!WarStateService.claimsEnforceProtection(level)) {
            return false;
        }
        return !ClaimsSavedData.get(level).containing(
                dimension(level),
                position.getX(),
                position.getY(),
                position.getZ()
        ).isEmpty();
    }

    public static boolean canInteract(ServerPlayer player, ServerLevel level, BlockPos position) {
        if (!WarStateService.claimsEnforceProtection(level) || player.hasPermissions(2)) {
            return true;
        }
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        if (level.getBlockState(position).is(ModContent.SIEGE_ANCHOR.get())) {
            Set<ClaimId> siegeClaims = claims.containing(
                    dimension(level), position.getX(), position.getY(), position.getZ()
            );
            if (!siegeClaims.isEmpty() && siegeClaims.stream()
                    .map(claims::get)
                    .flatMap(java.util.Optional::stream)
                    .allMatch(claim -> WarAccessService.canDamageClaim(player, level, claim))) {
                return true;
            }
        }
        return claims.containing(dimension(level), position.getX(), position.getY(), position.getZ()).stream()
                .map(claims::get)
                .flatMap(java.util.Optional::stream)
                .allMatch(claim -> canUseClaim(player, level, claim));
    }

    public static boolean canPlaceSiegeAnchor(ServerPlayer player, ServerLevel level, BlockPos position) {
        Set<ClaimId> containing = ClaimsSavedData.get(level).containing(
                dimension(level), position.getX(), position.getY(), position.getZ()
        );
        return !containing.isEmpty() && containing.stream()
                .map(ClaimsSavedData.get(level)::get)
                .flatMap(java.util.Optional::stream)
                .allMatch(claim -> WarAccessService.canDamageClaim(player, level, claim));
    }

    public static PrincipalId playerPrincipal(ServerPlayer player) {
        return new PrincipalId("player:" + player.getUUID());
    }

    public static DimensionId dimension(ServerLevel level) {
        return new DimensionId(level.dimension().location().toString());
    }

    public static boolean canManageClaim(ServerPlayer player, ServerLevel level, Claim claim) {
        if (player.hasPermissions(2)) {
            return true;
        }
        OccupationRecord occupation = OccupationsSavedData.get(level).get(claim.id()).orElse(null);
        if (occupation != null && occupation.settledAtMillis().isEmpty()) {
            return NationMembershipService.nationFor(level, player.getUUID())
                    .filter(occupation.occupyingNation()::equals)
                    .flatMap(nationId -> NationsSavedData.get(level).get(nationId))
                    .map(nation -> nation.isLeader(player.getUUID())
                            || RolePermissionService.has(level, player, "claim.manage", java.util.Optional.of(occupation.occupyingNation())))
                    .orElse(false);
        }
        if (claim.owner().equals(playerPrincipal(player))) {
            return true;
        }
        if (claimPermissionAllowed(player, level, claim, "claim.manage")) {
            return true;
        }
        if (RolePermissionService.has(level, player, "claim.manage", java.util.Optional.empty())) {
            return true;
        }
        return nationOwner(claim).map(nationId ->
                NationMembershipService.nationFor(level, player.getUUID())
                        .filter(nationId::equals)
                        .flatMap(memberNation -> NationsSavedData.get(level).get(memberNation))
                        .map(nation -> nation.isLeader(player.getUUID())
                                || RolePermissionService.has(level, player, "claim.manage", java.util.Optional.of(nationId)))
                        .orElse(false)
        ).orElse(false);
    }

    private static boolean canModifyClaim(
            ServerPlayer player,
            ServerLevel level,
            Claim claim,
            boolean breaking
    ) {
        if (CaptiveService.isActiveAt(level, player.getUUID(), claim.id())) {
            return true;
        }
        java.util.Optional<Boolean> occupation = occupationAllows(player, level, claim, true, breaking);
        if (occupation.isPresent()) {
            return occupation.get();
        }
        if (claim.owner().equals(playerPrincipal(player))) {
            return true;
        }
        String permission = breaking ? "claim.build.break" : "claim.build.place";
        if (claimPermissionAllowed(player, level, claim, permission)) {
            return true;
        }
        if (RolePermissionService.has(level, player, permission, java.util.Optional.empty())) {
            return true;
        }
        if (breaking && WarAccessService.canDamageClaim(player, level, claim)) {
            return true;
        }
        return nationOwner(claim).map(nationId ->
                NationMembershipService.nationFor(level, player.getUUID())
                        .filter(nationId::equals)
                        .flatMap(memberNation -> NationsSavedData.get(level).get(memberNation))
                        .map(nation -> nation.isLeader(player.getUUID())
                                || RolePermissionService.has(level, player, permission, java.util.Optional.of(nationId)))
                        .orElse(false)
        ).orElse(false);
    }

    private static boolean canUseClaim(ServerPlayer player, ServerLevel level, Claim claim) {
        if (CaptiveService.isActiveAt(level, player.getUUID(), claim.id())) {
            return true;
        }
        java.util.Optional<Boolean> occupation = occupationAllows(player, level, claim, false, false);
        if (occupation.isPresent()) {
            return occupation.get();
        }
        if (claim.owner().equals(playerPrincipal(player))
                || claimPermissionAllowed(player, level, claim, "claim.container.use")
                || activeLeaseAllows(player, level, claim)
                || RolePermissionService.has(level, player, "claim.container.use", java.util.Optional.empty())) {
            return true;
        }
        return nationOwner(claim).map(nationId ->
                NationMembershipService.nationFor(level, player.getUUID())
                        .filter(nationId::equals)
                        .flatMap(memberNation -> NationsSavedData.get(level).get(memberNation))
                        .map(nation -> nation.isLeader(player.getUUID())
                                || RolePermissionService.has(level, player, "claim.container.use", java.util.Optional.of(nationId)))
                        .orElse(false)
        ).orElse(false);
    }

    private static boolean activeLeaseAllows(ServerPlayer player, ServerLevel level, Claim claim) {
        AccountId tenant = new AccountId("player:" + player.getUUID());
        return LeaseSavedData.get(level).forClaim(claim.id())
                .map(lease -> lease.tenant().equals(tenant))
                .orElse(false);
    }

    private static java.util.Optional<Boolean> occupationAllows(
            ServerPlayer player,
            ServerLevel level,
            Claim claim,
            boolean building,
            boolean breaking
    ) {
        OccupationRecord occupation = OccupationsSavedData.get(level).get(claim.id()).orElse(null);
        if (occupation == null || occupation.settledAtMillis().isPresent()) {
            return java.util.Optional.empty();
        }
        java.util.Optional<NationId> actorNation = NationMembershipService.nationFor(level, player.getUUID());
        boolean occupier = actorNation.filter(occupation.occupyingNation()::equals).isPresent();
        boolean formerOwner = occupation.formerPopulation().contains(player.getUUID())
                || (occupation.formerPopulation().isEmpty()
                && claimOwnerMatchesPlayer(occupation.formerOwner(), player, actorNation));
        if (occupier) {
            return java.util.Optional.of(true);
        }
        if (!formerOwner) {
            return java.util.Optional.of(false);
        }
        if (building || breaking) {
            return java.util.Optional.of(occupation.rights().allowsBuild());
        }
        return java.util.Optional.of(occupation.rights().allowsContainerUse());
    }

    private static boolean claimOwnerMatchesPlayer(
            PrincipalId formerOwner,
            ServerPlayer player,
            java.util.Optional<NationId> actorNation
    ) {
        if (formerOwner.equals(playerPrincipal(player))) {
            return true;
        }
        String value = formerOwner.value();
        if (!value.startsWith("nation:")) {
            return false;
        }
        try {
            return actorNation.filter(new NationId(java.util.UUID.fromString(value.substring("nation:".length())))::equals).isPresent();
        } catch (IllegalArgumentException invalidNation) {
            return false;
        }
    }

    private static boolean claimPermissionAllowed(
            ServerPlayer player,
            ServerLevel level,
            Claim claim,
            String requested
    ) {
        return claim.permissions().check(
                new PermissionContext(permissionSubjects(player, level)),
                new PermissionNode(requested)
        ) == PermissionDecision.ALLOW;
    }

    private static Set<PrincipalId> permissionSubjects(ServerPlayer player, ServerLevel level) {
        Set<PrincipalId> subjects = new HashSet<>();
        subjects.add(playerPrincipal(player));
        NationMembershipService.nationFor(level, player.getUUID()).ifPresent(nation -> {
            subjects.add(new PrincipalId("nation:" + nation.value()));
        });
        RolesSavedData roles = RolesSavedData.get(level);
        roles.assignments(player.getUUID()).stream()
                .filter(assignment -> assignment.nation().isEmpty()
                        || NationMembershipService.nationFor(level, player.getUUID())
                        .map(nation -> assignment.nation().filter(nation::equals).isPresent())
                        .orElse(false))
                .forEach(assignment -> subjects.add(new PrincipalId("role:" + assignment.roleId())));
        return subjects;
    }

    private static java.util.Optional<NationId> nationOwner(Claim claim) {
        String value = claim.owner().value();
        if (!value.startsWith("nation:")) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new NationId(
                    java.util.UUID.fromString(value.substring("nation:".length()))
            ));
        } catch (IllegalArgumentException invalidOwner) {
            return java.util.Optional.empty();
        }
    }
}
