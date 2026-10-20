package io.github.geopoliticalwar.governance;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.Election;
import io.github.geopoliticalwar.domain.governance.AdmissionPolicy;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.NationRequest;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import io.github.geopoliticalwar.persistence.GovernmentSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.NationMembershipSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import io.github.geopoliticalwar.economy.TreasuryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** Nation, citizenship, and election commands for the first governance slice. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NationCommands {
    private NationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("nation")
                                .then(Commands.literal("create")
                                         .then(Commands.argument("name", StringArgumentType.string())
                                                .then(Commands.argument("regime", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(RegimeType.values()).map(Enum::name),
                                                                builder
                                                        ))
                                                        .executes(NationCommands::create))))
                                .then(Commands.literal("list").executes(NationCommands::list))
                                 .then(Commands.literal("info").executes(NationCommands::info)
                                         .then(Commands.argument("id", UuidArgument.uuid()).executes(NationCommands::infoTarget)))
                                 .then(Commands.literal("join")
                                         .then(Commands.argument("id", UuidArgument.uuid())
                                                  .executes(NationCommands::join)))
                                 .then(Commands.literal("immigrate")
                                         .then(Commands.argument("id", UuidArgument.uuid())
                                                 .executes(NationCommands::immigrate)
                                                 .then(Commands.argument("status", StringArgumentType.word())
                                                         .executes(NationCommands::immigrate))))
                                .then(Commands.literal("apply")
                                        .then(Commands.argument("id", UuidArgument.uuid())
                                                .executes(NationCommands::apply)
                                                .then(Commands.argument("status", StringArgumentType.word())
                                                        .executes(NationCommands::apply))))
                                .then(Commands.literal("invite")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(NationCommands::invite)
                                                .then(Commands.argument("status", StringArgumentType.word())
                                                        .executes(NationCommands::invite))))
                                .then(Commands.literal("requests").executes(NationCommands::requests))
                                .then(Commands.literal("accept")
                                        .then(Commands.literal("invite")
                                                .then(Commands.argument("request", UuidArgument.uuid())
                                                        .executes(NationCommands::acceptInvite)))
                                        .then(Commands.literal("application")
                                                .then(Commands.argument("request", UuidArgument.uuid())
                                                        .executes(NationCommands::acceptApplication))))
                                .then(Commands.literal("decline")
                                        .then(Commands.argument("request", UuidArgument.uuid())
                                                .executes(NationCommands::decline)))
                                .then(Commands.literal("admission")
                                        .then(Commands.argument("policy", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(AdmissionPolicy.values()).map(Enum::name), builder
                                                ))
                                                .executes(NationCommands::admission)))
                                .then(Commands.literal("transfer")
                                        .then(Commands.argument("player", UuidArgument.uuid())
                                                .executes(NationCommands::transfer)))
                                 .then(Commands.literal("status").executes(NationCommands::status))
                                 .then(Commands.literal("government").executes(NationCommands::government))
                                .then(Commands.literal("leave").executes(NationCommands::leave))
                                .then(Commands.literal("regime")
                                        .then(Commands.argument("regime", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(RegimeType.values()).map(Enum::name),
                                                        builder
                                                ))
                                                .executes(NationCommands::changeRegime)))
                                .then(Commands.literal("election")
                                        .then(Commands.literal("open").executes(NationCommands::openElection))
                                        .then(Commands.literal("stand").executes(NationCommands::stand))
                                        .then(Commands.literal("vote")
                                                .then(Commands.argument("candidate", UuidArgument.uuid())
                                                        .executes(NationCommands::vote)))
                                        .then(Commands.literal("status").executes(NationCommands::electionStatus))
                                        .then(Commands.literal("finalize").executes(NationCommands::finalizeElection))
                                )
                        )
         );
        event.getDispatcher().register(
                Commands.literal("gwar")
                        .then(Commands.literal("nation").redirect(
                                event.getDispatcher().getRoot().getChild("geopoliticalwar").getChild("nation")
                        ))
        );
    }

    private static int create(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        RegimeType regime;
        try {
            regime = RegimeType.valueOf(StringArgumentType.getString(context, "regime").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal("Unknown regime."));
            return 0;
        }

        try {
            if (NationMembershipService.nationFor(player.serverLevel(), player.getUUID()).isPresent()) {
                throw new IllegalArgumentException("Player already belongs to a civilization");
            }
            Nation nation = NationsSavedData.get(player.serverLevel()).create(
                    name,
                    player.getUUID(),
                    regime,
                    "#3f76e4"
            );
            NationMembershipService.initializeFounder(player.serverLevel(), nation);
            context.getSource().sendSuccess(
                    () -> Component.literal("Created nation " + nation.name() + " with ID " + nation.id().value()),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Nation could not be created: " + failure.getMessage()));
            return 0;
        }
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        NationsSavedData.get(level).all().values().stream()
                .sorted((first, second) -> first.name().compareToIgnoreCase(second.name()))
                .forEach(nation -> context.getSource().sendSuccess(
                        () -> Component.literal(nation.name() + " " + nation.id().value() + " " + nation.regime()
                                + " admission=" + NationMembershipSavedData.get(level).policy(nation.id())),
                        false
                ));
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Nation nation = NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .flatMap(id -> NationsSavedData.get(player.serverLevel()).get(id))
                .orElse(null);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("You do not belong to a nation."));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal(
                        nation.name() + " | id=" + nation.id().value()
                                + " | regime=" + nation.regime()
                                + " | leader=" + nation.leader()
                                + " | citizens=" + nation.citizens().size()
                                + " | population=" + PopulationService.population(player.serverLevel(), nation.id())
                ),
                false
        );
        return 1;
    }

    private static int infoTarget(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NationId id = new NationId(UuidArgument.getUuid(context, "id"));
        Nation nation = NationsSavedData.get(context.getSource().getLevel()).get(id).orElse(null);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Unknown nation."));
            return 0;
        }
        AdmissionPolicy policy = NationMembershipSavedData.get(context.getSource().getLevel()).policy(id);
        context.getSource().sendSuccess(
                        () -> Component.literal(nation.name() + " | id=" + id.value() + " | regime=" + nation.regime()
                                + " | admission=" + policy + " | leader=" + nation.leader()
                                + " | citizens=" + nation.citizens().size()
                                + " | population=" + PopulationService.population(context.getSource().getLevel(), id)),
                false
        );
        return 1;
    }

    private static int join(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            NationMembershipService.joinOpen(
                    player.serverLevel(),
                    new NationId(UuidArgument.getUuid(context, "id")),
                    player.getUUID(),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Joined nation."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Could not join nation: " + failure.getMessage()));
            return 0;
        }
    }

    private static int immigrate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            NationMembershipService.immigrate(
                    player.serverLevel(),
                    new NationId(UuidArgument.getUuid(context, "id")),
                    player.getUUID(),
                    requestedStatus(context),
                    System.currentTimeMillis()
            ).ifPresentOrElse(
                    request -> context.getSource().sendSuccess(
                            () -> Component.literal("Immigration application submitted: " + request.id()), true),
                    () -> context.getSource().sendSuccess(() -> Component.literal("Immigration completed."), true)
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Immigration failed: " + failure.getMessage()));
            return 0;
        }
    }

    private static int leave(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            NationMembershipService.leave(player.serverLevel(), player.getUUID());
            context.getSource().sendSuccess(() -> Component.literal("Left nation."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Could not leave nation: " + failure.getMessage()));
            return 0;
        }
    }

    private static int apply(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            NationRequest request = NationMembershipService.apply(
                    player.serverLevel(),
                    new NationId(UuidArgument.getUuid(context, "id")),
                    player.getUUID(),
                    requestedStatus(context),
                    System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Application submitted: " + request.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int invite(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            NationRequest request = NationMembershipService.invite(
                    player.serverLevel(), nation.id(), UuidArgument.getUuid(context, "player"),
                    requestedStatus(context), player.getUUID(), System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Invitation created: " + request.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int requests(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationMembershipSavedData data = NationMembershipSavedData.get(player.serverLevel());
        long now = System.currentTimeMillis();
        data.expireRequests(now);
        data.requests().values().stream()
                .filter(request -> request.pending(now)
                        && (request.player().equals(player.getUUID())
                        || NationsSavedData.get(player.serverLevel()).get(request.nation())
                        .map(nation -> NationMembershipService.canManage(player.serverLevel(), nation, player.getUUID()))
                        .orElse(false)))
                .forEach(request -> context.getSource().sendSuccess(
                        () -> Component.literal(request.id() + " " + request.type() + " nation=" + request.nation().value()
                                + " player=" + request.player() + " status=" + request.requestedStatus()),
                        false
                ));
        return 1;
    }

    private static int acceptInvite(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return acceptRequest(context, false);
    }

    private static int acceptApplication(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return acceptRequest(context, true);
    }

    private static int acceptRequest(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            boolean application
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            UUID request = UuidArgument.getUuid(context, "request");
            if (application) {
                NationMembershipService.acceptApplication(player.serverLevel(), request, player.getUUID(), System.currentTimeMillis());
            } else {
                NationMembershipService.accept(player.serverLevel(), request, player.getUUID(), System.currentTimeMillis());
            }
            context.getSource().sendSuccess(() -> Component.literal("Nation request accepted."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int decline(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            NationMembershipService.decline(
                    player.serverLevel(), UuidArgument.getUuid(context, "request"),
                    player.getUUID(), System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Nation request declined."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int admission(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            AdmissionPolicy policy = AdmissionPolicy.valueOf(StringArgumentType.getString(context, "policy").toUpperCase(Locale.ROOT));
            NationMembershipService.setPolicy(player.serverLevel(), nation.id(), policy, player.getUUID());
            context.getSource().sendSuccess(() -> Component.literal("Admission policy changed to " + policy + "."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int transfer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            Nation updated = NationMembershipService.transferLeadership(
                    player.serverLevel(), nation.id(), player.getUUID(),
                    UuidArgument.getUuid(context, "player"), System.currentTimeMillis()
            );
            context.getSource().sendSuccess(() -> Component.literal("Leadership transferred to " + updated.leader()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationId nation = NationMembershipService.nationFor(player.serverLevel(), player.getUUID()).orElse(null);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("You do not belong to a civilization."));
            return 0;
        }
        MembershipStatus status = NationMembershipService.membership(player.serverLevel(), player.getUUID())
                .map(value -> value.status()).orElse(MembershipStatus.CITIZEN);
        context.getSource().sendSuccess(
                () -> Component.literal("Civilization=" + nation.value() + " membership=" + status
                        + " admission=" + NationMembershipSavedData.get(player.serverLevel()).policy(nation)
                        + " population=" + PopulationService.population(player.serverLevel(), nation)),
                false
        );
        return 1;
    }

    private static int government(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Nation nation = requireNation(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "Government " + nation.name() + " regime=" + nation.regime()
                        + " population=" + PopulationService.population(player.serverLevel(), nation.id())
                        + " treasury=" + TreasuryService.balance(player.serverLevel(), nation.id())
                        + " leader=" + nation.leader()), false);
        RolesSavedData.get(player.serverLevel()).allAssignments().stream()
                .filter(assignment -> assignment.nation().filter(nation.id()::equals).isPresent())
                .forEach(assignment -> context.getSource().sendSuccess(
                        () -> Component.literal("official=" + assignment.player() + " role=" + assignment.roleId()), false));
        context.getSource().sendSuccess(() -> Component.literal(
                nation.regime() == RegimeType.DEMOCRACY
                        ? "Authority: elections and eligible citizen delegation"
                        : "Authority: leader-directed appointments with delegated operations"), false);
        return 1;
    }

    private static MembershipStatus requestedStatus(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context
    ) {
        boolean supplied = context.getNodes().stream()
                .anyMatch(node -> "status".equals(node.getNode().getName()));
        if (!supplied) {
            return MembershipStatus.CITIZEN;
        }
        try {
            return MembershipStatus.valueOf(StringArgumentType.getString(context, "status").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException missingOrInvalid) {
            throw new IllegalArgumentException("Unknown membership status. Use CITIZEN or RESIDENT.");
        }
    }

    private static int changeRegime(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NationsSavedData nations = NationsSavedData.get(player.serverLevel());
        Nation nation = NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .flatMap(id -> nations.get(id))
                .orElse(null);
        if (nation == null || (!nation.isLeader(player.getUUID()) && !context.getSource().hasPermission(2))) {
            context.getSource().sendFailure(Component.literal("Only the nation leader or an administrator may change regimes."));
            return 0;
        }
        try {
            RegimeType regime = RegimeType.valueOf(StringArgumentType.getString(context, "regime").toUpperCase(Locale.ROOT));
            Nation updated = nation.withRegime(regime);
            nations.replace(updated);
            if (regime != RegimeType.DEMOCRACY) {
                GovernmentSavedData.get(player.serverLevel()).removeElection(nation.id());
            }
            GovernmentPolicyService.onRegimeChanged(player.serverLevel(), nation, updated, System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Nation regime changed to " + regime + "."), true);
            return 1;
        } catch (IllegalArgumentException failure) {
            context.getSource().sendFailure(Component.literal("Unknown regime."));
            return 0;
        }
    }

    private static Nation requireNation(ServerPlayer player) {
        return NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .flatMap(id -> NationsSavedData.get(player.serverLevel()).get(id))
                .orElseThrow(() -> new IllegalArgumentException("You do not belong to a nation"));
    }

    private static int openElection(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            if (!nation.isLeader(player.getUUID()) && !context.getSource().hasPermission(2)) {
                throw new IllegalArgumentException("Only the leader or an administrator may open an election");
            }
            Election election = ElectionService.open(player.serverLevel(), nation, System.currentTimeMillis());
            context.getSource().sendSuccess(
                    () -> Component.literal("Election opened until " + election.closesAtMillis() + "."),
                    true
            );
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int stand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            ElectionService.stand(player.serverLevel(), nation, player.getUUID(), System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("You are now a candidate."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int vote(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            UUID candidate = UuidArgument.getUuid(context, "candidate");
            ElectionService.vote(player.serverLevel(), nation, player.getUUID(), candidate, System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Vote recorded."), false);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int electionStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Nation nation = requireNation(player);
        GovernmentSavedData.get(player.serverLevel()).election(nation.id()).ifPresentOrElse(
                election -> context.getSource().sendSuccess(
                        () -> Component.literal(
                                "Election open=" + election.isOpen(System.currentTimeMillis())
                                        + " candidates=" + election.candidates().size()
                                        + " votes=" + election.votes().size()
                        ),
                        false
                ),
                () -> context.getSource().sendSuccess(
                        () -> Component.literal("No election is currently open."),
                        false
                )
        );
        return 1;
    }

    private static int finalizeElection(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(player);
            if (!nation.isLeader(player.getUUID()) && !context.getSource().hasPermission(2)) {
                throw new IllegalArgumentException("Only the leader or an administrator may finalize an election");
            }
            Nation updated = ElectionService.finalizeIfReady(
                    player.serverLevel(),
                    nation,
                    System.currentTimeMillis()
            ).orElseThrow(() -> new IllegalArgumentException("The election is not ready to finalize"));
            context.getSource().sendSuccess(() -> Component.literal("Election finalized. Leader: " + updated.leader()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }
}
