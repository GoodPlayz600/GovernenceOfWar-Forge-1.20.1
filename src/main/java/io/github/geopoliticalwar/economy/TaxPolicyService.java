package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.governance.GovernmentPolicyService;
import io.github.geopoliticalwar.persistence.TaxPolicySavedData;
import net.minecraft.server.level.ServerLevel;

/** Resolves and validates property-tax policy for a claim. */
public final class TaxPolicyService {
    private TaxPolicyService() {
    }

    public static TaxPolicy effective(ServerLevel level, Claim claim) {
        return TaxPolicySavedData.get(level).get(claim.id())
                .or(() -> nationOwner(claim).flatMap(nation -> GovernmentPolicyService.propertyTax(level, nation)))
                .orElseGet(() -> TaxPolicy.ledger(
                        Math.max(1L, ServerConfig.PROPERTY_TAX_PER_BLOCK.get()),
                        new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get())));
    }

    public static boolean hasCustom(ServerLevel level, ClaimId claim) {
        return TaxPolicySavedData.get(level).get(claim).isPresent();
    }

    public static boolean hasEffectiveOverride(ServerLevel level, Claim claim) {
        return hasCustom(level, claim.id()) || nationOwner(claim)
                .flatMap(nation -> GovernmentPolicyService.propertyTax(level, nation)).isPresent();
    }

    public static long unitsDue(Claim claim, TaxPolicy policy) {
        return Math.multiplyExact(claim.volume().volume(), policy.unitsPerBlock());
    }

    public static void set(ServerLevel level, ClaimId claim, TaxPolicy policy) {
        TaxPolicySavedData.get(level).set(claim, policy);
    }

    public static void clear(ServerLevel level, ClaimId claim) {
        TaxPolicySavedData.get(level).remove(claim);
    }

    private static java.util.Optional<NationId> nationOwner(Claim claim) {
        String owner = claim.owner().value();
        if (!owner.startsWith("nation:")) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new NationId(java.util.UUID.fromString(owner.substring("nation:".length()))));
        } catch (IllegalArgumentException invalid) {
            return java.util.Optional.empty();
        }
    }
}
