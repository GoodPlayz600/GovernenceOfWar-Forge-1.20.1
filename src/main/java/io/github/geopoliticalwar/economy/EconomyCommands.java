package io.github.geopoliticalwar.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Fixed-point economy commands for the vanilla-compatible foundation. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EconomyCommands {
    private EconomyCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("money")
                                 .then(Commands.literal("balance").executes(EconomyCommands::balance))
                                 .then(Commands.literal("withdraw")
                                         .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                 .executes(EconomyCommands::withdraw)))
                                 .then(Commands.literal("deposit").executes(EconomyCommands::deposit))
                                 .then(Commands.literal("pay")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                        .executes(EconomyCommands::pay))))
                                .then(Commands.literal("mint")
                                        .requires(source -> source.hasPermission(2))
                                 .then(Commands.argument("player", UuidArgument.uuid())
                                                 .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                         .executes(EconomyCommands::mint))))
                                  .then(Commands.literal("mint-payroll")
                                          .requires(source -> source.hasPermission(2))
                                          .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                  .executes(EconomyCommands::mintPayroll)))
                                  .then(Commands.literal("mint-nation")
                                          .requires(source -> source.hasPermission(2))
                                          .then(Commands.argument("nation", UuidArgument.uuid())
                                                 .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                         .executes(EconomyCommands::mintNation))))
                                 .then(Commands.literal("supply").requires(source -> source.hasPermission(2))
                                         .executes(EconomyCommands::supply))
                                 .then(Commands.literal("audit")
                                         .requires(source -> source.hasPermission(2))
                                         .executes(EconomyCommands::audit))
                        )
        );
    }

    private static CurrencyId currency() {
        return new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
    }

    private static int balance(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EconomySavedData economy = EconomySavedData.get(player.serverLevel());
        long balance = economy.balance(currency(), playerAccount(player.getUUID()));
        context.getSource().sendSuccess(
                () -> Component.literal("Balance: " + balance + " " + currency().value()),
                false
        );
        return 1;
    }

    private static int pay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        UUID destination = UuidArgument.getUuid(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        try {
            EconomySavedData.get(sender.serverLevel()).transfer(
                    currency(),
                    playerAccount(sender.getUUID()),
                    playerAccount(destination),
                    amount,
                    "player payment"
            );
            context.getSource().sendSuccess(() -> Component.literal("Payment sent."), false);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int withdraw(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long amount = LongArgumentType.getLong(context, "amount");
        try {
            PhysicalCurrencyService.withdraw(player, amount);
            context.getSource().sendSuccess(() -> Component.literal("Withdrew " + amount + " " + currency().value() + " as issued cash."), false);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int deposit(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            long amount = PhysicalCurrencyService.depositHeld(player);
            context.getSource().sendSuccess(() -> Component.literal("Deposited " + amount + " " + currency().value() + "."), false);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int mint(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID destination = UuidArgument.getUuid(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        try {
            EconomySavedData.get(context.getSource().getLevel()).mint(
                    currency(),
                    playerAccount(destination),
                    amount,
                    "administrator mint"
            );
            context.getSource().sendSuccess(() -> Component.literal("Currency minted."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int mintPayroll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        long amount = LongArgumentType.getLong(context, "amount");
        return mintAccount(context, new AccountId("system:payroll"), amount, "administrator payroll funding");
    }

    private static int mintNation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        java.util.UUID nation = UuidArgument.getUuid(context, "nation");
        long amount = LongArgumentType.getLong(context, "amount");
        if (NationsSavedData.get(context.getSource().getLevel())
                .get(new io.github.geopoliticalwar.domain.governance.NationId(nation)).isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown nation."));
            return 0;
        }
        return mintAccount(context, new AccountId("nation:" + nation), amount, "administrator nation treasury funding");
    }

    private static int mintAccount(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            AccountId destination,
            long amount,
            String reason
    ) {
        try {
            EconomySavedData.get(context.getSource().getLevel()).mint(currency(), destination, amount, reason);
            context.getSource().sendSuccess(() -> Component.literal("Currency minted."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int supply(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        EconomySavedData economy = EconomySavedData.get(context.getSource().getLevel());
        long supply = economy.currency(currency()).map(value -> value.supply()).orElse(0L);
        context.getSource().sendSuccess(
                () -> Component.literal("Supply: " + supply + " " + currency().value()
                        + " purchasing_power_bps=" + economy.currency(currency()).map(value -> value.purchasingPowerBps()).orElse(0L)),
                false
        );
        return 1;
    }

    private static int audit(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        EconomySavedData.get(context.getSource().getLevel()).transactions().stream()
                .skip(Math.max(0, EconomySavedData.get(context.getSource().getLevel()).transactions().size() - 10L))
                .forEach(transaction -> context.getSource().sendSuccess(
                        () -> Component.literal(transaction.timestampMillis() + " " + transaction.reason()
                                + " " + transaction.from().value() + " -> " + transaction.to().value()
                                + " amount=" + transaction.amount()),
                        false
                ));
        return 1;
    }

    private static AccountId playerAccount(UUID player) {
        return new AccountId("player:" + player);
    }
}
