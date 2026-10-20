package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import io.github.geopoliticalwar.domain.war.OccupationRights;
import io.github.geopoliticalwar.domain.war.WarGoal;
import io.github.geopoliticalwar.domain.war.WarGoalType;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import io.github.geopoliticalwar.persistence.OwnershipSavedData;
import io.github.geopoliticalwar.persistence.LaborSitesSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Turns achieved war goals into delayed, auditable occupation and settlement records. */
public final class ConquestService {
    private ConquestService() {
    }

    public static WarRecord resolve(ServerLevel level, WarId warId, NationId victor, long now) {
        WarsSavedData wars = WarsSavedData.get(level);
        WarRecord war = wars.get(warId).orElseThrow(() -> new IllegalArgumentException("Unknown war"));
        if (!war.includes(victor)) {
            throw new IllegalArgumentException("Victor must be a participant");
        }
        if (war.goals().stream().noneMatch(goal -> goal.achieved() && goal.beneficiary().equals(victor))) {
            throw new IllegalArgumentException("The victor has not achieved a war goal");
        }
        Math.multiplyExact(ServerConfig.OCCUPATION_SETTLEMENT_SECONDS.get(), 1_000L);
        for (WarGoal goal : war.goals()) {
            if (goal.type() == WarGoalType.CONQUEST_CLAIM && goal.achieved()
                    && goal.beneficiary().equals(victor)) {
                ClaimId target = goal.claim().orElseThrow(() -> new IllegalArgumentException("Conquest goal has no claim"));
                Claim targetClaim = ClaimsSavedData.get(level).get(target)
                        .orElseThrow(() -> new IllegalArgumentException("Conquest target no longer exists"));
                validateOccupationPopulation(level, targetClaim);
                if (OccupationsSavedData.get(level).get(target)
                        .filter(existing -> existing.settledAtMillis().isEmpty()).isPresent()) {
                    throw new IllegalArgumentException("Conquest target is already occupied");
                }
            }
        }
        WarRecord resolved = war.resolve(victor, now);
        wars.replace(resolved);
        WarStateService.setPhase(level, io.github.geopoliticalwar.domain.war.WarPhase.ARMISTICE);
        for (WarGoal goal : resolved.goals()) {
            if (goal.type() == WarGoalType.CONQUEST_CLAIM
                    && goal.achieved()
                    && goal.beneficiary().equals(victor)) {
                beginOccupation(level, resolved, goal, now);
            }
        }
        return resolved;
    }

    public static OccupationRecord beginOccupation(ServerLevel level, WarRecord war, WarGoal goal, long now) {
        ClaimId claimId = goal.claim().orElseThrow(() -> new IllegalArgumentException("Goal has no claim"));
        Claim claim = ClaimsSavedData.get(level).get(claimId).orElseThrow(() -> new IllegalArgumentException("Unknown target claim"));
        OccupationsSavedData occupations = OccupationsSavedData.get(level);
        if (occupations.get(claimId).filter(existing -> existing.settledAtMillis().isEmpty()).isPresent()) {
            throw new IllegalArgumentException("Claim is already occupied");
        }
        long delay = Math.multiplyExact(ServerConfig.OCCUPATION_SETTLEMENT_SECONDS.get(), 1_000L);
        OccupationRecord occupation = new OccupationRecord(
                claimId,
                war.id(),
                goal.beneficiary(),
                claim.owner(),
                OccupationRights.parse(ServerConfig.DEFAULT_OCCUPATION_RIGHTS.get()),
                now,
                now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay,
                Optional.empty(),
                formerPopulation(level, claim)
        );
        validateOccupationPopulation(level, claim);
        nationOwner(claim).ifPresent(formerNation -> occupation.formerPopulation().forEach(player ->
                NationMembershipService.applyOccupation(
                        level,
                        formerNation,
                        goal.beneficiary(),
                        player,
                        occupation.rights(),
                        goal.beneficiary().value(),
                        now
                )
        ));
        occupations.set(occupation);
        return occupation;
    }

    private static Set<java.util.UUID> formerPopulation(ServerLevel level, Claim claim) {
        return nationOwner(claim).map(nation -> {
            java.util.Set<java.util.UUID> population = new java.util.HashSet<>(io.github.geopoliticalwar.persistence.NationsSavedData.get(level)
                    .get(nation)
                    .map(value -> value.citizens())
                    .orElse(Set.of()));
            io.github.geopoliticalwar.persistence.NationMembershipSavedData.get(level).memberships().values().stream()
                    .filter(membership -> membership.nation().equals(nation))
                    .map(value -> value.player())
                    .forEach(population::add);
            return population;
        }).orElse(Set.of());
    }

    private static Optional<NationId> nationOwner(Claim claim) {
        String owner = claim.owner().value();
        if (!owner.startsWith("nation:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new NationId(UUID.fromString(owner.substring("nation:".length()))));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static void validateOccupationPopulation(ServerLevel level, Claim claim) {
        NationId formerNation = nationOwner(claim).orElse(null);
        if (formerNation == null) {
            return;
        }
        io.github.geopoliticalwar.persistence.NationsSavedData.get(level).get(formerNation).ifPresent(nation -> {
            if (nation.citizens().size() == 1) {
                throw new IllegalArgumentException(
                        "The former nation has a sole leader; transfer leadership or disband it before occupation"
                );
            }
        });
    }

    public static OccupationRecord settle(ServerLevel level, ClaimId claimId, long now) {
        OccupationsSavedData occupations = OccupationsSavedData.get(level);
        OccupationRecord occupation = occupations.get(claimId).orElseThrow(() -> new IllegalArgumentException("Unknown occupation"));
        if (!occupation.eligible(now)) {
            throw new IllegalArgumentException("Occupation settlement cooldown has not elapsed");
        }
        WarRecord war = WarsSavedData.get(level).get(occupation.war())
                .orElseThrow(() -> new IllegalArgumentException("Occupation war no longer exists"));
        if (war.status() != io.github.geopoliticalwar.domain.war.WarStatus.RESOLVED
                || war.victor().filter(occupation.occupyingNation()::equals).isEmpty()) {
            throw new IllegalArgumentException("Occupation can only settle after a resolved victory");
        }
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        Claim claim = claims.get(claimId).orElseThrow(() -> new IllegalArgumentException("Target claim no longer exists"));
        if (LeaseSavedData.get(level).forClaim(claimId).isPresent()) {
            throw new IllegalArgumentException("An active lease must be terminated before occupation settlement");
        }
        claims.replace(claim.withOwnerClearingPermissions(new PrincipalId("nation:" + occupation.occupyingNation().value())));
        OwnershipSavedData.get(level).add(new io.github.geopoliticalwar.domain.economy.OwnershipRecord(
                UUID.randomUUID(), claimId, claim.owner(),
                new PrincipalId("nation:" + occupation.occupyingNation().value()),
                io.github.geopoliticalwar.domain.economy.OwnershipChangeReason.CONQUEST_SETTLEMENT,
                occupation.occupyingNation().value(), now
        ));
        EstateSavedData.get(level).removeForClaim(claimId);
        TaxSavedData.get(level).remove(claimId);
        LaborSitesSavedData.get(level).removeForClaim(claimId);
        OccupationRecord settled = occupation.settle(now);
        occupations.set(settled);
        WarStateService.setPhase(level, io.github.geopoliticalwar.domain.war.WarPhase.ARMISTICE);
        GeopoliticalWarfare.LOGGER.info("Settled occupation of {} for nation {}", claimId.value(), occupation.occupyingNation().value());
        return settled;
    }
}
