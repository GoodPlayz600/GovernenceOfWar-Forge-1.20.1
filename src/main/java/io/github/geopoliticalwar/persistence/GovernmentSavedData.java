package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.Election;
import io.github.geopoliticalwar.domain.governance.EnactedLaw;
import io.github.geopoliticalwar.domain.governance.FundedProject;
import io.github.geopoliticalwar.domain.governance.LawOrigin;
import io.github.geopoliticalwar.domain.governance.LawProposal;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.PolicyCategory;
import io.github.geopoliticalwar.domain.governance.PolicyState;
import io.github.geopoliticalwar.domain.governance.ProposalStatus;
import io.github.geopoliticalwar.domain.governance.ProjectStatus;
import io.github.geopoliticalwar.domain.governance.VoteChoice;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistent elections and later legislative records. */
public final class GovernmentSavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_government";
    private static final int CURRENT_SCHEMA = 2;
    private final Map<NationId, Election> elections = new HashMap<>();
    private final Map<UUID, LawProposal> proposals = new HashMap<>();
    private final Map<UUID, EnactedLaw> enactedLaws = new HashMap<>();
    private final Map<NationId, Map<PolicyCategory, PolicyState>> policies = new HashMap<>();
    private final Map<UUID, FundedProject> fundedProjects = new HashMap<>();

    public static GovernmentSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                GovernmentSavedData::load,
                GovernmentSavedData::new,
                DATA_NAME
        );
    }

    public static GovernmentSavedData load(CompoundTag tag) {
        int schema = tag.contains("schema") ? tag.getInt("schema") : 1;
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported government data schema: " + schema);
        }
        GovernmentSavedData data = new GovernmentSavedData();
        for (Tag electionTag : tag.getList("elections", Tag.TAG_COMPOUND)) {
            try {
                Election election = ElectionNbtCodec.read((CompoundTag) electionTag);
                data.elections.put(election.nation(), election);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted election", failure);
            }
        }
        for (Tag raw : tag.getList("proposals", Tag.TAG_COMPOUND)) {
            try {
                LawProposal proposal = readProposal((CompoundTag) raw);
                data.proposals.put(proposal.id(), proposal);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted law proposal", failure);
            }
        }
        for (Tag raw : tag.getList("enacted_laws", Tag.TAG_COMPOUND)) {
            try {
                EnactedLaw law = readLaw((CompoundTag) raw);
                data.enactedLaws.put(law.id(), law);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted enacted law", failure);
            }
        }
        for (Tag raw : tag.getList("active_policies", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                NationId nation = new NationId(value.getUUID("nation"));
                PolicyCategory category = PolicyCategory.valueOf(value.getString("category"));
                PolicyState state = new PolicyState(
                        value.getUUID("law"),
                        io.github.geopoliticalwar.domain.governance.PolicyChange.fromTag(value.getCompound("change")),
                        value.getLong("enacted_at"),
                        value.getLong("next_run_at")
                );
                data.policies.computeIfAbsent(nation, ignored -> new HashMap<>()).put(category, state);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted active policy", failure);
            }
        }
        for (Tag raw : tag.getList("funded_projects", Tag.TAG_COMPOUND)) {
            try {
                FundedProject project = readProject((CompoundTag) raw);
                data.fundedProjects.put(project.id(), project);
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid persisted funded project", failure);
            }
        }
        return data;
    }

    public Optional<Election> election(NationId nation) {
        return Optional.ofNullable(elections.get(nation));
    }

    public void setElection(Election election) {
        elections.put(election.nation(), election);
        setDirty();
    }

    public void removeElection(NationId nation) {
        elections.remove(nation);
        setDirty();
    }

    public Optional<LawProposal> proposal(UUID id) {
        return Optional.ofNullable(proposals.get(id));
    }

    public Map<UUID, LawProposal> proposals() {
        return Map.copyOf(proposals);
    }

    public Set<LawProposal> proposalsFor(NationId nation) {
        return proposals.values().stream().filter(value -> value.nation().equals(nation)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void setProposal(LawProposal proposal) {
        proposals.put(proposal.id(), proposal);
        pruneHistory();
        setDirty();
    }

    public Optional<EnactedLaw> enactedLaw(UUID id) {
        return Optional.ofNullable(enactedLaws.get(id));
    }

    public Map<UUID, EnactedLaw> enactedLaws() {
        return Map.copyOf(enactedLaws);
    }

    public void setEnactedLaw(EnactedLaw law) {
        enactedLaws.put(law.id(), law);
        pruneHistory();
        setDirty();
    }

    public Optional<PolicyState> policy(NationId nation, PolicyCategory category) {
        return Optional.ofNullable(policies.getOrDefault(nation, Map.of()).get(category));
    }

    public Map<PolicyCategory, PolicyState> policies(NationId nation) {
        return Map.copyOf(policies.getOrDefault(nation, Map.of()));
    }

    public Map<NationId, Map<PolicyCategory, PolicyState>> allPolicies() {
        Map<NationId, Map<PolicyCategory, PolicyState>> result = new HashMap<>();
        policies.forEach((nation, values) -> result.put(nation, Map.copyOf(values)));
        return Map.copyOf(result);
    }

    public void setPolicy(NationId nation, PolicyCategory category, PolicyState state) {
        policies.computeIfAbsent(nation, ignored -> new HashMap<>()).put(category, state);
        pruneHistory();
        setDirty();
    }

    public void removePolicy(NationId nation, PolicyCategory category) {
        Map<PolicyCategory, PolicyState> values = policies.get(nation);
        if (values != null && values.remove(category) != null) {
            if (values.isEmpty()) {
                policies.remove(nation);
            }
            setDirty();
        }
    }

    public Optional<FundedProject> fundedProject(UUID id) {
        return Optional.ofNullable(fundedProjects.get(id));
    }

    public Map<UUID, FundedProject> fundedProjects() {
        return Map.copyOf(fundedProjects);
    }

    public Set<FundedProject> fundedProjectsFor(NationId nation) {
        return fundedProjects.values().stream().filter(value -> value.nation().equals(nation)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void setFundedProject(FundedProject project) {
        fundedProjects.put(project.id(), project);
        pruneHistory();
        setDirty();
    }

    public void removeFundedProject(UUID id) {
        if (fundedProjects.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        pruneHistory();
        tag.putInt("schema", CURRENT_SCHEMA);
        ListTag serialized = new ListTag();
        elections.values().stream()
                .sorted((first, second) -> first.nation().value().toString().compareTo(second.nation().value().toString()))
                .map(ElectionNbtCodec::write)
                .forEach(serialized::add);
        tag.put("elections", serialized);
        ListTag proposalTags = new ListTag();
        proposals.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .map(GovernmentSavedData::writeProposal)
                .forEach(proposalTags::add);
        tag.put("proposals", proposalTags);

        ListTag lawTags = new ListTag();
        enactedLaws.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .map(GovernmentSavedData::writeLaw)
                .forEach(lawTags::add);
        tag.put("enacted_laws", lawTags);

        ListTag policyTags = new ListTag();
        policies.entrySet().stream()
                .sorted((first, second) -> first.getKey().value().toString().compareTo(second.getKey().value().toString()))
                .forEach(entry -> entry.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(policy -> {
                            CompoundTag value = new CompoundTag();
                            value.putUUID("nation", entry.getKey().value());
                            value.putString("category", policy.getKey().name());
                            value.putUUID("law", policy.getValue().law());
                            value.putLong("enacted_at", policy.getValue().enactedAtMillis());
                            value.putLong("next_run_at", policy.getValue().nextRunAtMillis());
                            value.put("change", policy.getValue().change().toTag());
                            policyTags.add(value);
                        }));
        tag.put("active_policies", policyTags);

        ListTag projectTags = new ListTag();
        fundedProjects.values().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .map(GovernmentSavedData::writeProject)
                .forEach(projectTags::add);
        tag.put("funded_projects", projectTags);
        return tag;
    }

    private void pruneHistory() {
        int limit = Math.max(1, ServerConfig.POLICY_MAX_HISTORY.get());
        Set<UUID> activeLaws = new HashSet<>();
        policies.values().forEach(states -> states.values().forEach(state -> activeLaws.add(state.law())));
        fundedProjects.values().stream()
                .filter(project -> project.status() == ProjectStatus.ACTIVE)
                .forEach(project -> activeLaws.add(project.law()));

        List<EnactedLaw> laws = enactedLaws.values().stream()
                .sorted(Comparator.comparingLong(EnactedLaw::enactedAtMillis).reversed())
                .toList();
        Set<UUID> keepLaws = new HashSet<>(activeLaws);
        for (EnactedLaw law : laws) {
            if (keepLaws.size() < limit) {
                keepLaws.add(law.id());
            }
        }
        enactedLaws.keySet().removeIf(id -> !keepLaws.contains(id));

        Set<UUID> linkedProposals = new HashSet<>();
        enactedLaws.values().forEach(law -> law.proposal().ifPresent(linkedProposals::add));
        List<LawProposal> proposalHistory = proposals.values().stream()
                .filter(proposal -> proposal.status() != ProposalStatus.OPEN)
                .sorted(Comparator.comparingLong((LawProposal proposal) ->
                        proposal.finalizedAtMillis().orElse(Long.MIN_VALUE)).reversed())
                .toList();
        Set<UUID> keepProposals = new HashSet<>(linkedProposals);
        proposals.values().stream()
                .filter(proposal -> proposal.status() == ProposalStatus.OPEN)
                .map(LawProposal::id)
                .forEach(keepProposals::add);
        for (LawProposal proposal : proposalHistory) {
            if (keepProposals.size() < limit) {
                keepProposals.add(proposal.id());
            }
        }
        proposals.keySet().removeIf(id -> !keepProposals.contains(id));
    }

    private static CompoundTag writeProposal(LawProposal proposal) {
        CompoundTag value = new CompoundTag();
        value.putUUID("id", proposal.id());
        value.putUUID("nation", proposal.nation().value());
        value.putUUID("sponsor", proposal.sponsor());
        value.put("change", proposal.change().toTag());
        value.putLong("opened_at", proposal.openedAtMillis());
        value.putLong("closes_at", proposal.closesAtMillis());
        value.putString("status", proposal.status().name());
        proposal.enactedLaw().ifPresent(id -> value.putUUID("enacted_law", id));
        proposal.finalizedAtMillis().ifPresent(time -> value.putLong("finalized_at", time));
        ListTag voters = new ListTag();
        proposal.eligibleVoters().stream().sorted().forEach(player -> {
            CompoundTag voter = new CompoundTag();
            voter.putUUID("player", player);
            voters.add(voter);
        });
        value.put("eligible_voters", voters);
        ListTag votes = new ListTag();
        proposal.votes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag vote = new CompoundTag();
            vote.putUUID("voter", entry.getKey());
            vote.putString("choice", entry.getValue().name());
            votes.add(vote);
        });
        value.put("votes", votes);
        return value;
    }

    private static LawProposal readProposal(CompoundTag value) {
        Set<UUID> eligible = new HashSet<>();
        for (Tag raw : value.getList("eligible_voters", Tag.TAG_COMPOUND)) {
            eligible.add(((CompoundTag) raw).getUUID("player"));
        }
        Map<UUID, VoteChoice> votes = new HashMap<>();
        for (Tag raw : value.getList("votes", Tag.TAG_COMPOUND)) {
            CompoundTag vote = (CompoundTag) raw;
            votes.put(vote.getUUID("voter"), VoteChoice.valueOf(vote.getString("choice")));
        }
        Optional<UUID> enacted = value.hasUUID("enacted_law")
                ? Optional.of(value.getUUID("enacted_law")) : Optional.empty();
        Optional<Long> finalized = value.contains("finalized_at")
                ? Optional.of(value.getLong("finalized_at")) : Optional.empty();
        return new LawProposal(value.getUUID("id"), new NationId(value.getUUID("nation")),
                value.getUUID("sponsor"), io.github.geopoliticalwar.domain.governance.PolicyChange.fromTag(value.getCompound("change")),
                value.getLong("opened_at"), value.getLong("closes_at"), eligible, votes,
                ProposalStatus.valueOf(value.getString("status")), enacted, finalized);
    }

    private static CompoundTag writeLaw(EnactedLaw law) {
        CompoundTag value = new CompoundTag();
        value.putUUID("id", law.id());
        value.putUUID("nation", law.nation().value());
        value.putString("origin", law.origin().name());
        value.putUUID("actor", law.actor());
        law.proposal().ifPresent(id -> value.putUUID("proposal", id));
        value.put("change", law.change().toTag());
        value.putLong("enacted_at", law.enactedAtMillis());
        return value;
    }

    private static EnactedLaw readLaw(CompoundTag value) {
        Optional<UUID> proposal = value.hasUUID("proposal")
                ? Optional.of(value.getUUID("proposal")) : Optional.empty();
        return new EnactedLaw(value.getUUID("id"), new NationId(value.getUUID("nation")),
                LawOrigin.valueOf(value.getString("origin")), value.getUUID("actor"), proposal,
                io.github.geopoliticalwar.domain.governance.PolicyChange.fromTag(value.getCompound("change")),
                value.getLong("enacted_at"));
    }

    private static CompoundTag writeProject(FundedProject project) {
        CompoundTag value = new CompoundTag();
        value.putUUID("id", project.id());
        value.putUUID("nation", project.nation().value());
        value.putUUID("law", project.law());
        value.putString("name", project.name());
        value.putUUID("recipient", project.recipient());
        value.putString("currency", project.currency().value());
        value.putLong("budget", project.budget());
        value.putLong("installment", project.installment());
        value.putLong("interval_seconds", project.intervalSeconds());
        value.putLong("paid", project.paid());
        value.putLong("next_payment_at", project.nextPaymentAtMillis());
        value.putString("status", project.status().name());
        return value;
    }

    private static FundedProject readProject(CompoundTag value) {
        return new FundedProject(value.getUUID("id"), new NationId(value.getUUID("nation")),
                value.getUUID("law"), value.getString("name"), value.getUUID("recipient"),
                new io.github.geopoliticalwar.domain.economy.CurrencyId(value.getString("currency")),
                value.getLong("budget"), value.getLong("installment"), value.getLong("interval_seconds"),
                value.getLong("paid"), value.getLong("next_payment_at"),
                ProjectStatus.valueOf(value.getString("status")));
    }
}
