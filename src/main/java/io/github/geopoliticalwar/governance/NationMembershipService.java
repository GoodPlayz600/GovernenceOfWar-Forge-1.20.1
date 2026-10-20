package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.AdmissionPolicy;
import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.NationMembership;
import io.github.geopoliticalwar.domain.governance.NationRequest;
import io.github.geopoliticalwar.domain.governance.NationRequestState;
import io.github.geopoliticalwar.domain.governance.NationRequestType;
import io.github.geopoliticalwar.domain.war.OccupationRights;
import io.github.geopoliticalwar.persistence.NationMembershipSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Server-authoritative civilization admission and membership transitions. */
public final class NationMembershipService {
    private NationMembershipService() {
    }

    public static void initializeFounder(ServerLevel level, Nation nation) {
        NationMembershipSavedData data = NationMembershipSavedData.get(level);
        data.setPolicy(nation.id(), AdmissionPolicy.OPEN);
        data.setMembership(new NationMembership(
                nation.id(),
                nation.leader(),
                MembershipStatus.CITIZEN,
                nation.createdAtMillis(),
                nation.leader()
        ));
        PopulationService.reconcile(level, nation.id());
    }

    public static Optional<NationMembership> membership(ServerLevel level, UUID player) {
        return NationMembershipSavedData.get(level).membership(player);
    }

    public static Optional<MembershipStatus> status(ServerLevel level, UUID player) {
        return membership(level, player).map(NationMembership::status)
                .or(() -> NationsSavedData.get(level).nationFor(player)
                        .map(ignored -> MembershipStatus.CITIZEN));
    }

    public static boolean mayHoldOffice(ServerLevel level, UUID player, NationId nation) {
        return nationFor(level, player).filter(nation::equals).isPresent()
                && status(level, player).map(MembershipStatus::mayHoldOffice).orElse(false);
    }

    public static Optional<NationId> nationFor(ServerLevel level, UUID player) {
        return membership(level, player).map(NationMembership::nation)
                .or(() -> NationsSavedData.get(level).nationFor(player).map(Nation::id));
    }

    public static NationRequest apply(ServerLevel level, NationId nationId, UUID player, MembershipStatus status, long now) {
        if (status == MembershipStatus.SUBJECT) {
            throw new IllegalArgumentException("Subjects cannot apply for citizenship");
        }
        ensureNoNation(level, player);
        Nation nation = requireNation(level, nationId);
        if (NationMembershipSavedData.get(level).policy(nationId) != AdmissionPolicy.APPLICATION) {
            throw new IllegalArgumentException("This nation is not accepting applications");
        }
        NationRequest request = new NationRequest(
                UUID.randomUUID(), nation.id(), player, NationRequestType.APPLICATION,
                status, NationRequestState.PENDING, now, expiry(now), player
        );
        NationMembershipSavedData.get(level).addRequest(request);
        return request;
    }

    public static NationRequest invite(
            ServerLevel level,
            NationId nationId,
            UUID player,
            MembershipStatus status,
            UUID inviter,
            long now
    ) {
        if (status == MembershipStatus.SUBJECT) {
            throw new IllegalArgumentException("Subjects cannot be invited as citizens");
        }
        Nation nation = requireNation(level, nationId);
        AdmissionPolicy policy = NationMembershipSavedData.get(level).policy(nationId);
        if (policy == AdmissionPolicy.CLOSED) {
            throw new IllegalArgumentException("This civilization is not accepting invitations");
        }
        if (!canManage(level, nation, inviter)) {
            throw new IllegalArgumentException("You cannot manage this nation's membership");
        }
        ensureNoNation(level, player);
        NationRequest request = new NationRequest(
                UUID.randomUUID(), nation.id(), player, NationRequestType.INVITATION,
                status, NationRequestState.PENDING, now, expiry(now), inviter
        );
        NationMembershipSavedData.get(level).addRequest(request);
        return request;
    }

    public static void joinOpen(ServerLevel level, NationId nationId, UUID player, long now) {
        ensureNoNation(level, player);
        Nation nation = requireNation(level, nationId);
        if (NationMembershipSavedData.get(level).policy(nationId) != AdmissionPolicy.OPEN) {
            throw new IllegalArgumentException("This nation requires an application or invitation");
        }
        grant(level, nation, player, MembershipStatus.CITIZEN, player, now);
    }

    public static Optional<NationRequest> immigrate(
            ServerLevel level,
            NationId nationId,
            UUID player,
            MembershipStatus requestedStatus,
            long now
    ) {
        if (requestedStatus == MembershipStatus.SUBJECT) {
            throw new IllegalArgumentException("Subjects are created by occupation, not immigration");
        }
        AdmissionPolicy policy = NationMembershipSavedData.get(level).policy(nationId);
        if (policy == AdmissionPolicy.OPEN) {
            ensureNoNation(level, player);
            grant(level, requireNation(level, nationId), player, requestedStatus, player, now);
            return Optional.empty();
        }
        if (policy == AdmissionPolicy.APPLICATION) {
            return Optional.of(apply(level, nationId, player, requestedStatus, now));
        }
        throw new IllegalArgumentException("This civilization requires an invitation and cannot accept seamless immigration");
    }

    public static void accept(ServerLevel level, UUID requestId, UUID player, long now) {
        NationMembershipSavedData data = NationMembershipSavedData.get(level);
        data.expireRequests(now);
        NationRequest request = data.request(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown nation request"));
        if (!request.player().equals(player) || !request.pending(now)) {
            throw new IllegalArgumentException("That request is not available to you");
        }
        if (request.type() != NationRequestType.INVITATION) {
            throw new IllegalArgumentException("That request is an application, not an invitation");
        }
        Nation nation = requireNation(level, request.nation());
        if (NationMembershipSavedData.get(level).policy(request.nation()) == AdmissionPolicy.CLOSED) {
            throw new IllegalArgumentException("This civilization is closed to new members");
        }
        ensureNoNation(level, player);
        grant(level, nation, player, request.requestedStatus(), request.actor(), now);
        data.replaceRequest(request.withState(NationRequestState.ACCEPTED));
    }

    public static void acceptApplication(ServerLevel level, UUID requestId, UUID approver, long now) {
        NationMembershipSavedData data = NationMembershipSavedData.get(level);
        data.expireRequests(now);
        NationRequest request = data.request(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown nation request"));
        Nation nation = requireNation(level, request.nation());
        if (NationMembershipSavedData.get(level).policy(request.nation()) == AdmissionPolicy.CLOSED) {
            throw new IllegalArgumentException("This civilization is closed to new members");
        }
        if (request.type() != NationRequestType.APPLICATION || !request.pending(now)
                || !canManage(level, nation, approver)) {
            throw new IllegalArgumentException("That application cannot be accepted");
        }
        ensureNoNation(level, request.player());
        grant(level, nation, request.player(), request.requestedStatus(), approver, now);
        data.replaceRequest(request.withState(NationRequestState.ACCEPTED));
    }

    public static void decline(ServerLevel level, UUID requestId, UUID actor, long now) {
        NationMembershipSavedData data = NationMembershipSavedData.get(level);
        data.expireRequests(now);
        NationRequest request = data.request(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown nation request"));
        Nation nation = requireNation(level, request.nation());
        boolean owner = request.player().equals(actor);
        if (!owner && !canManage(level, nation, actor)) {
            throw new IllegalArgumentException("You cannot decline this request");
        }
        if (!request.pending(now)) {
            throw new IllegalArgumentException("That request is no longer pending");
        }
        data.replaceRequest(request.withState(owner ? NationRequestState.WITHDRAWN : NationRequestState.DECLINED));
    }

    public static void setPolicy(ServerLevel level, NationId nationId, AdmissionPolicy policy, UUID actor) {
        Nation nation = requireNation(level, nationId);
        if (!canManage(level, nation, actor)) {
            throw new IllegalArgumentException("You cannot change this nation's admission policy");
        }
        NationMembershipSavedData.get(level).setPolicy(nationId, policy);
    }

    public static void leave(ServerLevel level, UUID player) {
        NationMembershipSavedData memberships = NationMembershipSavedData.get(level);
        NationMembership membership = memberships.membership(player).orElse(null);
        Nation nation = NationsSavedData.get(level).nationFor(player).orElse(null);
        if (nation == null && membership != null) {
            PopulationService.reconcile(level, membership.nation());
            memberships.removeMembership(player);
            return;
        }
        if (nation == null) {
            throw new IllegalArgumentException("Player is not in a civilization");
        }
        if (membership != null && (!membership.nation().equals(nation.id())
                || membership.status() != MembershipStatus.CITIZEN)) {
            NationsSavedData.get(level).removeCitizenForOccupation(player);
            PopulationService.reconcile(level, nation.id());
            PopulationService.reconcile(level, membership.nation());
            memberships.removeMembership(player);
            return;
        }
        if (nation.isLeader(player)) {
            throw new IllegalArgumentException("The leader must transfer leadership or disband the nation");
        }
        NationsSavedData.get(level).leave(level, player);
        memberships.removeMembership(player);
        PopulationService.reconcile(level, nation.id());
    }

    public static void applyOccupation(
            ServerLevel level,
            NationId formerNation,
            NationId occupyingNation,
            UUID player,
            OccupationRights rights,
            UUID actor,
            long now
    ) {
        NationsSavedData nations = NationsSavedData.get(level);
        NationMembershipSavedData memberships = NationMembershipSavedData.get(level);
        RolesSavedData.get(level).removeNationAssignments(player, formerNation);
        switch (rights) {
            case EQUAL_CITIZEN -> {
                try {
                    nations.transferCitizen(player, occupyingNation);
                } catch (IllegalArgumentException leaderTransferBlocked) {
                    // A defeated nation's leader cannot be removed from its Nation record until
                    // that nation appoints a successor. Membership state still gives the player
                    // the occupying nation's effective political identity.
                }
                memberships.setMembership(new NationMembership(
                        occupyingNation, player, MembershipStatus.CITIZEN, now, actor
                ));
            }
            case RESIDENT -> {
                nations.removeCitizenForOccupation(player);
                memberships.setMembership(new NationMembership(
                        occupyingNation, player, MembershipStatus.RESIDENT, now, actor
                ));
            }
            case SUBJECT -> {
                nations.removeCitizenForOccupation(player);
                memberships.setMembership(new NationMembership(
                        occupyingNation, player, MembershipStatus.SUBJECT, now, actor
                ));
            }
            case RESTRICTED, NONE -> {
                nations.removeCitizenForOccupation(player);
                memberships.removeMembership(player);
            }
        }
        PopulationService.reconcile(level, formerNation);
        PopulationService.reconcile(level, occupyingNation);
    }

    public static Nation transferLeadership(ServerLevel level, NationId nationId, UUID currentLeader, UUID successor, long now) {
        NationsSavedData nations = NationsSavedData.get(level);
        Nation nation = requireNation(level, nationId);
        if (!nation.isLeader(currentLeader)) {
            throw new IllegalArgumentException("Only the current leader may transfer leadership");
        }
        if (!nation.isCitizen(successor)) {
            throw new IllegalArgumentException("The successor must be a citizen");
        }
        Nation updated = nation.withLeader(successor, now);
        nations.replace(updated);
        NationMembershipSavedData.get(level).setMembership(new NationMembership(
                nation.id(), successor, MembershipStatus.CITIZEN, now, currentLeader
        ));
        PopulationService.reconcile(level, nation.id());
        return updated;
    }

    public static boolean canManage(ServerLevel level, Nation nation, UUID player) {
        return GovernmentAuthorityService.can(level, nation, findPlayer(level, player), AuthorityAction.MANAGE_MEMBERSHIP);
    }

    private static net.minecraft.server.level.ServerPlayer findPlayer(ServerLevel level, UUID player) {
        net.minecraft.server.level.ServerPlayer serverPlayer = level.getServer().getPlayerList().getPlayer(player);
        if (serverPlayer == null) {
            throw new IllegalArgumentException("The manager must be online");
        }
        return serverPlayer;
    }

    private static void grant(ServerLevel level, Nation nation, UUID player, MembershipStatus status, UUID actor, long now) {
        if (status == MembershipStatus.CITIZEN) {
            NationsSavedData.get(level).join(nation.id(), player);
        }
        NationMembershipSavedData.get(level).setMembership(new NationMembership(
                nation.id(), player, status, now, actor
        ));
        PopulationService.reconcile(level, nation.id());
    }

    private static Nation requireNation(ServerLevel level, NationId id) {
        return NationsSavedData.get(level).get(id).orElseThrow(() -> new IllegalArgumentException("Unknown nation"));
    }

    private static void ensureNoNation(ServerLevel level, UUID player) {
        if (NationMembershipSavedData.get(level).membership(player).isPresent()
                || NationsSavedData.get(level).nationFor(player).isPresent()) {
            throw new IllegalArgumentException("Player already belongs to a civilization");
        }
    }

    private static long expiry(long now) {
        long duration = Math.multiplyExact(ServerConfig.NATION_REQUEST_EXPIRY_SECONDS.get(), 1_000L);
        return now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    }
}
