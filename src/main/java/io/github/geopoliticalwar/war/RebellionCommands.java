package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RebellionsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RebellionCommands {
    private RebellionCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("rebellion")
                                .then(Commands.literal("start").executes(RebellionCommands::start))
                                .then(Commands.literal("propaganda").executes(RebellionCommands::propaganda))
                                .then(Commands.literal("sabotage").executes(RebellionCommands::sabotage))
                                .then(Commands.literal("status").executes(RebellionCommands::status))
                        )
        );
    }

    private static Nation nation(ServerPlayer player) {
        return NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .flatMap(id -> NationsSavedData.get(player.serverLevel()).get(id))
                .orElseThrow(() -> new IllegalArgumentException("You do not belong to a nation"));
    }

    private static int start(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = nation(player);
            var record = RebellionService.start(player.serverLevel(), nation, player.getUUID(), System.currentTimeMillis());
            context.getSource().sendSuccess(
                    () -> Component.literal("Rebellion cell established. Momentum=" + record.momentum()),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int propaganda(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return pressure(context, ServerConfig.PROPAGANDA_MOMENTUM.get(), "Propaganda spread");
    }

    private static int sabotage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return pressure(context, ServerConfig.SABOTAGE_MOMENTUM.get(), "Infrastructure sabotage completed");
    }

    private static int pressure(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            int amount,
            String action
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            var record = RebellionService.addPressure(
                    player.serverLevel(),
                    nation(player),
                    player.getUUID(),
                    amount,
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(
                    () -> Component.literal(action + ". Momentum=" + record.momentum() + "/" + ServerConfig.REBELLION_MOMENTUM_THRESHOLD.get()),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = nation(player);
            var record = RebellionsSavedData.get(player.serverLevel()).get(nation.id());
            context.getSource().sendSuccess(
                    () -> Component.literal(record.map(value ->
                            "Momentum=" + value.momentum() + "/" + ServerConfig.REBELLION_MOMENTUM_THRESHOLD.get()
                                    + " civilWar=" + value.civilWar()
                                    + " cells=" + value.rebelCells().size()
                    ).orElse("No active rebellion.")),
                    false
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }
}
