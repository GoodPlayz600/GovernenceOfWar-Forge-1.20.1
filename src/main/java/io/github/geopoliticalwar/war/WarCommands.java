package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.domain.war.WarPhase;
import io.github.geopoliticalwar.domain.war.WarSide;
import io.github.geopoliticalwar.governance.RolePermissionService;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Administrative phase commands used by the first vanilla war path. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WarCommands {
    private WarCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("war")
                                .requires(WarCommands::canManageWar)
                                .then(Commands.literal("start").executes(context -> set(context.getSource(), WarPhase.WAR)))
                                .then(Commands.literal("stop").executes(context -> set(context.getSource(), WarPhase.PEACE)))
                                .then(Commands.literal("rebellion").executes(context -> set(context.getSource(), WarPhase.REBELLION)))
                                 .then(Commands.literal("occupation").executes(context -> set(context.getSource(), WarPhase.OCCUPIED)))
                                 .then(Commands.literal("armistice").executes(context -> set(context.getSource(), WarPhase.ARMISTICE)))
                                 .then(Commands.literal("status").executes(context -> status(context.getSource())))
                                 .then(Commands.literal("declare")
                                         .then(Commands.argument("attacker", UuidArgument.uuid())
                                                 .then(Commands.argument("defender", UuidArgument.uuid())
                                                         .executes(WarCommands::declare))))
                                 .then(Commands.literal("activate")
                                         .then(Commands.argument("war", UuidArgument.uuid())
                                                 .executes(WarCommands::activate)))
                                 .then(Commands.literal("participant")
                                         .then(Commands.literal("add")
                                                 .then(Commands.argument("war", UuidArgument.uuid())
                                                         .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .then(Commands.argument("side", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                                         .executes(WarCommands::addParticipant))))))
                                 .then(Commands.literal("goal")
                                         .then(Commands.literal("claim")
                                                 .then(Commands.argument("war", UuidArgument.uuid())
                                                         .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .then(Commands.argument("claim", UuidArgument.uuid())
                                                                         .executes(WarCommands::addClaimGoal)))))
                                         .then(Commands.literal("capture")
                                                 .then(Commands.argument("war", UuidArgument.uuid())
                                                         .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .then(Commands.argument("point", UuidArgument.uuid())
                                                                          .executes(WarCommands::addCaptureGoal)))))
                                         .then(Commands.literal("complete")
                                                 .then(Commands.argument("war", UuidArgument.uuid())
                                                         .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .then(Commands.argument("claim", UuidArgument.uuid())
                                                                         .executes(WarCommands::completeClaimGoal)))))
                                 )
                                 .then(Commands.literal("resolve")
                                         .then(Commands.argument("war", UuidArgument.uuid())
                                                 .then(Commands.argument("victor", UuidArgument.uuid())
                                                         .executes(WarCommands::resolve))))
                                 .then(Commands.literal("settle")
                                         .then(Commands.argument("claim", UuidArgument.uuid())
                                                 .executes(WarCommands::settle)))
                         )
        );
    }

    private static int set(CommandSourceStack source, WarPhase phase) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        WarStateService.setPhase(level, phase);
        source.sendSuccess(
                () -> Component.literal("War phase for " + level.dimension().location() + " is now " + phase + "."),
                true
        );
        return 1;
    }

    private static boolean canManageWar(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        try {
            var player = source.getPlayerOrException();
            return RolePermissionService.has(
                    player.serverLevel(),
                    player,
                    "war.manage",
                    java.util.Optional.empty()
            );
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException notAPlayer) {
            return false;
        }
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        source.sendSuccess(
                () -> Component.literal("War phase for " + level.dimension().location() + ": " + WarStateService.phase(level)),
                false
        );
        return 1;
    }

    private static int declare(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = WarService.declare(
                    context.getSource().getLevel(),
                    nation(context, "attacker"),
                    nation(context, "defender"),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Declared war " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int activate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = WarService.activate(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Activated war " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int addParticipant(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            WarSide side = WarSide.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "side").toUpperCase(java.util.Locale.ROOT));
            var war = WarService.addParticipant(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    nation(context, "nation"),
                    side,
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Added participant to " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int addClaimGoal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = WarService.addClaimGoal(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    nation(context, "nation"),
                    new ClaimId(UuidArgument.getUuid(context, "claim")),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Added conquest goal to " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int addCaptureGoal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = WarService.addCaptureGoal(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    nation(context, "nation"),
                    UuidArgument.getUuid(context, "point"),
                    io.github.geopoliticalwar.ServerConfig.CAPTURE_HOLD_SECONDS.get()
            );
            context.getSource().sendSuccess(() -> Component.literal("Added capture goal to " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = ConquestService.resolve(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    nation(context, "victor"),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Resolved war " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int completeClaimGoal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var war = WarService.markClaimConquered(
                    context.getSource().getLevel(),
                    new WarId(UuidArgument.getUuid(context, "war")),
                    new ClaimId(UuidArgument.getUuid(context, "claim")),
                    nation(context, "nation")
            );
            context.getSource().sendSuccess(() -> Component.literal("Completed conquest goal in " + war.id().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static int settle(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var occupation = ConquestService.settle(
                    context.getSource().getLevel(),
                    new ClaimId(UuidArgument.getUuid(context, "claim")),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Settled occupation for " + occupation.claim().value()), true);
            return 1;
        } catch (RuntimeException failure) {
            return failure(context, failure);
        }
    }

    private static NationId nation(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            String argument
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return new NationId(UuidArgument.getUuid(context, argument));
    }

    private static int failure(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            RuntimeException failure
    ) {
        context.getSource().sendFailure(Component.literal(failure.getMessage() == null ? "War operation failed." : failure.getMessage()));
        return 0;
    }
}
