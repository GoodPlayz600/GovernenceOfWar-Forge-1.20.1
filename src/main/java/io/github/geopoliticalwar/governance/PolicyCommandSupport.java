package io.github.geopoliticalwar.governance;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.domain.governance.PolicyCategory;
import io.github.geopoliticalwar.domain.governance.PolicyChange;
import io.github.geopoliticalwar.domain.governance.RevenueTarget;
import io.github.geopoliticalwar.domain.governance.SubsidyScope;
import net.minecraft.commands.CommandSourceStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Shared, deterministic key=value parsing for decree and proposal commands. */
public final class PolicyCommandSupport {
    private PolicyCommandSupport() {
    }

    public static PolicyCategory category(CommandContext<CommandSourceStack> context) {
        return PolicyCategory.valueOf(StringArgumentType.getString(context, "category").toUpperCase(Locale.ROOT));
    }

    public static PolicyChange parse(CommandContext<CommandSourceStack> context) {
        PolicyCategory category = category(context);
        return parse(category, StringArgumentType.getString(context, "spec"));
    }

    public static PolicyChange parse(PolicyCategory category, String raw) {
        Map<String, String> values = new HashMap<>();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            int separator = token.indexOf('=');
            if (separator <= 0 || separator == token.length() - 1) {
                throw new IllegalArgumentException("Policy fields must use key=value syntax");
            }
            values.put(token.substring(0, separator).toLowerCase(Locale.ROOT), token.substring(separator + 1));
        }
        rejectUnknown(category, values);
        return switch (category) {
            case TAXES -> taxes(values);
            case PAYROLL -> PolicyChange.payroll(integer(values, "multiplier_bps", required(values, "multiplier_bps")));
            case SUBSIDIES -> PolicyChange.subsidies(
                    currency(values), positive(values, "amount"), positive(values, "interval"),
                    enumValue(values, "scope", SubsidyScope.class, SubsidyScope.CITIZENS));
            case MONEY_PRINTING -> PolicyChange.moneyPrinting(
                    currency(values), positive(values, "amount"), positive(values, "interval"));
            case FUNDED_PROJECTS -> PolicyChange.fundedProject(
                    required(values, "name"), UUID.fromString(required(values, "recipient")), currency(values),
                    positive(values, "budget"), positive(values, "installment"), positive(values, "interval"));
        };
    }

    private static PolicyChange taxes(Map<String, String> values) {
        Optional<TaxPolicy> property = Optional.empty();
        if (values.containsKey("property_mode")) {
            TaxMode mode = enumValue(values, "property_mode", TaxMode.class, TaxMode.LEDGER);
            long rate = positive(values, "property_rate");
            CurrencyId currency = values.containsKey("property_currency")
                    ? new CurrencyId(values.get("property_currency")) : defaultCurrency();
            property = Optional.of(switch (mode) {
                case LEDGER -> TaxPolicy.ledger(rate, currency);
                case DIAMONDS -> TaxPolicy.diamonds(rate, currency);
                case ITEM -> TaxPolicy.item(rate, currency, required(values, "property_item"));
            });
        }
        return PolicyChange.taxes(property,
                integer(values, "income_bps", -1),
                integer(values, "sales_bps", -1),
                integer(values, "tariff_bps", -1),
                enumValue(values, "revenue", RevenueTarget.class, RevenueTarget.SYSTEM_TAX));
    }

    private static CurrencyId currency(Map<String, String> values) {
        return new CurrencyId(values.getOrDefault("currency", ServerConfig.DEFAULT_CURRENCY_ID.get()));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing policy field: " + key);
        }
        return value;
    }

    private static long positive(Map<String, String> values, String key) {
        long value = Long.parseLong(required(values, key));
        if (value <= 0L) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
    }

    private static int integer(Map<String, String> values, String key, String raw) {
        return Integer.parseInt(raw);
    }

    private static void rejectUnknown(PolicyCategory category, Map<String, String> values) {
        Set<String> allowed = switch (category) {
            case TAXES -> Set.of("property_mode", "property_rate", "property_currency", "property_item",
                    "income_bps", "sales_bps", "tariff_bps", "revenue");
            case PAYROLL -> Set.of("multiplier_bps");
            case SUBSIDIES -> Set.of("currency", "amount", "interval", "scope");
            case MONEY_PRINTING -> Set.of("currency", "amount", "interval");
            case FUNDED_PROJECTS -> Set.of("name", "recipient", "currency", "budget", "installment", "interval");
        };
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Unknown policy field: " + key);
        });
    }

    private static <T extends Enum<T>> T enumValue(Map<String, String> values, String key, Class<T> type, T fallback) {
        return values.containsKey(key) ? Enum.valueOf(type, values.get(key).toUpperCase(Locale.ROOT)) : fallback;
    }

    private static CurrencyId defaultCurrency() {
        return new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
    }
}
