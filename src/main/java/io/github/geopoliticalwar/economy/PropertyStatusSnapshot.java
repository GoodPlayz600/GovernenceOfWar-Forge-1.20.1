package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.CaptivesSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Read-only server-generated estate-board data sent to the client UI. */
public record PropertyStatusSnapshot(
        String claimName,
        String dimension,
        String owner,
        String listing,
        String lease,
        String tax,
        String occupation,
        String captives
) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(claimName, 128);
        buffer.writeUtf(dimension, 128);
        buffer.writeUtf(owner, 256);
        buffer.writeUtf(listing, 256);
        buffer.writeUtf(lease, 256);
        buffer.writeUtf(tax, 256);
        buffer.writeUtf(occupation, 256);
        buffer.writeUtf(captives, 256);
    }

    public static PropertyStatusSnapshot read(FriendlyByteBuf buffer) {
        return new PropertyStatusSnapshot(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256)
        );
    }

    public static PropertyStatusSnapshot at(ServerLevel level, BlockPos position) {
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        Claim claim = claims.containing(
                        new DimensionId(level.dimension().location().toString()),
                        position.getX(), position.getY(), position.getZ()
                ).stream()
                .map(claims::get)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
        if (claim == null) {
            return new PropertyStatusSnapshot("No claim", level.dimension().location().toString(),
                    "unowned", "none", "none", "none", "none", "none");
        }
        var listing = EstateSavedData.get(level).all().values().stream()
                .filter(value -> value.claim().equals(claim.id())).findFirst();
        var lease = LeaseSavedData.get(level).forClaim(claim.id());
        var tax = TaxSavedData.get(level).get(claim.id());
        var occupation = OccupationsSavedData.get(level).get(claim.id())
                .filter(value -> value.settledAtMillis().isEmpty());
        var policy = TaxPolicyService.effective(level, claim);
        String taxRate = !TaxPolicyService.hasEffectiveOverride(level, claim)
                && ServerConfig.PROPERTY_TAX_PER_BLOCK.get() <= 0
                ? "disabled"
                : policy.mode() + " " + policy.unitsPerBlock() + "/block";
        long captiveCount = CaptivesSavedData.get(level).all().values().stream()
                .filter(value -> value.claim().equals(claim.id()) && value.active())
                .count();
        return new PropertyStatusSnapshot(
                claim.name(),
                claim.dimension().value(),
                claim.owner().value(),
                listing.map(value -> (value.forSale() ? "sale=" + value.salePrice() : "rent=" + value.rentPerCycle()))
                        .orElse("none"),
                lease.map(value -> "tenant=" + value.tenant().value() + " rent=" + value.rentPerCycle()
                        + " missed=" + value.missedPayments()).orElse("none"),
                taxRate
                        + (tax == null ? " unpaid=none" : " unpaid=" + tax.amount() + " due=" + tax.dueAtMillis()),
                occupation.map(value -> "holding=" + value.occupyingNation().value() + " rights=" + value.rights())
                        .orElse("none"),
                "active=" + captiveCount
        );
    }
}
