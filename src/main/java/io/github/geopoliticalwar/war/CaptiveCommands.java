package io.github.geopoliticalwar.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.persistence.CaptivesSavedData;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.LaborSitesSavedData;
import io.github.geopoliticalwar.economy.LaborSiteService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Commands for occupied-population captive and liberation gameplay. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptiveCommands {
    private CaptiveCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("captive")
                                .then(Commands.literal("list").executes(CaptiveCommands::list))
                                .then(Commands.literal("status")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(CaptiveCommands::status)))
                                .then(Commands.literal("capture")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .then(Commands.argument("claim", UuidArgument.uuid())
                                                        .executes(CaptiveCommands::capture))))
                                .then(Commands.literal("liberate")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(CaptiveCommands::liberate)))
                                .then(Commands.literal("release")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(CaptiveCommands::liberate)))
                                .then(Commands.literal("rebel")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(CaptiveCommands::rebel)))
                                .then(Commands.literal("site")
                                        .then(Commands.literal("list").executes(CaptiveCommands::siteList))
                                        .then(Commands.literal("create")
                                                .then(Commands.argument("claim", UuidArgument.uuid())
                                                        .then(Commands.argument("item", StringArgumentType.word())
                                                                .then(Commands.argument("rate", LongArgumentType.longArg(1))
                                                                        .executes(CaptiveCommands::siteCreate)))))
                                        .then(Commands.literal("status")
                                                .then(Commands.argument("site", UuidArgument.uuid())
                                                        .executes(CaptiveCommands::siteStatus)))
                                        .then(Commands.literal("collect")
                                                .then(Commands.argument("site", UuidArgument.uuid())
                                                        .executes(CaptiveCommands::siteCollect))))
                        )
        );
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CaptivesSavedData.get(context.getSource().getLevel()).all().values().stream()
                .filter(value -> value.active())
                .forEach(value -> context.getSource().sendSuccess(
                        () -> Component.literal(value.player() + " claim=" + value.claim().value()
                                + " holding=" + value.holdingNation().value()
                                + " unrest=" + value.unrest() + " worked=" + value.workedSeconds()), false));
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        UUID player = UuidArgument.getUuid(context, "player");
        var record = CaptivesSavedData.get(context.getSource().getLevel()).get(player).orElse(null);
        if (record == null) {
            context.getSource().sendFailure(Component.literal("No captive record exists for that player."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Captive " + record.player() + " state=" + record.state()
                        + " claim=" + record.claim().value() + " holding=" + record.holdingNation().value()
                        + " former=" + record.formerNation().value() + " unrest=" + record.unrest()
                        + " worked=" + record.workedSeconds()), false);
        return 1;
    }

    private static int capture(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = actor.serverLevel().getServer().getPlayerList()
                .getPlayer(UuidArgument.getUuid(context, "player"));
        if (target == null) {
            context.getSource().sendFailure(Component.literal("The target must be online."));
            return 0;
        }
        ClaimId claim = new ClaimId(UuidArgument.getUuid(context, "claim"));
        try {
            CaptiveService.capture(actor.serverLevel(), actor, target, claim, System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Captive record created."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int liberate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            CaptiveService.liberate(actor.serverLevel(), UuidArgument.getUuid(context, "player"),
                    actor.getUUID(), System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Captive liberated."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int rebel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            CaptiveService.rebel(actor.serverLevel(), UuidArgument.getUuid(context, "player"),
                    actor.getUUID(), System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Captive rebellion triggered."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int siteList(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        LaborSitesSavedData.get(context.getSource().getLevel()).all().values().forEach(site ->
                context.getSource().sendSuccess(() -> Component.literal(
                        site.id() + " claim=" + site.claim().value() + " item=" + site.itemId()
                                + " rate=" + site.outputPerCycle() + " buffered=" + site.bufferedOutput()), false));
        return 1;
    }

    private static int siteCreate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            var site = LaborSiteService.create(
                    actor.serverLevel(), actor,
                    new ClaimId(UuidArgument.getUuid(context, "claim")),
                    StringArgumentType.getString(context, "item"),
                    LongArgumentType.getLong(context, "rate")
            );
            context.getSource().sendSuccess(() -> Component.literal("Labor site created: " + site.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int siteStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "site");
        var site = LaborSitesSavedData.get(context.getSource().getLevel()).get(id).orElse(null);
        if (site == null) {
            context.getSource().sendFailure(Component.literal("Unknown labor site."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                site.id() + " claim=" + site.claim().value() + " item=" + site.itemId()
                        + " buffered=" + site.bufferedOutput() + " rate=" + site.outputPerCycle()), false);
        return 1;
    }

    private static int siteCollect(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            long amount = LaborSiteService.collect(actor.serverLevel(), actor,
                    UuidArgument.getUuid(context, "site"));
            context.getSource().sendSuccess(() -> Component.literal("Collected " + amount + " labor output."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }
}
