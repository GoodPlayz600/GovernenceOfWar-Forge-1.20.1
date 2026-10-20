package io.github.geopoliticalwar.governance;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.domain.governance.EnactedLaw;
import io.github.geopoliticalwar.domain.governance.LawProposal;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.PolicyCategory;
import io.github.geopoliticalwar.domain.governance.PolicyChange;
import io.github.geopoliticalwar.domain.governance.PolicyOperation;
import io.github.geopoliticalwar.domain.governance.ProposalStatus;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import io.github.geopoliticalwar.domain.governance.VoteChoice;
import io.github.geopoliticalwar.persistence.GovernmentSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** Decree/proposal/vote command surface for government policy lawmaking. */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PolicyCommands {
    private PolicyCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("geopoliticalwar")
                        .then(Commands.literal("law")
                                .then(Commands.literal("decree")
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .then(Commands.literal("clear").executes(PolicyCommands::decreeClear))
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                                .executes(PolicyCommands::decreeSet)))))
                                .then(Commands.literal("propose")
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .then(Commands.literal("clear").executes(PolicyCommands::proposeClear))
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                                .executes(PolicyCommands::proposeSet)))))
                                .then(Commands.literal("vote")
                                        .then(Commands.argument("proposal", UuidArgument.uuid())
                                                .then(Commands.argument("choice", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(VoteChoice.values()).map(Enum::name), builder))
                                                        .executes(PolicyCommands::vote))))
                                .then(Commands.literal("finalize")
                                        .then(Commands.argument("proposal", UuidArgument.uuid())
                                                .executes(PolicyCommands::finalizeProposal)))
                                .then(Commands.literal("proposal")
                                        .then(Commands.argument("proposal", UuidArgument.uuid())
                                                .executes(PolicyCommands::proposal)))
                                .then(Commands.literal("status").executes(PolicyCommands::status))
                                .then(Commands.literal("history").executes(PolicyCommands::history)))
        );
    }

    private static int decreeSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return decree(context, PolicyCommandSupport.parse(context));
        } catch (RuntimeException failure) {
            return policyFailure(context, "Decree rejected: ", failure);
        }
    }

    private static int decreeClear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return decree(context, PolicyChange.clear(PolicyCommandSupport.category(context)));
        } catch (RuntimeException failure) {
            return policyFailure(context, "Decree rejected: ", failure);
        }
    }

    private static int decree(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                              PolicyChange change) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(actor);
            EnactedLaw law = GovernmentPolicyService.issueDecree(actor.serverLevel(), nation.id(), actor, change,
                    System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Decree enacted: " + law.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Decree rejected: " + failure.getMessage()));
            return 0;
        }
    }

    private static int proposeSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return propose(context, PolicyCommandSupport.parse(context));
        } catch (RuntimeException failure) {
            return policyFailure(context, "Proposal rejected: ", failure);
        }
    }

    private static int proposeClear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return propose(context, PolicyChange.clear(PolicyCommandSupport.category(context)));
        } catch (RuntimeException failure) {
            return policyFailure(context, "Proposal rejected: ", failure);
        }
    }

    private static int propose(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               PolicyChange change) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            Nation nation = requireNation(actor);
            LawProposal proposal = GovernmentPolicyService.propose(actor.serverLevel(), nation.id(), actor, change,
                    System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal(
                    "Proposal opened: " + proposal.id() + " closes=" + proposal.closesAtMillis()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Proposal rejected: " + failure.getMessage()));
            return 0;
        }
    }

    private static int vote(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            VoteChoice choice = VoteChoice.valueOf(StringArgumentType.getString(context, "choice").toUpperCase(Locale.ROOT));
            LawProposal proposal = GovernmentPolicyService.vote(actor.serverLevel(),
                    UuidArgument.getUuid(context, "proposal"), actor, choice, System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Vote recorded for " + proposal.id()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Vote rejected: " + failure.getMessage()));
            return 0;
        }
    }

    private static int finalizeProposal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        try {
            UUID id = UuidArgument.getUuid(context, "proposal");
            GovernmentPolicyService.finalizeIfReady(actor.serverLevel(), id, actor, System.currentTimeMillis());
            context.getSource().sendSuccess(() -> Component.literal("Proposal finalized or is not yet due."), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
    }

    private static int proposal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        LawProposal proposal = GovernmentSavedData.get(actor.serverLevel())
                .proposal(UuidArgument.getUuid(context, "proposal")).orElse(null);
        if (proposal == null) {
            context.getSource().sendFailure(Component.literal("Unknown proposal."));
            return 0;
        }
        try {
            GovernmentPolicyService.requireProposalAccess(actor.serverLevel(), proposal, actor);
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal(failure.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                proposal.id() + " nation=" + proposal.nation().value() + " category=" + proposal.change().category()
                        + " status=" + proposal.status() + " votes=" + proposal.votes().size()
                        + "/" + proposal.eligibleVoters().size() + " closes=" + proposal.closesAtMillis()), false);
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        Nation nation = requireNation(actor);
        GovernmentSavedData government = GovernmentSavedData.get(actor.serverLevel());
        government.policies(nation.id()).forEach((category, state) -> context.getSource().sendSuccess(
                () -> Component.literal(category + " law=" + state.law() + " next=" + state.nextRunAtMillis()), false));
        government.proposalsFor(nation.id()).stream().filter(value -> value.status() == ProposalStatus.OPEN)
                .forEach(value -> context.getSource().sendSuccess(
                        () -> Component.literal("open proposal=" + value.id() + " closes=" + value.closesAtMillis()), false));
        return 1;
    }

    private static int history(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        Nation nation = requireNation(actor);
        GovernmentSavedData.get(actor.serverLevel()).enactedLaws().values().stream()
                .filter(value -> value.nation().equals(nation.id()))
                .sorted((first, second) -> Long.compare(second.enactedAtMillis(), first.enactedAtMillis()))
                .forEach(value -> context.getSource().sendSuccess(
                        () -> Component.literal(value.enactedAtMillis() + " " + value.origin()
                                + " " + value.change().category() + " law=" + value.id()), false));
        return 1;
    }

    private static Nation requireNation(ServerPlayer player) {
        return NationMembershipService.nationFor(player.serverLevel(), player.getUUID())
                .flatMap(id -> io.github.geopoliticalwar.persistence.NationsSavedData.get(player.serverLevel()).get(id))
                .orElseThrow(() -> new IllegalArgumentException("You do not belong to a nation"));
    }

    private static int policyFailure(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            String prefix,
            RuntimeException failure
    ) {
        context.getSource().sendFailure(Component.literal(prefix +
                (failure.getMessage() == null ? "Invalid policy." : failure.getMessage())));
        return 0;
    }
}
