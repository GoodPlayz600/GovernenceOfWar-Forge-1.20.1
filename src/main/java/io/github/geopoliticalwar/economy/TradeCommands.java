package io.github.geopoliticalwar.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.governance.GovernmentAuthorityService;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeCommands {
    private TradeCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("trade")
                                .then(Commands.literal("tariff")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> {
                                                    long amount = LongArgumentType.getLong(context, "amount");
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal("Tariff: " + TradeService.tariff(amount)), false
                                                    );
                                                    return 1;
                                                })))
                                .then(Commands.literal("transfer")
                                        .then(Commands.argument("from", UuidArgument.uuid())
                                                .then(Commands.argument("to", UuidArgument.uuid())
                                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                                .executes(TradeCommands::transfer)))))
                        )
        );
    }

    private static int transfer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationId from = new NationId(UuidArgument.getUuid(context, "from"));
        NationId to = new NationId(UuidArgument.getUuid(context, "to"));
        var sourceNation = NationsSavedData.get(player.serverLevel()).get(from).orElse(null);
        if (sourceNation == null || !GovernmentAuthorityService.can(
                player.serverLevel(), sourceNation, player, AuthorityAction.MANAGE_TRADE)) {
            context.getSource().sendFailure(Component.literal("Only the source civilization leader may authorize trade."));
            return 0;
        }
        try {
            long amount = LongArgumentType.getLong(context, "amount");
            TradeService.transfer(player.serverLevel(), from, to, amount);
            context.getSource().sendSuccess(
                    () -> Component.literal("Trade settled. Tariff=" + TradeService.tariff(player.serverLevel(), from, amount)), true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }
}
