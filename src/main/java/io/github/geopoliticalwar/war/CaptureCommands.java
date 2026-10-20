package io.github.geopoliticalwar.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.BlockPoint;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.domain.war.CapturePoint;
import io.github.geopoliticalwar.persistence.CapturePointsSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureCommands {
    private CaptureCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("capture")
                                .then(Commands.literal("create")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(CaptureCommands::create)))
                                .then(Commands.literal("list").executes(CaptureCommands::list))
                                .then(Commands.literal("status").executes(CaptureCommands::status))
                                .then(Commands.literal("delete")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("id", UuidArgument.uuid())
                                                .executes(CaptureCommands::delete)))
                        )
        );
    }

    private static int create(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var position = player.blockPosition();
        Optional<NationId> owner = NationMembershipService.nationFor(player.serverLevel(), player.getUUID());
        CapturePoint point = new CapturePoint(
                UUID.randomUUID(),
                StringArgumentType.getString(context, "name"),
                new DimensionId(player.serverLevel().dimension().location().toString()),
                new BlockPoint(position.getX(), position.getY(), position.getZ()),
                ServerConfig.CAPTURE_RADIUS_BLOCKS.get(),
                owner,
                0L,
                System.currentTimeMillis()
        );
        CapturePointsSavedData.get(player.serverLevel()).add(point);
        context.getSource().sendSuccess(
                () -> Component.literal("Created capture point " + point.name() + " with ID " + point.id()),
                true
        );
        return 1;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CapturePointsSavedData.get(context.getSource().getLevel()).all().values().forEach(point ->
                context.getSource().sendSuccess(
                        () -> Component.literal(point.name() + " " + point.id() + " owner=" + point.owner()),
                        false
                )
        );
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CapturePointsSavedData.get(player.serverLevel()).all().values().stream()
                .filter(point -> point.contains(player.getX(), player.getY(), player.getZ()))
                .forEach(point -> context.getSource().sendSuccess(
                        () -> Component.literal(
                                point.name() + " progress=" + point.progressSeconds()
                                        + "/" + ServerConfig.CAPTURE_HOLD_SECONDS.get()
                                        + " owner=" + point.owner()
                                        + " war=" + WarService.activeForCapturePoint(player.serverLevel(), point.id())
                        ),
                        false
                ));
        return 1;
    }

    private static int delete(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        if (io.github.geopoliticalwar.persistence.WarsSavedData.get(context.getSource().getLevel()).all().values().stream()
                .anyMatch(war -> war.goals().stream().anyMatch(goal -> goal.capturePoint().filter(id::equals).isPresent()))) {
            context.getSource().sendFailure(Component.literal("A formal war targets this capture point."));
            return 0;
        }
        boolean removed = CapturePointsSavedData.get(context.getSource().getLevel()).remove(id);
        context.getSource().sendSuccess(
                () -> Component.literal(removed ? "Capture point deleted." : "Unknown capture point."),
                true
        );
        return removed ? 1 : 0;
    }
}
