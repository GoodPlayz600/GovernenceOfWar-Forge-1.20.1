package io.github.geopoliticalwar.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.blockentity.StrategicLinkBlockEntity;
import io.github.geopoliticalwar.governance.GovernmentAuthorityService;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VaultCommands {
    private VaultCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("treasury")
                                .then(Commands.literal("balance")
                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                .executes(VaultCommands::balance)))
                                .then(Commands.literal("pay")
                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                .then(Commands.argument("player", UuidArgument.uuid())
                                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                                .executes(VaultCommands::pay)))))
                        )
                        .then(Commands.literal("vault")
                                .then(Commands.literal("bind")
                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                .executes(VaultCommands::bind)))
                                .then(Commands.literal("status").executes(VaultCommands::status))
                        )
        );
    }

    private static int balance(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NationId nation = nation(context, "nation");
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!canView(player, nation)) {
            context.getSource().sendFailure(Component.literal("You cannot view that treasury."));
            return 0;
        }
        try {
            long balance = TreasuryService.balance(player.serverLevel(), nation);
            context.getSource().sendSuccess(() -> Component.literal("Treasury balance: " + balance), false);
            return 1;
        } catch (RuntimeException failure) {
            return fail(context, failure);
        }
    }

    private static int pay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationId nation = nation(context, "nation");
        if (!canManage(player, nation)) {
            context.getSource().sendFailure(Component.literal("You cannot spend from that treasury."));
            return 0;
        }
        try {
            TreasuryService.pay(
                    player.serverLevel(),
                    nation,
                    UuidArgument.getUuid(context, "player"),
                    LongArgumentType.getLong(context, "amount"),
                    "nation treasury payment"
            );
            context.getSource().sendSuccess(() -> Component.literal("Treasury payment sent."), true);
            return 1;
        } catch (RuntimeException failure) {
            return fail(context, failure);
        }
    }

    private static int bind(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationId nation = nation(context, "nation");
        if (NationsSavedData.get(player.serverLevel()).get(nation).isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown nation."));
            return 0;
        }
        if (!canManage(player, nation)) {
            context.getSource().sendFailure(Component.literal("You cannot bind that nation to a vault."));
            return 0;
        }
        BlockPos vaultPosition = player.blockPosition().below();
        if (!(player.level().getBlockEntity(vaultPosition) instanceof StrategicLinkBlockEntity vault)
                || !(player.level().getBlockState(vaultPosition).is(ModContent.CAPITAL_VAULT.get())
                || player.level().getBlockState(vaultPosition).is(ModContent.RESERVE_VAULT.get()))) {
            context.getSource().sendFailure(Component.literal("Stand on a Capital Vault or Reserve Vault."));
            return 0;
        }
        if (vault.nation().isPresent() && !vault.nation().get().equals(nation.value())
                && !player.hasPermissions(2)) {
            context.getSource().sendFailure(Component.literal("This vault is already bound to another nation."));
            return 0;
        }
        vault.linkNation(nation.value());
        context.getSource().sendSuccess(() -> Component.literal("Vault bound to nation " + nation.value()), true);
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos vaultPosition = player.blockPosition().below();
        if (!(player.level().getBlockEntity(vaultPosition) instanceof StrategicLinkBlockEntity vault)
                || !(player.level().getBlockState(vaultPosition).is(ModContent.CAPITAL_VAULT.get())
                || player.level().getBlockState(vaultPosition).is(ModContent.RESERVE_VAULT.get()))) {
            context.getSource().sendFailure(Component.literal("Stand on a Capital Vault or Reserve Vault."));
            return 0;
        }
        Optional<UUID> boundNation = vault.nation();
        if (boundNation.isEmpty()) {
            context.getSource().sendFailure(Component.literal("This vault is not bound to a nation."));
            return 0;
        }
        NationId nation = new NationId(boundNation.get());
        if (!canView(player, nation)) {
            context.getSource().sendFailure(Component.literal("You cannot view that treasury."));
            return 0;
        }
        try {
            context.getSource().sendSuccess(
                    () -> Component.literal("Vault treasury balance: " + TreasuryService.balance(player.serverLevel(), nation)),
                    false
            );
            return 1;
        } catch (RuntimeException failure) {
            return fail(context, failure);
        }
    }

    private static boolean canView(ServerPlayer player, NationId nation) {
        return player.hasPermissions(2)
                || NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .filter(nation::equals)
                .flatMap(ignored -> NationMembershipService.status(player.serverLevel(), player.getUUID()))
                .filter(MembershipStatus.CITIZEN::equals)
                .isPresent();
    }

    private static boolean canManage(ServerPlayer player, NationId nation) {
        return NationsSavedData.get(player.serverLevel()).get(nation)
                .map(value -> GovernmentAuthorityService.can(
                        player.serverLevel(), value, player, AuthorityAction.MANAGE_TREASURY))
                .orElse(false);
    }

    private static NationId nation(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            String argument
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return new NationId(UuidArgument.getUuid(context, argument));
    }

    private static int fail(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            RuntimeException failure
    ) {
        context.getSource().sendFailure(Component.literal(failure.getMessage() == null ? "Treasury operation failed." : failure.getMessage()));
        return 0;
    }
}
