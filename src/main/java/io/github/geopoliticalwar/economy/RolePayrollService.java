package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.governance.MembershipStatus;
import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import io.github.geopoliticalwar.persistence.NationMembershipSavedData;
import io.github.geopoliticalwar.persistence.GovernmentSavedData;
import io.github.geopoliticalwar.governance.GovernmentPolicyService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

/** Pays persisted role assignments from a nation or server payroll account. */
public final class RolePayrollService {
    private RolePayrollService() {
    }

    public static void collect(MinecraftServer server) {
        collect(server, System.currentTimeMillis());
    }

    static void collect(MinecraftServer server, long now) {
        ServerLevel overworld = server.overworld();
        EconomySavedData economy = EconomySavedData.get(overworld);
        long intervalMillis;
        try {
            intervalMillis = Math.multiplyExact(ServerConfig.ROLE_PAYROLL_INTERVAL_SECONDS.get(), 1_000L);
        } catch (ArithmeticException overflow) {
            GeopoliticalWarfare.LOGGER.error("Role payroll interval is too large to represent in milliseconds", overflow);
            return;
        }
        collect(
                economy,
                RolesSavedData.get(overworld),
                NationsSavedData.get(overworld),
                NationMembershipSavedData.get(overworld),
                GovernmentSavedData.get(overworld),
                overworld,
                now,
                intervalMillis,
                new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get())
        );
    }

    static void collect(
            EconomySavedData economy,
            RolesSavedData roles,
            NationsSavedData nations,
            long now,
            long intervalMillis,
            CurrencyId currency
    ) {
        collect(economy, roles, nations, null, null, null, now, intervalMillis, currency);
    }

    static void collect(
            EconomySavedData economy,
            RolesSavedData roles,
            NationsSavedData nations,
            NationMembershipSavedData memberships,
            GovernmentSavedData government,
            ServerLevel level,
            long now,
            long intervalMillis,
            CurrencyId currency
    ) {
        if (economy.nextRolePayrollAtMillis() != 0 && now < economy.nextRolePayrollAtMillis()) {
            return;
        }
        Map<AccountId, List<Payment>> paymentsBySource = new TreeMap<>(Comparator.comparing(AccountId::value));

        for (RoleAssignment assignment : roles.allAssignments()) {
            RoleDefinition definition = roles.definition(assignment.roleId()).orElse(null);
            if (definition == null) {
                GeopoliticalWarfare.LOGGER.warn("Skipping payroll for unknown role {}", assignment.roleId());
                continue;
            }
            if (definition.wagePerCycle() == 0) {
                continue;
            }

            AccountId source = payrollSource(assignment, nations, memberships);
            if (source == null) {
                continue;
            }
            long wage = definition.wagePerCycle();
            if (government != null && assignment.nation().isPresent()) {
                int multiplier = GovernmentPolicyService.payrollMultiplierBps(level, assignment.nation().get());
                wage = Math.multiplyExact(wage, multiplier) / 10_000L;
            }
            if (wage <= 0L) {
                continue;
            }
            Payment payment = new Payment(
                    source,
                    new AccountId("player:" + assignment.player()),
                    wage,
                    assignment
            );
            paymentsBySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(payment);
        }

        for (Map.Entry<AccountId, List<Payment>> entry : paymentsBySource.entrySet()) {
            paySource(economy, currency, entry.getKey(), entry.getValue(), level, government);
        }

        long next = now > Long.MAX_VALUE - intervalMillis ? Long.MAX_VALUE : now + intervalMillis;
        economy.setNextRolePayrollAtMillis(next);
    }

    private static AccountId payrollSource(
            RoleAssignment assignment,
            NationsSavedData nations,
            NationMembershipSavedData memberships
    ) {
        if (assignment.nation().isEmpty()) {
            return new AccountId("system:payroll");
        }
        NationId nationId = assignment.nation().get();
        return nations.get(nationId)
                .filter(nation -> memberships == null
                        ? nation.isCitizen(assignment.player())
                        : memberships.membership(assignment.player())
                        .map(value -> value.nation().equals(nationId) && value.status() == MembershipStatus.CITIZEN)
                        .orElse(nation.isCitizen(assignment.player())))
                .map(ignored -> new AccountId("nation:" + nationId.value()))
                .orElse(null);
    }

    private static void paySource(
            EconomySavedData economy,
            CurrencyId currency,
            AccountId source,
            List<Payment> payments,
            ServerLevel level,
            GovernmentSavedData government
    ) {
        Map<AccountId, Long> transfers = new HashMap<>();
        try {
            for (Payment payment : payments) {
                long tax = incomeTax(level, payment, payment.amount());
                long net = payment.amount() - tax;
                if (net > 0L && !payment.destination().equals(source)) {
                    transfers.merge(payment.destination(), net, (first, second) -> Math.addExact(first, second));
                }
                AccountId taxDestination = taxRecipient(level, payment, currency);
                if (tax > 0L && !taxDestination.equals(source)) {
                    transfers.merge(taxDestination, tax, (first, second) -> Math.addExact(first, second));
                }
            }
            for (Map.Entry<AccountId, Long> destination : transfers.entrySet()) {
                Math.addExact(economy.balance(currency, destination.getKey()), destination.getValue());
            }
        } catch (ArithmeticException overflow) {
            GeopoliticalWarfare.LOGGER.warn("Skipping role payroll from {} because a balance would overflow", source.value());
            return;
        }
        long totalDebit;
        try {
            totalDebit = transfers.values().stream().reduce(0L, (first, second) -> Math.addExact(first, second));
        } catch (ArithmeticException overflow) {
            GeopoliticalWarfare.LOGGER.warn("Skipping role payroll from {} because the total would overflow", source.value());
            return;
        }
        if (economy.balance(currency, source) < totalDebit) {
            GeopoliticalWarfare.LOGGER.warn(
                    "Skipping role payroll from {}: need {}, have {}",
                    source.value(),
                    totalDebit,
                    economy.balance(currency, source)
            );
            return;
        }
        try {
            economy.transferBatch(currency, source, transfers, "role payroll and income tax");
        } catch (RuntimeException failure) {
            GeopoliticalWarfare.LOGGER.error("Role payroll transfer failed from {}", source.value(), failure);
        }
    }

    private record Payment(AccountId source, AccountId destination, long amount, RoleAssignment assignment) {
    }

    private static long incomeTax(ServerLevel level, Payment payment, long amount) {
        return level == null ? TaxService.incomeTax(amount)
                : TaxService.incomeTax(level, payment.assignment().nation(), amount);
    }

    private static AccountId taxRecipient(ServerLevel level, Payment payment, CurrencyId currency) {
        if (level == null || payment.assignment().nation().isEmpty()) {
            return new AccountId("system:tax");
        }
        return TaxService.incomeTaxRecipient(level, payment.assignment().nation());
    }

}
