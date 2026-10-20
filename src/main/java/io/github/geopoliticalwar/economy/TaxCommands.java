package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.TaxMaterialTreasurySavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.governance.NationMembershipService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaxCommands {
    private TaxCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("tax")
                                .then(Commands.literal("status")
                                        .executes(TaxCommands::status)
                                        .then(Commands.argument("claim", UuidArgument.uuid())
                                                .executes(TaxCommands::statusClaim)))
                                .then(Commands.literal("set")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .then(Commands.argument("rate", LongArgumentType.longArg(1))
                                                                .executes(TaxCommands::set))))
                                .then(Commands.literal("clear")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                .executes(TaxCommands::clear)))
                                .then(Commands.literal("pay")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                .executes(TaxCommands::pay)))
                                .then(Commands.literal("treasury")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(TaxCommands::treasury)
                                        .then(Commands.literal("withdraw")
                                                .then(Commands.argument("item", StringArgumentType.word())
                                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                                .executes(TaxCommands::withdrawTreasury)))))
                                .then(Commands.literal("collect")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(TaxCommands::collect))
                        )
        );
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AccountId account = new AccountId("player:" + player.getUUID());
        TaxSavedData taxes = TaxSavedData.get(player.serverLevel());
        taxes.all().values().stream()
                .filter(obligation -> obligation.debtor().equals(account)
                        || NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                        .map(nation -> obligation.debtor().equals(new AccountId("nation:" + nation.value())))
                        .orElse(false))
                .forEach(obligation -> context.getSource().sendSuccess(
                        () -> Component.literal(
                                "Claim " + obligation.claim().value() + " owes " + obligation.amount()
                                        + " due=" + obligation.dueAtMillis()
                        ),
                        false
                ));
        return 1;
    }

    private static int statusClaim(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId id = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown claim."));
            return 0;
        }
        if (!ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)
                && !claim.owner().equals(ClaimAccessService.playerPrincipal(player))) {
            context.getSource().sendFailure(Component.literal("You cannot inspect that claim's tax status."));
            return 0;
        }
        TaxSavedData taxes = TaxSavedData.get(player.serverLevel());
        var policy = TaxPolicyService.effective(player.serverLevel(), claim);
        String rate = !TaxPolicyService.hasEffectiveOverride(player.serverLevel(), claim)
                && ServerConfig.PROPERTY_TAX_PER_BLOCK.get() <= 0
                ? "disabled"
                : policy.unitsPerBlock() + "/block";
        var obligation = taxes.get(id);
        context.getSource().sendSuccess(() -> Component.literal(
                "Claim " + claim.name() + " tax mode=" + policy.mode()
                        + " rate=" + rate + " item=" + policy.itemId()
                        + " due=" + (obligation == null ? "none" : obligation.amount()
                        + " at " + obligation.dueAtMillis() + " attempts=" + obligation.attempts())
        ), false);
        return 1;
    }

    private static int set(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId id = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (claim == null || !ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage that claim."));
            return 0;
        }
        long rate = LongArgumentType.getLong(context, "rate");
        String mode = StringArgumentType.getString(context, "mode").toUpperCase(java.util.Locale.ROOT);
        CurrencyId currency = new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
        TaxPolicy policy;
        try {
            policy = switch (TaxMode.valueOf(mode)) {
                case LEDGER -> TaxPolicy.ledger(rate, currency);
                case DIAMONDS -> TaxPolicy.diamonds(rate, currency);
                case ITEM -> {
                    if (player.getMainHandItem().isEmpty()) {
                        throw new IllegalArgumentException("Hold an exemplar item for an ITEM tax");
                    }
                    yield TaxPolicy.item(rate, currency, TaxPaymentService.itemId(player.getMainHandItem()));
                }
            };
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(
                    "Tax policy rejected: use LEDGER, DIAMONDS, or ITEM and hold the taxed item for ITEM."
            ));
            return 0;
        }
        TaxPolicyService.set(player.serverLevel(), id, policy);
        context.getSource().sendSuccess(() -> Component.literal(
                "Tax policy set to " + policy.mode() + " at " + policy.unitsPerBlock() + " per block."), true);
        return 1;
    }

    private static int clear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId id = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (claim == null || !ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage that claim."));
            return 0;
        }
        TaxPolicyService.clear(player.serverLevel(), id);
        context.getSource().sendSuccess(() -> Component.literal("Claim tax policy reset to the server default."), true);
        return 1;
    }

    private static int pay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimId id = new ClaimId(UuidArgument.getUuid(context, "claim"));
        Claim claim = ClaimsSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (claim == null || !ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage that claim."));
            return 0;
        }
        var policy = TaxPolicyService.effective(player.serverLevel(), claim);
        if (policy.mode() == TaxMode.LEDGER) {
            context.getSource().sendFailure(Component.literal("Ledger taxes are collected from the account automatically."));
            return 0;
        }
        if (!TaxPolicyService.hasEffectiveOverride(player.serverLevel(), claim)
                && ServerConfig.PROPERTY_TAX_PER_BLOCK.get() <= 0) {
            context.getSource().sendFailure(Component.literal("Property tax is disabled for this claim."));
            return 0;
        }
        long required;
        try {
            required = TaxPolicyService.unitsDue(claim, policy);
        } catch (ArithmeticException overflow) {
            context.getSource().sendFailure(Component.literal("Tax demand is too large."));
            return 0;
        }
        if (!TaxPaymentService.payNow(player.serverLevel(), player, claim, policy, required)) {
            context.getSource().sendFailure(Component.literal("You do not have enough required tax items or the tax treasury is full."));
            return 0;
        }
        TaxSavedData.get(player.serverLevel()).remove(id);
        context.getSource().sendSuccess(() -> Component.literal("Physical property tax paid."), true);
        return 1;
    }

    private static int treasury(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        TaxMaterialTreasurySavedData treasury = TaxMaterialTreasurySavedData.get(context.getSource().getLevel());
        treasury.all().forEach((item, amount) -> context.getSource().sendSuccess(
                () -> Component.literal(item + "=" + amount), false));
        return 1;
    }

    private static int withdrawTreasury(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String itemId = StringArgumentType.getString(context, "item");
        long amount = LongArgumentType.getLong(context, "amount");
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || amount > Integer.MAX_VALUE) {
            context.getSource().sendFailure(Component.literal("Invalid item or amount."));
            return 0;
        }
        TaxMaterialTreasurySavedData treasury = TaxMaterialTreasurySavedData.get(player.serverLevel());
        if (treasury.amount(itemId) < amount) {
            context.getSource().sendFailure(Component.literal("The tax treasury does not hold that amount."));
            return 0;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), (int) amount);
        java.util.List<ItemStack> before = player.getInventory().items.stream().map(ItemStack::copy).toList();
        treasury.withdraw(itemId, amount);
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            for (int index = 0; index < before.size(); index++) {
                player.getInventory().items.set(index, before.get(index));
            }
            player.getInventory().setChanged();
            treasury.deposit(itemId, amount, ServerConfig.MATERIAL_TAX_TREASURY_CAPACITY.get());
            context.getSource().sendFailure(Component.literal("Not enough inventory space."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Withdrew " + amount + " " + itemId + " from tax treasury."), true);
        return 1;
    }

    private static int collect(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        TaxService.collect(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.literal("Property tax collection completed. Rate=" + ServerConfig.PROPERTY_TAX_PER_BLOCK.get()),
                true
        );
        return 1;
    }
}
