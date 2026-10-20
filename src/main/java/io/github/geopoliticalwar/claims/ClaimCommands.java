package io.github.geopoliticalwar.claims;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.BlockBox;
import io.github.geopoliticalwar.domain.claim.BlockPoint;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.claim.PermissionEffect;
import io.github.geopoliticalwar.domain.claim.PermissionNode;
import io.github.geopoliticalwar.domain.claim.PermissionPolicy;
import io.github.geopoliticalwar.domain.claim.PermissionRule;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.claim.ZoneType;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.governance.RolePermissionService;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;

import java.util.Locale;
import java.util.HashSet;
import java.util.UUID;

/** Commands for the first usable volumetric-claim workflow. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimCommands {
    private ClaimCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> claim = Commands.literal("claim")
                .then(Commands.literal("select1").executes(ClaimCommands::selectFirst))
                .then(Commands.literal("select2").executes(ClaimCommands::selectSecond))
                .then(Commands.literal("clear").executes(ClaimCommands::clearSelection))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("zone", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(ZoneType.values()).map(Enum::name),
                                                builder
                                        ))
                                        .executes(ClaimCommands::createClaim)
                                        .then(Commands.argument("nation", UuidArgument.uuid())
                                                .executes(ClaimCommands::createClaim)))))
                .then(Commands.literal("list").executes(ClaimCommands::listClaims))
                .then(Commands.literal("info").executes(ClaimCommands::infoAtPlayer))
                .then(Commands.literal("grant")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .executes(context -> changePermission(context, PermissionEffect.ALLOW, false))))))
                .then(Commands.literal("deny")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .executes(context -> changePermission(context, PermissionEffect.DENY, false))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .executes(context -> changePermission(context, null, true))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(ClaimCommands::deleteClaim)));

        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(claim)
        );
        event.getDispatcher().register(
                Commands.literal("gwar")
                        .then(Commands.literal("claim").redirect(
                                event.getDispatcher().getRoot().getChild("geopoliticalwar").getChild("claim")
                        ))
        );
    }

    private static int selectFirst(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return select(context, 1);
    }

    private static int selectSecond(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return select(context, 2);
    }

    private static int select(CommandContext<CommandSourceStack> context, int corner)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ClaimsSavedData data = ClaimsSavedData.get(level);
        BlockPos position = player.blockPosition();
        data.setSelection(
                player.getUUID(),
                corner,
                new DimensionId(level.dimension().location().toString()),
                new BlockPoint(position.getX(), position.getY(), position.getZ())
        );
        context.getSource().sendSuccess(
                () -> Component.literal("Claim corner " + corner + " set at " + position),
                false
        );
        return 1;
    }

    private static int clearSelection(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimsSavedData.get(player.serverLevel()).clearSelection(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Claim selection cleared."), false);
        return 1;
    }

    private static int createClaim(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ClaimsSavedData data = ClaimsSavedData.get(level);
        ClaimsSavedData.Selection selection = data.selection(player.getUUID()).orElse(null);
        if (selection == null || selection.first().isEmpty() || selection.second().isEmpty()) {
            context.getSource().sendFailure(Component.literal("Set both corners with claim select1 and claim select2 first."));
            return 0;
        }
        DimensionId currentDimension = new DimensionId(level.dimension().location().toString());
        if (!currentDimension.equals(selection.dimension())) {
            context.getSource().sendFailure(Component.literal("Both claim corners must be in the current dimension."));
            return 0;
        }

        BlockBox volume = BlockBox.fromInclusiveCorners(selection.first().get(), selection.second().get());
        if (volume.volume() > ServerConfig.MAX_CLAIM_VOLUME.get()) {
            context.getSource().sendFailure(Component.literal(
                    "Claim volume " + volume.volume() + " exceeds the configured maximum "
                            + ServerConfig.MAX_CLAIM_VOLUME.get() + "."
            ));
            return 0;
        }

        java.util.Optional<NationId> nation = nationArgument(context);
        PrincipalId owner;
        if (nation.isPresent()) {
            boolean citizen = NationMembershipService.nationFor(level, player.getUUID())
                    .filter(nation.get()::equals)
                    .flatMap(ignored -> NationMembershipService.status(level, player.getUUID()))
                    .filter(MembershipStatus.CITIZEN::equals)
                    .isPresent();
            boolean administrator = NationsSavedData.get(level).get(nation.get())
                    .filter(value -> value.isLeader(player.getUUID()))
                    .isPresent()
                    || RolePermissionService.has(level, player, "claim.manage", nation);
            if (!citizen || !administrator) {
                context.getSource().sendFailure(Component.literal(
                        "You must be a nation leader or claim manager to create a nation claim."
                ));
                return 0;
            }
            owner = new PrincipalId("nation:" + nation.get().value());
        } else {
            owner = ClaimAccessService.playerPrincipal(player);
        }
        long ownedClaims = data.all().values().stream()
                .filter(claim -> claim.owner().equals(owner))
                .count();
        int capacity = nation.map(value -> ClaimCapacityService.nationCapacity(level, value))
                .orElse(ServerConfig.MAX_CLAIMS_PER_NATION.get());
        if (ownedClaims >= capacity) {
            context.getSource().sendFailure(Component.literal(
                    "You have reached the claim capacity (" + ownedClaims + "/" + capacity
                            + "). Population increases administrative capacity."
            ));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        ZoneType zone;
        try {
            zone = ZoneType.valueOf(StringArgumentType.getString(context, "zone").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidZone) {
            context.getSource().sendFailure(Component.literal("Unknown zone type."));
            return 0;
        }

        Claim claim = Claim.root(
                ClaimId.random(),
                name,
                currentDimension,
                volume,
                owner,
                zone
        );
        try {
            data.add(claim);
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Claim could not be created: " + failure.getMessage()));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Created " + claim.name() + " (" + claim.id().value() + ")"),
                true
        );
        return 1;
    }

    private static java.util.Optional<NationId> nationArgument(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean hasNation = context.getNodes().stream()
                .anyMatch(node -> "nation".equals(node.getNode().getName()));
        if (!hasNation) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new NationId(UuidArgument.getUuid(context, "nation")));
    }

    private static int listClaims(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var owner = ClaimAccessService.playerPrincipal(player);
        boolean administrator = context.getSource().hasPermission(2);
        ClaimsSavedData.get(player.serverLevel()).all().values().stream()
                .filter(claim -> administrator || claim.owner().equals(owner)
                        || ClaimAccessService.canManageClaim(player, player.serverLevel(), claim))
                .sorted((first, second) -> first.name().compareToIgnoreCase(second.name()))
                .forEach(claim -> context.getSource().sendSuccess(
                        () -> Component.literal(
                                claim.name() + " " + claim.id().value() + " " + claim.zone()
                                        + " " + claim.dimension().value()
                        ),
                        false
                ));
        return 1;
    }

    private static int infoAtPlayer(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        ClaimsSavedData data = ClaimsSavedData.get(level);
        var ids = data.containing(
                new DimensionId(level.dimension().location().toString()),
                position.getX(),
                position.getY(),
                position.getZ()
        );
        if (ids.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("This block is unclaimed."), false);
        } else {
            ids.stream().map(data::get).flatMap(java.util.Optional::stream).forEach(claim ->
                    context.getSource().sendSuccess(
                            () -> Component.literal(
                                    claim.name() + " | zone=" + claim.zone()
                                            + " | owner=" + claim.owner().value()
                                            + " | bounds=" + claim.volume()
                            ),
                            false
                    )
            );
        }
        return 1;
    }

    private static int deleteClaim(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID uuid = UuidArgument.getUuid(context, "id");
        ClaimsSavedData data = ClaimsSavedData.get(player.serverLevel());
        Claim claim = data.get(new ClaimId(uuid)).orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown claim."));
            return 0;
        }
        if (!context.getSource().hasPermission(2)
                && io.github.geopoliticalwar.persistence.OccupationsSavedData.get(player.serverLevel()).get(claim.id())
                .filter(occupation -> occupation.settledAtMillis().isEmpty()).isPresent()) {
            context.getSource().sendFailure(Component.literal("Occupied claims cannot be deleted during settlement.") );
            return 0;
        }
        if (io.github.geopoliticalwar.persistence.WarsSavedData.get(player.serverLevel()).referencesClaim(claim.id())) {
            context.getSource().sendFailure(Component.literal("A formal war references this claim."));
            return 0;
        }
        if (!ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not own this claim."));
            return 0;
        }
        ClaimLifecycleService.delete(player.serverLevel(), new ClaimId(uuid));
        context.getSource().sendSuccess(() -> Component.literal("Deleted claim " + claim.name() + "."), true);
        return 1;
    }

    private static int changePermission(
            CommandContext<CommandSourceStack> context,
            PermissionEffect effect,
            boolean revoke
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimsSavedData data = ClaimsSavedData.get(player.serverLevel());
        ClaimId claimId = new ClaimId(UuidArgument.getUuid(context, "id"));
        Claim claim = data.get(claimId).orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("Unknown claim."));
            return 0;
        }
        if (!ClaimAccessService.canManageClaim(player, player.serverLevel(), claim)) {
            context.getSource().sendFailure(Component.literal("You do not manage this claim."));
            return 0;
        }

        PermissionRule target = new PermissionRule(
                new PrincipalId(StringArgumentType.getString(context, "subject")),
                new PermissionNode(StringArgumentType.getString(context, "permission")),
                effect == null ? PermissionEffect.ALLOW : effect
        );
        HashSet<PermissionRule> rules = new HashSet<>(claim.permissions().rules());
        rules.removeIf(rule -> rule.subject().equals(target.subject()) && rule.node().equals(target.node()));
        if (!revoke) {
            rules.add(target);
        }
        data.replace(claim.withPermissions(new PermissionPolicy(rules)));
        context.getSource().sendSuccess(
                () -> Component.literal(revoke ? "Permission revoked." : "Permission updated."),
                true
        );
        return 1;
    }
}
