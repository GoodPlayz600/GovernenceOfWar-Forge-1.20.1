package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.domain.economy.OwnershipChangeReason;
import io.github.geopoliticalwar.domain.economy.OwnershipRecord;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.OwnershipSavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Central ownership transfer path for ordinary administrative transfers. */
public final class PropertyLifecycleService {
    private PropertyLifecycleService() {
    }

    public static Claim transfer(
            ServerLevel level,
            ClaimId claimId,
            PrincipalId newOwner,
            ServerPlayer actor,
            OwnershipChangeReason reason
    ) {
        Claim claim = ClaimsSavedData.get(level).get(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown claim"));
        if (!actor.hasPermissions(2) && !ClaimAccessService.canManageClaim(actor, level, claim)) {
            throw new IllegalArgumentException("You do not manage this claim");
        }
        if (OccupationsSavedData.get(level).get(claimId)
                .filter(value -> value.settledAtMillis().isEmpty()).isPresent()) {
            throw new IllegalArgumentException("Occupied claims cannot be transferred");
        }
        if (LeaseSavedData.get(level).forClaim(claimId).isPresent()) {
            throw new IllegalArgumentException("Terminate the active lease before transferring ownership");
        }
        if (WarsSavedData.get(level).referencesClaim(claimId)) {
            throw new IllegalArgumentException("Formal war references this claim");
        }
        if (TaxSavedData.get(level).get(claimId) != null) {
            throw new IllegalArgumentException("Settle the outstanding tax obligation before transferring ownership");
        }
        if (claim.owner().equals(newOwner)) {
            throw new IllegalArgumentException("The new owner is already the current owner");
        }
        Claim updated = claim.withOwnerClearingPermissions(newOwner);
        ClaimsSavedData.get(level).replace(updated);
        EstateSavedData.get(level).removeForClaim(claimId);
        OwnershipSavedData.get(level).add(new OwnershipRecord(
                UUID.randomUUID(), claimId, claim.owner(), newOwner, reason, actor.getUUID(), System.currentTimeMillis()
        ));
        notifyPlayer(level, claim.owner(), "Your ownership of claim " + claim.name() + " ended.");
        notifyPlayer(level, newOwner, "You now own claim " + claim.name() + ".");
        return updated;
    }

    private static void notifyPlayer(ServerLevel level, PrincipalId principal, String message) {
        String value = principal.value();
        if (!value.startsWith("player:")) {
            return;
        }
        try {
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(UUID.fromString(value.substring("player:".length())));
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed legacy principals are ignored after the ownership change is persisted.
        }
    }
}
