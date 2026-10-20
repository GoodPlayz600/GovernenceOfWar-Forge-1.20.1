package io.github.geopoliticalwar.claims;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.persistence.TaxPolicySavedData;
import io.github.geopoliticalwar.persistence.LaborSitesSavedData;
import net.minecraft.server.level.ServerLevel;

/** Keeps claim-linked records consistent when a claim is removed. */
public final class ClaimLifecycleService {
    private ClaimLifecycleService() {
    }

    public static boolean delete(ServerLevel level, ClaimId claim) {
        boolean removed = ClaimsSavedData.get(level).remove(claim);
        if (!removed) {
            return false;
        }
        EstateSavedData.get(level).removeForClaim(claim);
        TaxSavedData.get(level).remove(claim);
        TaxPolicySavedData.get(level).remove(claim);
        LaborSitesSavedData.get(level).removeForClaim(claim);
        OccupationsSavedData.get(level).remove(claim);
        LeaseSavedData.get(level).removeForClaim(claim);
        return true;
    }
}
