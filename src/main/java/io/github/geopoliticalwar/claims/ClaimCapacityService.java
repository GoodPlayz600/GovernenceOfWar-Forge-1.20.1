package io.github.geopoliticalwar.claims;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.governance.PopulationService;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import net.minecraft.server.level.ServerLevel;

/** Population-scaled land administration limits. */
public final class ClaimCapacityService {
    private ClaimCapacityService() {
    }

    public static int nationCapacity(ServerLevel level, NationId nation) {
        return io.github.geopoliticalwar.domain.claim.ClaimCapacityPolicy.capacity(
                PopulationService.population(level, nation),
                ServerConfig.BASE_CLAIM_CAPACITY.get(),
                ServerConfig.POPULATION_PER_CLAIM.get(),
                ServerConfig.MAX_CLAIMS_PER_NATION.get()
        );
    }

    public static long nationOwnedClaims(ServerLevel level, NationId nation) {
        String owner = "nation:" + nation.value();
        return ClaimsSavedData.get(level).all().values().stream()
                .map(Claim::owner)
                .filter(principal -> principal.value().equals(owner))
                .count();
    }

    public static boolean mayCreateNationClaim(ServerLevel level, NationId nation) {
        return nationOwnedClaims(level, nation) < nationCapacity(level, nation);
    }
}
