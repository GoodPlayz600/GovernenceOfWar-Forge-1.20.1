package io.github.geopoliticalwar.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.EstateListing;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import io.github.geopoliticalwar.domain.economy.Lease;
import io.github.geopoliticalwar.domain.economy.OwnershipChangeReason;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.persistence.OwnershipSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/** Estate listings and server-validated claim purchases. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EstateCommands {
    private EstateCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("estate")
                                 .then(Commands.literal("list")
                                        .then(Commands.argument("claim", UuidArgument.uuid())
                                                .then(Commands.argument("price", LongArgumentType.longArg(1))
                                                         .executes(EstateCommands::listForSale))))
                                 .then(Commands.literal("rent")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                 .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                         .executes(EstateCommands::listForRent))))
                                 .then(Commands.literal("browse").executes(EstateCommands::browse))
                                 .then(Commands.literal("status")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                 .executes(EstateCommands::status)))
                                 .then(Commands.literal("leases").executes(EstateCommands::leases))
                                 .then(Commands.literal("lease-status")
                                          .then(Commands.argument("lease", UuidArgument.uuid())
                                                  .executes(EstateCommands::leaseStatus)))
                                  .then(Commands.literal("history")
                                          .then(Commands.argument("claim", UuidArgument.uuid())
                                                  .executes(EstateCommands::history)))
                                  .then(Commands.literal("transfer")
                                          .then(Commands.literal("player")
                                                  .then(Commands.argument("claim", UuidArgument.uuid())
                                                          .then(Commands.argument("player", UuidArgument.uuid())
                                                                  .executes(EstateCommands::transferPlayer))))
                                          .then(Commands.literal("nation")
                                                  .then(Commands.argument("claim", UuidArgument.uuid())
                                                          .then(Commands.argument("nation", UuidArgument.uuid())
                                                                  .executes(EstateCommands::transferNation)))))
                                 .then(Commands.literal("buy")
                                        .then(Commands.argument("listing", UuidArgument.uuid())
                                                 .executes(EstateCommands::buy)))
                                 .then(Commands.literal("lease")
                                          .then(Commands.argument("listing", UuidArgument.uuid())
                                                  .executes(EstateCommands::lease)))
                                 .then(Commands.literal("terminate")
                                         .then(Commands.argument("lease", UuidArgument.uuid())
                                                 .executes(EstateCommands::terminateLease)))
                                 .then(Commands.literal("evict")
                                         .then(Commands.argument("lease", UuidArgument.uuid())
                                                 .executes(EstateCommands::evict)))
                                 .then(Commands.literal("cancel")
                                        .then(Commands.argument("listing", UuidArgument.uuid())
                                                .executes(EstateCommands::cancel)))
                        )
        );
    }

    private static CurrencyId currency() {
        return new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
    }

    private static AccountId account(String value) {
        return new AccountId(value);
    }

    private static int listForSale(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId claimId = new ClaimId(UuidArgument.getUuid(context, "claim"));
        long price = LongArgumentType.getLong(context, "price");
        ClaimsSavedData claims = ClaimsSavedData.get(player.serverLevel());
        Claim claim = claims.get(claimId).orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown claim."));
            return 0;
        }
        if (OccupationsSavedData.get(player.serverLevel()).get(claimId)
                .filter(occupation -> occupation.settledAtMillis().isEmpty()).isPresent()) {
            context.getSource().sendFailure(Component.literal("Occupied claims cannot be listed for sale."));
            return 0;
        }
        if (LeaseSavedData.get(player.serverLevel()).forClaim(claimId).isPresent()) {
            context.getSource().sendFailure(Component.literal("Leased claims cannot be listed for sale."));
            return 0;
        }
        if (TaxSavedData.get(player.serverLevel()).get(claimId) != null) {
            context.getSource().sendFailure(Component.literal("Claims with unpaid tax cannot be listed for sale."));
            return 0;
        }
        if (!ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage this claim."));
            return 0;
        }

        EstateListing listing = new EstateListing(
                UUID.randomUUID(),
                claimId,
                account(claim.owner().value()),
                price,
                0L,
                System.currentTimeMillis()
        );
        try {
            EstateSavedData.get(player.serverLevel()).add(listing);
            context.getSource().sendSuccess(
                    () -> Component.literal("Listed " + claim.name() + " for " + price + " " + currency().value()
                            + ". Listing ID: " + listing.id()),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int browse(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        EstateSavedData.get(context.getSource().getLevel()).all().values().forEach(listing ->
                context.getSource().sendSuccess(
                        () -> Component.literal(
                                listing.id() + " claim=" + listing.claim().value()
                                        + " price=" + listing.salePrice() + " seller=" + listing.seller().value()
                        ),
                        false
                )
        );
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId claimId = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(claimId).orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown claim."));
            return 0;
        }
        EstateListing listing = EstateSavedData.get(player.serverLevel()).all().values().stream()
                .filter(value -> value.claim().equals(claimId)).findFirst().orElse(null);
        Lease lease = LeaseSavedData.get(player.serverLevel()).forClaim(claimId).orElse(null);
        context.getSource().sendSuccess(() -> Component.literal(
                claim.name() + " owner=" + claim.owner().value()
                        + " listing=" + (listing == null ? "none" : listing.id())
                        + " lease=" + (lease == null ? "none" : lease.id() + " tenant=" + lease.tenant().value()
                        + " rent=" + lease.rentPerCycle() + " missed=" + lease.missedPayments())
        ), false);
        return 1;
    }

    private static int leases(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AccountId account = account("player:" + player.getUUID());
        LeaseSavedData.get(player.serverLevel()).all().values().stream()
                .filter(lease -> lease.tenant().equals(account)
                        || ClaimsSavedData.get(player.serverLevel()).get(lease.claim())
                        .map(claim -> ClaimAccessService.canManageClaim(player, player.serverLevel(), claim))
                        .orElse(false))
                .forEach(lease -> context.getSource().sendSuccess(
                        () -> Component.literal(lease.id() + " claim=" + lease.claim().value()
                                + " tenant=" + lease.tenant().value() + " missed=" + lease.missedPayments()), false));
        return 1;
    }

    private static int leaseStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(context, "lease");
        Lease lease = LeaseSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (lease == null) {
            context.getSource().sendFailure(Component.literal("Unknown lease."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Lease " + lease.id() + " claim=" + lease.claim().value()
                        + " landlord=" + lease.landlord().value() + " tenant=" + lease.tenant().value()
                        + " rent=" + lease.rentPerCycle() + " nextDue=" + lease.nextDueAtMillis()
                        + " missed=" + lease.missedPayments()), false);
        return 1;
    }

    private static int history(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId claim = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim current = ClaimsSavedData.get(player.serverLevel()).get(claim).orElse(null);
        if (current == null || (!context.getSource().hasPermission(2)
                && !ClaimAccessService.canManageClaim(player, player.serverLevel(), current))) {
            context.getSource().sendFailure(Component.literal("You cannot inspect that ownership history."));
            return 0;
        }
        OwnershipSavedData.get(player.serverLevel()).forClaim(claim).forEach(record ->
                context.getSource().sendSuccess(() -> Component.literal(
                        record.changedAtMillis() + " " + record.reason() + " "
                                + record.previousOwner().value() + " -> " + record.newOwner().value()), false));
        return 1;
    }

    private static int transferPlayer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ClaimId claim = new ClaimId(UuidArgument.getUuid(context, "claim"));
        PrincipalId owner = new PrincipalId("player:" + UuidArgument.getUuid(context, "player"));
        try {
            PropertyLifecycleService.transfer(actor.serverLevel(), claim, owner, actor, OwnershipChangeReason.ADMIN_TRANSFER);
            context.getSource().sendSuccess(() -> Component.literal("Claim ownership transferred to the player."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int transferNation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ClaimId claim = new ClaimId(UuidArgument.getUuid(context, "claim"));
        java.util.UUID nationId = UuidArgument.getUuid(context, "nation");
        if (NationsSavedData.get(actor.serverLevel()).get(
                new io.github.geopoliticalwar.domain.governance.NationId(nationId)).isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown nation."));
            return 0;
        }
        try {
            PropertyLifecycleService.transfer(actor.serverLevel(), claim,
                    new PrincipalId("nation:" + nationId), actor, OwnershipChangeReason.ADMIN_TRANSFER);
            context.getSource().sendSuccess(() -> Component.literal("Claim ownership transferred to the nation."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int listForRent(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId claimId = new ClaimId(UuidArgument.getUuid(context, "claim"));
        long rent = LongArgumentType.getLong(context, "amount");
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(claimId).orElse(null);
        if (claim == null || !ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage that claim."));
            return 0;
        }
        if (OccupationsSavedData.get(player.serverLevel()).get(claimId)
                .filter(occupation -> occupation.settledAtMillis().isEmpty()).isPresent()) {
            context.getSource().sendFailure(Component.literal("Occupied claims cannot be rented."));
            return 0;
        }
        if (LeaseSavedData.get(player.serverLevel()).forClaim(claimId).isPresent()) {
            context.getSource().sendFailure(Component.literal("Claims with active leases cannot be rented again."));
            return 0;
        }
        EstateListing listing = new EstateListing(
                UUID.randomUUID(), claimId, account(claim.owner().value()), 0L, rent, System.currentTimeMillis()
        );
        try {
            EstateSavedData.get(player.serverLevel()).add(listing);
            context.getSource().sendSuccess(() -> Component.literal("Listed claim for rent. Listing ID: " + listing.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int buy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer buyer = context.getSource().getPlayerOrException();
        UUID listingId = UuidArgument.getUuid(context, "listing");
        EstateSavedData estates = EstateSavedData.get(buyer.serverLevel());
        EstateListing listing = estates.get(listingId).orElse(null);
        if (listing == null) {
            context.getSource().sendFailure(Component.literal("Unknown estate listing."));
            return 0;
        }
        if (!listing.forSale()) {
            context.getSource().sendFailure(Component.literal("That listing is for rent, not for sale."));
            return 0;
        }
        ClaimsSavedData claims = ClaimsSavedData.get(buyer.serverLevel());
        Claim claim = claims.get(listing.claim()).orElse(null);
        if (claim == null) {
            estates.remove(listingId);
            context.getSource().sendFailure(Component.literal("The listed claim no longer exists."));
            return 0;
        }
        if (!account(claim.owner().value()).equals(listing.seller())) {
            estates.remove(listingId);
            context.getSource().sendFailure(Component.literal("The listing is stale because claim ownership changed."));
            return 0;
        }
        if (OccupationsSavedData.get(buyer.serverLevel()).get(listing.claim())
                .filter(occupation -> occupation.settledAtMillis().isEmpty()).isPresent()) {
            context.getSource().sendFailure(Component.literal("Occupied claims cannot be purchased."));
            return 0;
        }
        if (LeaseSavedData.get(buyer.serverLevel()).forClaim(listing.claim()).isPresent()) {
            context.getSource().sendFailure(Component.literal("Claims with active leases cannot be purchased."));
            return 0;
        }
        if (TaxSavedData.get(buyer.serverLevel()).get(listing.claim()) != null) {
            context.getSource().sendFailure(Component.literal("Claims with unpaid tax cannot be purchased."));
            return 0;
        }
        if (WarsSavedData.get(buyer.serverLevel()).referencesClaim(listing.claim())) {
            context.getSource().sendFailure(Component.literal("Formal war references this claim; it cannot be purchased."));
            return 0;
        }
        AccountId buyerAccount = account("player:" + buyer.getUUID());
        if (buyerAccount.equals(listing.seller())) {
            context.getSource().sendFailure(Component.literal("You already own this claim."));
            return 0;
        }
        try {
            EconomySavedData economy = EconomySavedData.get(buyer.serverLevel());
            long salesTax = Math.min(
                    listing.salePrice(),
                    economy.inflationAdjusted(currency(), TaxService.salesTax(
                            buyer.serverLevel(), TaxService.nationOwner(claim), listing.salePrice()))
            );
            AccountId salesTaxRecipient = TaxService.salesTaxRecipient(
                    buyer.serverLevel(), TaxService.nationOwner(claim));
            long sellerProceeds = listing.salePrice() - salesTax;
            if (economy.balance(currency(), buyerAccount) < listing.salePrice()) {
                throw new IllegalArgumentException("Insufficient funds");
            }
            Map<AccountId, Long> transfers = new HashMap<>();
            if (sellerProceeds > 0L && !buyerAccount.equals(listing.seller())) {
                transfers.merge(listing.seller(), sellerProceeds,
                        (first, second) -> Math.addExact(first, second));
            }
            if (salesTax > 0L && !buyerAccount.equals(salesTaxRecipient)) {
                transfers.merge(salesTaxRecipient, salesTax,
                        (first, second) -> Math.addExact(first, second));
            }
            economy.transferBatch(currency(), buyerAccount, transfers, "estate purchase and sales tax");
            claims.replace(claim.withOwnerClearingPermissions(ClaimAccessService.playerPrincipal(buyer)));
            OwnershipSavedData.get(buyer.serverLevel()).add(new io.github.geopoliticalwar.domain.economy.OwnershipRecord(
                    UUID.randomUUID(), claim.id(), claim.owner(), ClaimAccessService.playerPrincipal(buyer),
                    OwnershipChangeReason.SALE, buyer.getUUID(), System.currentTimeMillis()
            ));
            estates.remove(listingId);
            context.getSource().sendSuccess(
                    () -> Component.literal("Purchased " + claim.name() + " for " + listing.salePrice()
                            + " (sales tax " + salesTax + ")."),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int cancel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID listingId = UuidArgument.getUuid(context, "listing");
        EstateSavedData estates = EstateSavedData.get(player.serverLevel());
        EstateListing listing = estates.get(listingId).orElse(null);
        if (listing == null) {
            context.getSource().sendFailure(Component.literal("Unknown estate listing."));
            return 0;
        }
        Claim listedClaim = ClaimsSavedData.get(player.serverLevel()).get(listing.claim()).orElse(null);
        boolean managesClaim = listedClaim != null && ClaimAccessService.canManageClaim(player, player.serverLevel(), listedClaim);
        if (!managesClaim && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Only the seller may cancel this listing."));
            return 0;
        }
        estates.remove(listingId);
        context.getSource().sendSuccess(() -> Component.literal("Estate listing cancelled."), true);
        return 1;
    }

    private static int lease(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer tenant = context.getSource().getPlayerOrException();
        UUID listingId = UuidArgument.getUuid(context, "listing");
        EstateSavedData estates = EstateSavedData.get(tenant.serverLevel());
        EstateListing listing = estates.get(listingId).orElse(null);
        if (listing == null || !listing.forRent()) {
            context.getSource().sendFailure(Component.literal("That listing is not available for rent."));
            return 0;
        }
        Claim claim = ClaimsSavedData.get(tenant.serverLevel()).get(listing.claim()).orElse(null);
        if (claim == null || OccupationsSavedData.get(tenant.serverLevel()).get(listing.claim())
                .filter(occupation -> occupation.settledAtMillis().isEmpty()).isPresent()) {
            context.getSource().sendFailure(Component.literal("That claim is no longer leasable."));
            return 0;
        }
        AccountId tenantAccount = account("player:" + tenant.getUUID());
        if (tenantAccount.equals(listing.seller())) {
            context.getSource().sendFailure(Component.literal("The owner cannot lease their own claim."));
            return 0;
        }
        try {
            Lease lease = LeaseService.create(
                    tenant.serverLevel(), claim, tenantAccount, listing.rentPerCycle(), System.currentTimeMillis()
            );
            estates.remove(listingId);
            context.getSource().sendSuccess(() -> Component.literal("Lease accepted: " + lease.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int terminateLease(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID leaseId = UuidArgument.getUuid(context, "lease");
        LeaseSavedData leases = LeaseSavedData.get(player.serverLevel());
        Lease lease = leases.get(leaseId).orElse(null);
        if (lease == null) {
            context.getSource().sendFailure(Component.literal("Unknown lease."));
            return 0;
        }
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(lease.claim()).orElse(null);
        boolean tenant = lease.tenant().equals(account("player:" + player.getUUID()));
        boolean landlord = claim != null && ClaimAccessService.canManageClaim(player, player.serverLevel(), claim);
        if (!tenant && !landlord && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Only the tenant, landlord, or an administrator may terminate this lease."));
            return 0;
        }
        leases.remove(leaseId);
        context.getSource().sendSuccess(() -> Component.literal("Lease terminated."), true);
        return 1;
    }

    private static int evict(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID leaseId = UuidArgument.getUuid(context, "lease");
        LeaseSavedData leases = LeaseSavedData.get(player.serverLevel());
        Lease lease = leases.get(leaseId).orElse(null);
        Claim claim = lease == null ? null : ClaimsSavedData.get(player.serverLevel()).get(lease.claim()).orElse(null);
        if (lease == null || claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown lease."));
            return 0;
        }
        if (!ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)
                && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("Only the landlord or an administrator may evict a tenant."));
            return 0;
        }
        leases.remove(leaseId);
        context.getSource().sendSuccess(() -> Component.literal("Tenant evicted."), true);
        return 1;
    }
}
