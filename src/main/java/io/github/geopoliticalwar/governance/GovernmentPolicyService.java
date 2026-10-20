package io.github.geopoliticalwar.governance;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.domain.governance.AuthorityAction;
import io.github.geopoliticalwar.domain.governance.EnactedLaw;
import io.github.geopoliticalwar.domain.governance.FundedProject;
import io.github.geopoliticalwar.domain.governance.LawOrigin;
import io.github.geopoliticalwar.domain.governance.LawProposal;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.PolicyCategory;
import io.github.geopoliticalwar.domain.governance.PolicyChange;
import io.github.geopoliticalwar.domain.governance.PolicyOperation;
import io.github.geopoliticalwar.domain.governance.PolicyState;
import io.github.geopoliticalwar.domain.governance.PolicyVoteTally;
import io.github.geopoliticalwar.domain.governance.ProjectStatus;
import io.github.geopoliticalwar.domain.governance.ProposalStatus;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import io.github.geopoliticalwar.domain.governance.SubsidyScope;
import io.github.geopoliticalwar.domain.governance.VoteChoice;
import io.github.geopoliticalwar.economy.TreasuryService;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.GovernmentSavedData;
import io.github.geopoliticalwar.persistence.NationMembershipSavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative decrees, democratic laws, and recurring policy execution. */
public final class GovernmentPolicyService {
    private GovernmentPolicyService() {
    }

    public static EnactedLaw issueDecree(
            ServerLevel level,
            NationId nationId,
            ServerPlayer actor,
            PolicyChange change,
            long now
    ) {
        Nation nation = requireNation(level, nationId);
        if (nation.regime() != RegimeType.DICTATORSHIP) {
            throw new IllegalArgumentException("Only dictatorships may issue immediate decrees");
        }
        GovernmentAuthorityService.require(level, nation, actor, AuthorityAction.MANAGE_POLICIES);
        validateLimits(level, change);
        return enact(level, nation, change, LawOrigin.DECREE, actor.getUUID(), Optional.empty(), now);
    }

    public static LawProposal propose(
            ServerLevel level,
            NationId nationId,
            ServerPlayer sponsor,
            PolicyChange change,
            long now
    ) {
        Nation nation = requireNation(level, nationId);
        if (nation.regime() != RegimeType.DEMOCRACY) {
            throw new IllegalArgumentException("Only democracies may submit policy proposals");
        }
        if (!NationMembershipService.mayHoldOffice(level, sponsor.getUUID(), nationId)) {
            throw new IllegalArgumentException("Only citizens may submit policy proposals");
        }
        validateLimits(level, change);
        GovernmentSavedData government = GovernmentSavedData.get(level);
        long open = government.proposalsFor(nationId).stream()
                .filter(value -> value.status() == ProposalStatus.OPEN)
                .count();
        if (open >= ServerConfig.POLICY_MAX_OPEN_PROPOSALS.get()) {
            throw new IllegalArgumentException("This nation has reached its open policy proposal limit");
        }
        long window = Math.multiplyExact(ServerConfig.POLICY_PROPOSAL_WINDOW_SECONDS.get(), 1_000L);
        Set<UUID> voters = new HashSet<>();
        for (UUID citizen : nation.citizens()) {
            if (NationMembershipService.mayHoldOffice(level, citizen, nationId)) {
                voters.add(citizen);
            }
        }
        LawProposal proposal = new LawProposal(
                UUID.randomUUID(), nationId, sponsor.getUUID(), change, now,
                now > Long.MAX_VALUE - window ? Long.MAX_VALUE : now + window,
                voters, Map.of(), ProposalStatus.OPEN, Optional.empty(), Optional.empty()
        );
        government.setProposal(proposal);
        return proposal;
    }

    public static LawProposal vote(ServerLevel level, UUID proposalId, ServerPlayer voter,
                                   VoteChoice choice, long now) {
        GovernmentSavedData government = GovernmentSavedData.get(level);
        LawProposal proposal = government.proposal(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy proposal"));
        if (now >= proposal.closesAtMillis()) {
            requireProposalAccess(level, proposal, voter);
            finalizeIfReady(level, proposalId, now);
            throw new IllegalArgumentException("That proposal has closed");
        }
        if (!proposal.nation().equals(NationMembershipService.nationFor(level, voter.getUUID()).orElse(null))
                || !NationMembershipService.mayHoldOffice(level, voter.getUUID(), proposal.nation())) {
            throw new IllegalArgumentException("Only eligible citizens of the proposal nation may vote");
        }
        LawProposal updated = proposal.vote(voter.getUUID(), choice);
        government.setProposal(updated);
        return updated;
    }

    public static Optional<EnactedLaw> finalizeIfReady(ServerLevel level, UUID proposalId, long now) {
        GovernmentSavedData government = GovernmentSavedData.get(level);
        LawProposal proposal = government.proposal(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy proposal"));
        if (proposal.status() != ProposalStatus.OPEN) {
            return proposal.enactedLaw().flatMap(government::enactedLaw);
        }
        if (now < proposal.closesAtMillis()) {
            return Optional.empty();
        }
        Nation nation = requireNation(level, proposal.nation());
        if (nation.regime() != RegimeType.DEMOCRACY) {
            government.setProposal(proposal.finalized(ProposalStatus.EXPIRED, Optional.empty(), now));
            announce(level, proposal.nation(), "Policy proposal " + proposal.id() + " expired after the regime changed.");
            return Optional.empty();
        }
        int forVotes = (int) proposal.votes().values().stream().filter(VoteChoice.FOR::equals).count();
        int againstVotes = (int) proposal.votes().values().stream().filter(VoteChoice.AGAINST::equals).count();
        PolicyVoteTally tally = new PolicyVoteTally(proposal.eligibleVoters().size(), forVotes, againstVotes);
        if (!tally.passes(ServerConfig.POLICY_QUORUM_BPS.get(), ServerConfig.POLICY_PASS_BPS.get())) {
            government.setProposal(proposal.finalized(ProposalStatus.REJECTED, Optional.empty(), now));
            announce(level, proposal.nation(), "Policy proposal " + proposal.id() + " was rejected.");
            return Optional.empty();
        }
        try {
            validateLimits(level, proposal.change());
        } catch (RuntimeException invalidPolicy) {
            government.setProposal(proposal.finalized(ProposalStatus.REJECTED, Optional.empty(), now));
            GeopoliticalWarfare.LOGGER.warn("Rejected invalid persisted policy proposal {}", proposal.id(), invalidPolicy);
            announce(level, proposal.nation(), "Policy proposal " + proposal.id() + " was rejected because its policy is invalid.");
            return Optional.empty();
        }
        UUID lawId = UUID.nameUUIDFromBytes(("geopoliticalwar:law:" + proposal.id()).getBytes(StandardCharsets.UTF_8));
        EnactedLaw law = new EnactedLaw(lawId, nation.id(), LawOrigin.DEMOCRATIC_PROPOSAL,
                proposal.sponsor(), Optional.of(proposal.id()), proposal.change(), now);
        apply(level, law);
        government.setProposal(proposal.finalized(ProposalStatus.ENACTED, Optional.of(law.id()), now));
        announce(level, nation.id(), "Policy proposal " + proposal.id() + " was enacted.");
        return Optional.of(law);
    }

    public static Optional<PolicyChange> activeChange(ServerLevel level, NationId nation, PolicyCategory category) {
        return GovernmentSavedData.get(level).policy(nation, category).map(PolicyState::change)
                .filter(change -> change.operation() == PolicyOperation.SET);
    }

    public static void requireProposalAccess(ServerLevel level, LawProposal proposal, ServerPlayer actor) {
        if (actor.hasPermissions(2)) {
            return;
        }
        if (!proposal.nation().equals(NationMembershipService.nationFor(level, actor.getUUID()).orElse(null))
                || !NationMembershipService.mayHoldOffice(level, actor.getUUID(), proposal.nation())) {
            throw new IllegalArgumentException("Only eligible citizens of the proposal nation may inspect it");
        }
    }

    public static Optional<EnactedLaw> finalizeIfReady(
            ServerLevel level, UUID proposalId, ServerPlayer actor, long now
    ) {
        LawProposal proposal = GovernmentSavedData.get(level).proposal(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy proposal"));
        requireProposalAccess(level, proposal, actor);
        return finalizeIfReady(level, proposalId, now);
    }

    public static int payrollMultiplierBps(ServerLevel level, NationId nation) {
        return activeChange(level, nation, PolicyCategory.PAYROLL)
                .map(PolicyChange::wageMultiplierBps).orElse(10_000);
    }

    public static int incomeTaxBps(ServerLevel level, NationId nation, int fallback) {
        return activeChange(level, nation, PolicyCategory.TAXES)
                .map(PolicyChange::incomeTaxBps)
                .filter(value -> value >= 0)
                .orElse(fallback);
    }

    public static int salesTaxBps(ServerLevel level, NationId nation, int fallback) {
        return activeChange(level, nation, PolicyCategory.TAXES)
                .map(PolicyChange::salesTaxBps)
                .filter(value -> value >= 0)
                .orElse(fallback);
    }

    public static int tariffBps(ServerLevel level, NationId nation, int fallback) {
        return activeChange(level, nation, PolicyCategory.TAXES)
                .map(PolicyChange::tariffBps)
                .filter(value -> value >= 0)
                .orElse(fallback);
    }

    public static Optional<TaxPolicy> propertyTax(ServerLevel level, NationId nation) {
        return activeChange(level, nation, PolicyCategory.TAXES).flatMap(PolicyChange::propertyTax);
    }

    public static AccountId taxRecipient(ServerLevel level, NationId nation, AccountId fallback) {
        return activeChange(level, nation, PolicyCategory.TAXES)
                .filter(change -> change.revenueTarget() == io.github.geopoliticalwar.domain.governance.RevenueTarget.NATION_TREASURY)
                .map(change -> TreasuryService.account(nation))
                .orElse(fallback);
    }

    public static void onRegimeChanged(ServerLevel level, Nation previous, Nation current, long now) {
        if (previous.regime() == RegimeType.DEMOCRACY && current.regime() == RegimeType.DICTATORSHIP) {
            GovernmentSavedData government = GovernmentSavedData.get(level);
            government.proposalsFor(current.id()).stream()
                    .filter(value -> value.status() == ProposalStatus.OPEN)
                    .forEach(value -> government.setProposal(value.finalized(ProposalStatus.EXPIRED, Optional.empty(), now)));
        }
    }

    public static void tick(MinecraftServer server, long now) {
        ServerLevel level = server.overworld();
        GovernmentSavedData government = GovernmentSavedData.get(level);
        for (LawProposal proposal : new ArrayList<>(government.proposals().values())) {
            if (proposal.status() == ProposalStatus.OPEN && now >= proposal.closesAtMillis()) {
                try {
                    finalizeIfReady(level, proposal.id(), now);
                } catch (RuntimeException failure) {
                    GeopoliticalWarfare.LOGGER.error("Could not finalize policy proposal {}", proposal.id(), failure);
                    government.setProposal(proposal.finalized(ProposalStatus.EXPIRED, Optional.empty(), now));
                }
            }
        }
        for (Map.Entry<NationId, Map<PolicyCategory, PolicyState>> entry : government.allPolicies().entrySet()) {
            processRecurring(level, entry.getKey(), entry.getValue(), now);
        }
        for (FundedProject project : new ArrayList<>(government.fundedProjects().values())) {
            processProject(level, project, now);
        }
    }

    private static EnactedLaw enact(ServerLevel level, Nation nation, PolicyChange change, LawOrigin origin,
                                    UUID actor, Optional<UUID> proposal, long now) {
        UUID lawId = proposal.map(id -> UUID.nameUUIDFromBytes(("geopoliticalwar:law:" + id).getBytes(StandardCharsets.UTF_8)))
                .orElse(UUID.randomUUID());
        EnactedLaw law = new EnactedLaw(lawId, nation.id(), origin, actor, proposal, change, now);
        apply(level, law);
        announce(level, nation.id(), "Government decree enacted: " + change.category());
        return law;
    }

    private static void apply(ServerLevel level, EnactedLaw law) {
        GovernmentSavedData government = GovernmentSavedData.get(level);
        PolicyChange change = law.change();
        if (change.operation() == PolicyOperation.CLEAR) {
            government.removePolicy(law.nation(), change.category());
            if (change.category() == PolicyCategory.FUNDED_PROJECTS) {
                for (FundedProject project : government.fundedProjectsFor(law.nation())) {
                    if (project.status() == ProjectStatus.ACTIVE) {
                        government.setFundedProject(new FundedProject(project.id(), project.nation(), project.law(),
                                project.name(), project.recipient(), project.currency(), project.budget(), project.installment(),
                                project.intervalSeconds(), project.paid(), project.nextPaymentAtMillis(), ProjectStatus.CANCELLED));
                    }
                }
            }
            government.setEnactedLaw(law);
            return;
        }
        long nextRun = switch (change.category()) {
            case SUBSIDIES, MONEY_PRINTING, FUNDED_PROJECTS -> nowPlusIntervalBase(law.enactedAtMillis(), change.intervalSeconds());
            default -> 0L;
        };
        government.setPolicy(law.nation(), change.category(), new PolicyState(law.id(), change, law.enactedAtMillis(), nextRun));
        if (change.category() == PolicyCategory.FUNDED_PROJECTS) {
            for (FundedProject project : government.fundedProjectsFor(law.nation())) {
                if (project.status() == ProjectStatus.ACTIVE) {
                    government.setFundedProject(new FundedProject(project.id(), project.nation(), project.law(),
                            project.name(), project.recipient(), project.currency(), project.budget(), project.installment(),
                            project.intervalSeconds(), project.paid(), project.nextPaymentAtMillis(), ProjectStatus.CANCELLED));
                }
            }
            UUID projectId = UUID.nameUUIDFromBytes(("geopoliticalwar:project:" + law.id()).getBytes(StandardCharsets.UTF_8));
            government.setFundedProject(new FundedProject(projectId, law.nation(), law.id(), change.projectName(),
                    change.recipient().orElseThrow(), change.currency().orElseThrow(), change.budget(),
                    change.installment(), change.intervalSeconds(), 0L, nextRun, ProjectStatus.ACTIVE));
        }
        government.setEnactedLaw(law);
    }

    private static void processRecurring(ServerLevel level, NationId nation, Map<PolicyCategory, PolicyState> states, long now) {
        GovernmentSavedData government = GovernmentSavedData.get(level);
        for (PolicyState state : states.values()) {
            if (state.nextRunAtMillis() == 0L || now < state.nextRunAtMillis()) {
                continue;
            }
            PolicyChange change = state.change();
            try {
                validateLimits(level, change);
                boolean completed;
                if (change.category() == PolicyCategory.SUBSIDIES) {
                    completed = processSubsidy(level, nation, state);
                } else if (change.category() == PolicyCategory.MONEY_PRINTING) {
                    completed = processPrinting(level, nation, state);
                } else {
                    continue;
                }
                if (!completed) {
                    long retry = nowPlusIntervalBase(now, Math.min(change.intervalSeconds(), 60L));
                    government.setPolicy(nation, change.category(),
                            new PolicyState(state.law(), change, state.enactedAtMillis(), retry));
                    continue;
                }
                long next = nowPlusIntervalBase(now, change.intervalSeconds());
                government.setPolicy(nation, change.category(),
                        new PolicyState(state.law(), change, state.enactedAtMillis(), next));
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Disabling invalid recurring policy {} for nation {}",
                        state.law(), nation.value(), failure);
                government.removePolicy(nation, change.category());
            }
        }
    }

    private static boolean processSubsidy(ServerLevel level, NationId nation, PolicyState state) {
        PolicyChange change = state.change();
        CurrencyId currency = change.currency().orElseThrow();
        List<UUID> recipients = eligibleRecipients(level, nation, change.scope());
        EconomySavedData economy = EconomySavedData.get(level);
        AccountId source = TreasuryService.account(nation);
        long total = Math.multiplyExact(change.amount(), recipients.size());
        if (economy.balance(currency, source) < total) {
            GeopoliticalWarfare.LOGGER.info("Deferring underfunded subsidy for nation {}", nation.value());
            return false;
        }
        Map<AccountId, Long> destinations = new HashMap<>();
        for (UUID recipient : recipients) {
            AccountId account = new AccountId("player:" + recipient);
            destinations.merge(account, change.amount(), (first, second) -> Math.addExact(first, second));
        }
        try {
            UUID job = UUID.nameUUIDFromBytes(("geopoliticalwar:subsidy:" + state.law() + ":"
                    + state.nextRunAtMillis()).getBytes(StandardCharsets.UTF_8));
            economy.transferBatchOnce(currency, source, destinations, "government subsidy", job);
            return true;
        } catch (RuntimeException failure) {
            GeopoliticalWarfare.LOGGER.warn("Skipped subsidy for nation {}", nation.value(), failure);
            return false;
        }
    }

    private static boolean processPrinting(ServerLevel level, NationId nation, PolicyState state) {
        PolicyChange change = state.change();
        UUID job = UUID.nameUUIDFromBytes(("geopoliticalwar:printing:" + state.law() + ":" + state.nextRunAtMillis()).getBytes(StandardCharsets.UTF_8));
        EconomySavedData.get(level).mintOnce(change.currency().orElseThrow(), TreasuryService.account(nation),
                change.amount(), "government law printing: " + state.law(), job);
        return true;
    }

    private static void processProject(ServerLevel level, FundedProject project, long now) {
        if (project.status() != ProjectStatus.ACTIVE || now < project.nextPaymentAtMillis()) {
            return;
        }
        try {
            GovernmentSavedData government = GovernmentSavedData.get(level);
            long remaining = project.budget() - project.paid();
            if (remaining <= 0L) {
                government.setFundedProject(project.paid(0L, project.nextPaymentAtMillis(), ProjectStatus.COMPLETE));
                return;
            }
            long amount = Math.min(project.installment(), remaining);
            EconomySavedData economy = EconomySavedData.get(level);
            AccountId source = TreasuryService.account(project.nation());
            AccountId destination = new AccountId("player:" + project.recipient());
            if (economy.balance(project.currency(), source) < amount) {
                GeopoliticalWarfare.LOGGER.info("Deferring underfunded project {}", project.id());
                government.setFundedProject(project.paid(0L, nowPlusIntervalBase(now, project.intervalSeconds()), ProjectStatus.ACTIVE));
                return;
            }
            Math.addExact(economy.balance(project.currency(), destination), amount);
            UUID job = UUID.nameUUIDFromBytes(("geopoliticalwar:project-payment:" + project.id() + ":"
                    + project.nextPaymentAtMillis()).getBytes(StandardCharsets.UTF_8));
            economy.transferBatchOnce(project.currency(), source, Map.of(destination, amount),
                    "funded project: " + project.id(), job);
            long paid = project.paid() + amount;
            ProjectStatus status = paid >= project.budget() ? ProjectStatus.COMPLETE : ProjectStatus.ACTIVE;
            government.setFundedProject(project.paid(amount, nextRun(project.nextPaymentAtMillis(), project.intervalSeconds()), status));
        } catch (RuntimeException failure) {
            GeopoliticalWarfare.LOGGER.warn("Skipped funded project {}", project.id(), failure);
            GovernmentSavedData.get(level).setFundedProject(new FundedProject(project.id(), project.nation(), project.law(),
                    project.name(), project.recipient(), project.currency(), project.budget(), project.installment(),
                    project.intervalSeconds(), project.paid(), nowPlusIntervalBase(now, project.intervalSeconds()),
                    ProjectStatus.CANCELLED));
        }
    }

    private static List<UUID> eligibleRecipients(ServerLevel level, NationId nation, SubsidyScope scope) {
        Nation value = requireNation(level, nation);
        if (scope == SubsidyScope.CITIZENS) {
            return List.copyOf(value.citizens());
        }
        Set<UUID> result = new HashSet<>(value.citizens());
        NationMembershipSavedData.get(level).membershipsFor(nation).stream()
                .filter(member -> member.status() == MembershipStatus.CITIZEN
                        || member.status() == MembershipStatus.RESIDENT)
                .forEach(member -> result.add(member.player()));
        return List.copyOf(result);
    }

    private static void validateLimits(ServerLevel level, PolicyChange change) {
        if (change.amount() > ServerConfig.POLICY_MAX_PAYMENT.get()
                || change.budget() > ServerConfig.POLICY_MAX_PROJECT_BUDGET.get()
                || change.intervalSeconds() > ServerConfig.POLICY_MAX_INTERVAL_SECONDS.get()) {
            throw new IllegalArgumentException("Policy exceeds server safety limits");
        }
        EconomySavedData economy = EconomySavedData.get(level);
        change.currency().filter(currency -> economy.currency(currency).isEmpty()).ifPresent(currency -> {
            throw new IllegalArgumentException("Unknown policy currency: " + currency.value());
        });
        change.propertyTax().map(TaxPolicy::currency)
                .filter(currency -> economy.currency(currency).isEmpty()).ifPresent(currency -> {
                    throw new IllegalArgumentException("Unknown property-tax currency: " + currency.value());
                });
    }

    private static Nation requireNation(ServerLevel level, NationId nation) {
        return NationsSavedData.get(level).get(nation).orElseThrow(() -> new IllegalArgumentException("Unknown nation"));
    }

    private static long nowPlusIntervalBase(long now, long seconds) {
        long millis = Math.multiplyExact(seconds, 1_000L);
        return now > Long.MAX_VALUE - millis ? Long.MAX_VALUE : now + millis;
    }

    private static long nextRun(long previous, long seconds) {
        long millis = Math.multiplyExact(seconds, 1_000L);
        return previous > Long.MAX_VALUE - millis ? Long.MAX_VALUE : previous + millis;
    }

    private static void announce(ServerLevel level, NationId nation, String message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (NationMembershipService.nationFor(level, player.getUUID()).filter(nation::equals).isPresent()) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }
}
