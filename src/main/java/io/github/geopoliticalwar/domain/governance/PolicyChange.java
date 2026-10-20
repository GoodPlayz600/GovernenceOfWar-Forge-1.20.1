package io.github.geopoliticalwar.domain.governance;

import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Typed, serializable payload for an enacted government policy change. */
public record PolicyChange(
        PolicyCategory category,
        PolicyOperation operation,
        Optional<TaxPolicy> propertyTax,
        int incomeTaxBps,
        int salesTaxBps,
        int tariffBps,
        int wageMultiplierBps,
        RevenueTarget revenueTarget,
        Optional<CurrencyId> currency,
        long amount,
        long intervalSeconds,
        SubsidyScope scope,
        String projectName,
        Optional<UUID> recipient,
        long budget,
        long installment
) {
    public PolicyChange {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(propertyTax, "propertyTax");
        Objects.requireNonNull(revenueTarget, "revenueTarget");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(recipient, "recipient");
        projectName = projectName.trim();
        if (operation == PolicyOperation.CLEAR) {
            if (propertyTax.isPresent() || currency.isPresent() || recipient.isPresent()
                    || incomeTaxBps != -1 || salesTaxBps != -1 || tariffBps != -1
                    || wageMultiplierBps != -1 || amount != 0L || intervalSeconds != 0L
                    || budget != 0L || installment != 0L || !projectName.isEmpty()) {
                throw new IllegalArgumentException("A clear policy cannot carry a payload");
            }
            return;
        }
        switch (category) {
            case TAXES -> validateTaxes();
            case PAYROLL -> {
                if (wageMultiplierBps < 0 || wageMultiplierBps > 100_000) {
                    throw new IllegalArgumentException("Payroll multiplier must be 0..100000 bps");
                }
            }
            case SUBSIDIES -> validateRecurring("Subsidy", true);
            case MONEY_PRINTING -> validateRecurring("Money-printing", false);
            case FUNDED_PROJECTS -> {
                if (projectName.isEmpty() || recipient.isEmpty() || currency.isEmpty()
                        || budget <= 0L || installment <= 0L || intervalSeconds <= 0L) {
                    throw new IllegalArgumentException("Funded project fields are incomplete");
                }
            }
        }
    }

    public static PolicyChange clear(PolicyCategory category) {
        return new PolicyChange(category, PolicyOperation.CLEAR, Optional.empty(), -1, -1, -1, -1,
                RevenueTarget.SYSTEM_TAX, Optional.empty(), 0L, 0L, SubsidyScope.CITIZENS,
                "", Optional.empty(), 0L, 0L);
    }

    public static PolicyChange taxes(Optional<TaxPolicy> propertyTax, int incomeTaxBps,
                                     int salesTaxBps, int tariffBps, RevenueTarget target) {
        return new PolicyChange(PolicyCategory.TAXES, PolicyOperation.SET, propertyTax,
                incomeTaxBps, salesTaxBps, tariffBps, -1, target, Optional.empty(),
                0L, 0L, SubsidyScope.CITIZENS, "", Optional.empty(), 0L, 0L);
    }

    public static PolicyChange payroll(int wageMultiplierBps) {
        return new PolicyChange(PolicyCategory.PAYROLL, PolicyOperation.SET, Optional.empty(),
                -1, -1, -1, wageMultiplierBps, RevenueTarget.SYSTEM_TAX, Optional.empty(),
                0L, 0L, SubsidyScope.CITIZENS, "", Optional.empty(), 0L, 0L);
    }

    public static PolicyChange subsidies(CurrencyId currency, long amount, long intervalSeconds,
                                         SubsidyScope scope) {
        return new PolicyChange(PolicyCategory.SUBSIDIES, PolicyOperation.SET, Optional.empty(),
                -1, -1, -1, -1, RevenueTarget.SYSTEM_TAX, Optional.of(currency), amount,
                intervalSeconds, scope, "", Optional.empty(), 0L, 0L);
    }

    public static PolicyChange moneyPrinting(CurrencyId currency, long amount, long intervalSeconds) {
        return new PolicyChange(PolicyCategory.MONEY_PRINTING, PolicyOperation.SET, Optional.empty(),
                -1, -1, -1, -1, RevenueTarget.SYSTEM_TAX, Optional.of(currency), amount,
                intervalSeconds, SubsidyScope.CITIZENS, "", Optional.empty(), 0L, 0L);
    }

    public static PolicyChange fundedProject(String name, UUID recipient, CurrencyId currency,
                                             long budget, long installment, long intervalSeconds) {
        return new PolicyChange(PolicyCategory.FUNDED_PROJECTS, PolicyOperation.SET, Optional.empty(),
                -1, -1, -1, -1, RevenueTarget.SYSTEM_TAX, Optional.of(currency), 0L,
                intervalSeconds, SubsidyScope.CITIZENS, name, Optional.of(recipient), budget, installment);
    }

    private void validateTaxes() {
        if (incomeTaxBps < -1 || incomeTaxBps > 10_000
                || salesTaxBps < -1 || salesTaxBps > 10_000
                || tariffBps < -1 || tariffBps > 10_000
                || propertyTax.isEmpty() && incomeTaxBps == -1 && salesTaxBps == -1 && tariffBps == -1) {
            throw new IllegalArgumentException("Tax policy must contain valid rates");
        }
    }

    private void validateRecurring(String label, boolean allowScope) {
        if (currency.isEmpty() || amount <= 0L || intervalSeconds <= 0L) {
            throw new IllegalArgumentException(label + " policy fields are incomplete");
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("category", category.name());
        tag.putString("operation", operation.name());
        propertyTax.ifPresent(policy -> {
            tag.putString("property_mode", policy.mode().name());
            tag.putLong("property_units", policy.unitsPerBlock());
            tag.putString("property_currency", policy.currency().value());
            tag.putString("property_item", policy.itemId());
        });
        tag.putInt("income_bps", incomeTaxBps);
        tag.putInt("sales_bps", salesTaxBps);
        tag.putInt("tariff_bps", tariffBps);
        tag.putInt("wage_multiplier_bps", wageMultiplierBps);
        tag.putString("revenue_target", revenueTarget.name());
        currency.ifPresent(value -> tag.putString("currency", value.value()));
        tag.putLong("amount", amount);
        tag.putLong("interval_seconds", intervalSeconds);
        tag.putString("scope", scope.name());
        tag.putString("project_name", projectName);
        recipient.ifPresent(value -> tag.putUUID("recipient", value));
        tag.putLong("budget", budget);
        tag.putLong("installment", installment);
        return tag;
    }

    public static PolicyChange fromTag(CompoundTag tag) {
        PolicyCategory category = PolicyCategory.valueOf(tag.getString("category").toUpperCase(Locale.ROOT));
        PolicyOperation operation = PolicyOperation.valueOf(tag.getString("operation").toUpperCase(Locale.ROOT));
        if (operation == PolicyOperation.CLEAR) {
            return clear(category);
        }
        Optional<TaxPolicy> property = tag.contains("property_mode")
                ? Optional.of(new TaxPolicy(
                TaxMode.valueOf(tag.getString("property_mode").toUpperCase(Locale.ROOT)),
                tag.getLong("property_units"),
                new CurrencyId(tag.getString("property_currency")),
                tag.getString("property_item")))
                : Optional.empty();
        int income = tag.getInt("income_bps");
        int sales = tag.getInt("sales_bps");
        int tariff = tag.getInt("tariff_bps");
        int wage = tag.getInt("wage_multiplier_bps");
        RevenueTarget target = RevenueTarget.valueOf(tag.getString("revenue_target").toUpperCase(Locale.ROOT));
        Optional<CurrencyId> currency = tag.contains("currency")
                ? Optional.of(new CurrencyId(tag.getString("currency"))) : Optional.empty();
        Optional<UUID> recipient = tag.hasUUID("recipient")
                ? Optional.of(tag.getUUID("recipient")) : Optional.empty();
        return new PolicyChange(category, operation, property, income, sales, tariff, wage, target,
                currency, tag.getLong("amount"), tag.getLong("interval_seconds"),
                SubsidyScope.valueOf(tag.getString("scope").toUpperCase(Locale.ROOT)), tag.getString("project_name"), recipient,
                tag.getLong("budget"), tag.getLong("installment"));
    }
}
