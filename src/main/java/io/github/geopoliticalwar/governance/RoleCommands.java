package io.github.geopoliticalwar.governance;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoleCommands {
    private RoleCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("role")
                                .then(Commands.literal("create")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                        .then(Commands.argument("display", StringArgumentType.word())
                                                                .then(Commands.argument("wage", LongArgumentType.longArg(0))
                                                                        .executes(RoleCommands::create)
                                                                        .then(Commands.argument("permissions", StringArgumentType.greedyString())
                                                                                .executes(RoleCommands::create))))))
                                .then(Commands.literal("assign")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .executes(RoleCommands::assignGlobal)
                                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                                .executes(RoleCommands::assignNation)))))
                                 .then(Commands.literal("assign-nation")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .executes(RoleCommands::assignNationManaged)))))
                                 .then(Commands.literal("revoke-nation")
                                         .then(Commands.argument("player", UuidArgument.uuid())
                                                 .then(Commands.argument("role", StringArgumentType.word())
                                                         .then(Commands.argument("nation", UuidArgument.uuid())
                                                                 .executes(RoleCommands::revokeNationManaged)))))
                                .then(Commands.literal("revoke")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .then(Commands.argument("role", StringArgumentType.word())
                                                        .executes(RoleCommands::revokeGlobal)
                                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                                .executes(RoleCommands::revokeNation)))))
                                .then(Commands.literal("list").executes(RoleCommands::list))
                                .then(Commands.literal("self").executes(RoleCommands::self))
                        )
        );
    }

    private static int create(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String display = StringArgumentType.getString(context, "display");
        long wage = LongArgumentType.getLong(context, "wage");
        Set<String> permissions = permissionArgument(context);
        try {
            RolesSavedData.get(context.getSource().getLevel()).define(new RoleDefinition(
                    id,
                    display,
                    "role:" + id,
                    permissions,
                    wage,
                    Optional.empty()
            ));
            context.getSource().sendSuccess(() -> Component.literal("Role created."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static Set<String> permissionArgument(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context
    ) {
        try {
            String value = StringArgumentType.getString(context, "permissions");
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(permission -> !permission.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException missingArgument) {
            return Set.of();
        }
    }

    private static int assignGlobal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return assign(context, Optional.empty());
    }

    private static int assignNation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return assign(context, Optional.of(new NationId(UuidArgument.getUuid(context, "nation"))));
    }

    private static int assignNationManaged(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer manager = context.getSource().getPlayerOrException();
        NationId nation = new NationId(UuidArgument.getUuid(context, "nation"));
        var targetNation = io.github.geopoliticalwar.persistence.NationsSavedData.get(manager.serverLevel()).get(nation)
                .orElseThrow(() -> new IllegalArgumentException("Unknown nation"));
        if (!GovernmentAuthorityService.can(manager.serverLevel(), targetNation, manager, AuthorityAction.MANAGE_ROLES)) {
            context.getSource().sendFailure(Component.literal("You cannot manage roles for that nation."));
            return 0;
        }
        return assign(context, Optional.of(nation));
    }

    private static int assign(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            Optional<NationId> nation
    ) {
        UUID player = UuidArgument.getUuid(context, "player");
        String role = StringArgumentType.getString(context, "role");
        try {
            if (nation.isPresent()) {
                if (!NationMembershipService.mayHoldOffice(
                        context.getSource().getLevel(), player, nation.get())) {
                    throw new IllegalArgumentException("Player is not an eligible citizen of that nation");
                }
            }
            RolesSavedData.get(context.getSource().getLevel()).assign(new RoleAssignment(player, role, nation));
            context.getSource().sendSuccess(() -> Component.literal("Role assigned."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int revokeGlobal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return revoke(context, Optional.empty());
    }

    private static int revokeNation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return revoke(context, Optional.of(new NationId(UuidArgument.getUuid(context, "nation"))));
    }

    private static int revokeNationManaged(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer manager = context.getSource().getPlayerOrException();
        NationId nationId = new NationId(UuidArgument.getUuid(context, "nation"));
        var nation = NationsSavedData.get(manager.serverLevel()).get(nationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown nation"));
        if (!GovernmentAuthorityService.can(manager.serverLevel(), nation, manager, AuthorityAction.MANAGE_ROLES)) {
            context.getSource().sendFailure(Component.literal("You cannot manage roles for that nation."));
            return 0;
        }
        return revoke(context, Optional.of(nationId));
    }

    private static int revoke(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            Optional<NationId> nation
    ) {
        UUID player = UuidArgument.getUuid(context, "player");
        String role = StringArgumentType.getString(context, "role");
        boolean removed = RolesSavedData.get(context.getSource().getLevel()).unassign(player, role, nation);
        if (removed) {
            context.getSource().sendSuccess(() -> Component.literal("Role revoked."), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("That role assignment does not exist."));
        return 0;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        RolesSavedData.get(context.getSource().getLevel()).definitions().values().forEach(role ->
                context.getSource().sendSuccess(
                        () -> Component.literal(role.id() + " " + role.displayName() + " wage=" + role.wagePerCycle()
                                + " permissions=" + String.join(",", role.permissions())),
                        false
                )
        );
        return 1;
    }

    private static int self(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RolesSavedData roles = RolesSavedData.get(player.serverLevel());
        roles.assignments(player.getUUID()).forEach(assignment ->
                context.getSource().sendSuccess(
                        () -> Component.literal(
                                assignment.roleId() + " nation=" + assignment.nation().map(value -> value.value().toString()).orElse("global")
                        ),
                        false
                )
        );
        return 1;
    }
}
