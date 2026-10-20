package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyDefinition;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.CurrencyMode;
import io.github.geopoliticalwar.domain.economy.LedgerTransaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Fixed-point account, currency-supply, and audit ledger storage. */
public final class EconomySavedData extends SavedData {
    private static final String DATA_NAME = "geopoliticalwar_economy";
    private static final AccountId SYSTEM_ACCOUNT = new AccountId("system:mint");
    private static final int MAX_TRANSACTION_HISTORY = 2_048;

    private final Map<CurrencyId, CurrencyDefinition> currencies = new HashMap<>();
    private final Map<AccountKey, Long> balances = new HashMap<>();
    private final Deque<LedgerTransaction> transactions = new ArrayDeque<>();
    private final Set<UUID> completedMintingJobs = new HashSet<>();
    private final Set<UUID> completedTransferJobs = new HashSet<>();
    private final Set<UUID> redeemedCashSerials = new HashSet<>();
    private final Map<UUID, CashIssue> issuedCash = new HashMap<>();
    private long nextRolePayrollAtMillis;
    private long nextInflationAtMillis;

    public static EconomySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        EconomySavedData data = overworld.getDataStorage().computeIfAbsent(
                EconomySavedData::load,
                EconomySavedData::new,
                DATA_NAME
        );
        data.ensureDefaultCurrency();
        return data;
    }

    public static EconomySavedData load(CompoundTag tag) {
        EconomySavedData data = new EconomySavedData();
        for (Tag currencyTag : tag.getList("currencies", Tag.TAG_COMPOUND)) {
            CompoundTag currency = (CompoundTag) currencyTag;
            CurrencyDefinition definition = new CurrencyDefinition(
                    new CurrencyId(currency.getString("id")),
                    currency.getString("name"),
                    CurrencyMode.valueOf(currency.getString("mode")),
                    currency.getLong("supply"),
                    currency.getLong("purchasing_power_bps")
            );
            data.currencies.put(definition.id(), definition);
        }

        for (Tag balanceTag : tag.getList("balances", Tag.TAG_COMPOUND)) {
            CompoundTag balance = (CompoundTag) balanceTag;
            data.balances.put(
                    new AccountKey(
                            new CurrencyId(balance.getString("currency")),
                            new AccountId(balance.getString("account"))
                    ),
                    balance.getLong("amount")
            );
        }

        for (Tag transactionTag : tag.getList("transactions", Tag.TAG_COMPOUND)) {
            CompoundTag transaction = (CompoundTag) transactionTag;
            data.transactions.addLast(new LedgerTransaction(
                    transaction.getUUID("id"),
                    transaction.getLong("timestamp"),
                    new CurrencyId(transaction.getString("currency")),
                    new AccountId(transaction.getString("from")),
                    new AccountId(transaction.getString("to")),
                    transaction.getLong("amount"),
                    transaction.getString("reason")
            ));
        }
        for (Tag jobTag : tag.getList("completed_minting_jobs", Tag.TAG_COMPOUND)) {
            CompoundTag job = (CompoundTag) jobTag;
            if (job.hasUUID("id")) {
                data.completedMintingJobs.add(job.getUUID("id"));
            }
        }
        for (Tag jobTag : tag.getList("completed_transfer_jobs", Tag.TAG_COMPOUND)) {
            CompoundTag job = (CompoundTag) jobTag;
            if (job.hasUUID("id")) {
                data.completedTransferJobs.add(job.getUUID("id"));
            }
        }
        for (Tag raw : tag.getList("redeemed_cash_serials", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("id")) {
                data.redeemedCashSerials.add(value.getUUID("id"));
            }
        }
        for (Tag raw : tag.getList("issued_cash", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag value = (CompoundTag) raw;
                UUID serial = value.getUUID("id");
                data.issuedCash.put(serial, new CashIssue(
                        new CurrencyId(value.getString("currency")),
                        value.getLong("value")
                ));
            } catch (RuntimeException failure) {
                GeopoliticalWarfare.LOGGER.error("Skipping invalid issued cash record", failure);
            }
        }
        data.nextRolePayrollAtMillis = tag.getLong("next_role_payroll_at_millis");
        data.nextInflationAtMillis = tag.getLong("next_inflation_at_millis");
        return data;
    }

    public void ensureDefaultCurrency() {
        CurrencyId id = new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
        if (!currencies.containsKey(id)) {
            currencies.put(id, new CurrencyDefinition(
                    id,
                    ServerConfig.DEFAULT_CURRENCY_NAME.get(),
                    CurrencyMode.FIAT,
                    0L,
                    10_000L
            ));
            setDirty();
        }
    }

    public Optional<CurrencyDefinition> currency(CurrencyId id) {
        return Optional.ofNullable(currencies.get(Objects.requireNonNull(id, "id")));
    }

    public Map<CurrencyId, CurrencyDefinition> currencies() {
        return Map.copyOf(currencies);
    }

    public void replaceCurrency(CurrencyDefinition definition) {
        requireCurrency(definition.id());
        currencies.put(definition.id(), definition);
        setDirty();
    }

    public long nextInflationAtMillis() {
        return nextInflationAtMillis;
    }

    public void setNextInflationAtMillis(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Inflation timestamp cannot be negative");
        }
        nextInflationAtMillis = value;
        setDirty();
    }

    public long balance(CurrencyId currency, AccountId account) {
        requireCurrency(currency);
        return balances.getOrDefault(new AccountKey(currency, account), 0L);
    }

    public long inflationAdjusted(CurrencyId currency, long baseAmount) {
        if (baseAmount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        long purchasingPower = requireCurrency(currency).purchasingPowerBps();
        if (purchasingPower >= 10_000L) {
            return baseAmount;
        }
        long numerator = Math.multiplyExact(baseAmount, 10_000L);
        long rounded = numerator > Long.MAX_VALUE - purchasingPower + 1L
                ? Long.MAX_VALUE
                : numerator + purchasingPower - 1L;
        return rounded / purchasingPower;
    }

    public void mint(CurrencyId currency, AccountId destination, long amount, String reason) {
        mint(currency, destination, amount, reason, UUID.randomUUID());
    }

    public boolean mintOnce(
            CurrencyId currency,
            AccountId destination,
            long amount,
            String reason,
            UUID jobId
    ) {
        Objects.requireNonNull(jobId, "jobId");
        if (completedMintingJobs.contains(jobId)) {
            return false;
        }
        mint(currency, destination, amount, reason, jobId);
        completedMintingJobs.add(jobId);
        setDirty();
        return true;
    }

    public boolean redeemCash(UUID serial) {
        Objects.requireNonNull(serial, "serial");
        if (!redeemedCashSerials.add(serial)) {
            return false;
        }
        setDirty();
        return true;
    }

    public void unredeemCash(UUID serial) {
        if (redeemedCashSerials.remove(serial)) {
            setDirty();
        }
    }

    public void registerCashIssue(UUID serial, CurrencyId currency, long value) {
        Objects.requireNonNull(serial, "serial");
        requirePositive(value);
        requireCurrency(currency);
        CashIssue existing = issuedCash.putIfAbsent(serial, new CashIssue(currency, value));
        if (existing != null && (!existing.currency().equals(currency) || existing.value() != value)) {
            throw new IllegalArgumentException("Cash serial is already registered with different terms");
        }
        setDirty();
    }

    public void unregisterCashIssue(UUID serial) {
        if (issuedCash.remove(serial) != null) {
            setDirty();
        }
    }

    public boolean matchesCashIssue(UUID serial, CurrencyId currency, long value) {
        CashIssue issue = issuedCash.get(serial);
        return issue != null && issue.currency().equals(currency) && issue.value() == value;
    }

    private void mint(CurrencyId currency, AccountId destination, long amount, String reason, UUID transactionId) {
        requirePositive(amount);
        CurrencyDefinition definition = requireCurrency(currency);
        AccountKey key = new AccountKey(currency, destination);
        long destinationBalance = Math.addExact(balance(currency, destination), amount);
        CurrencyDefinition updatedDefinition = definition.withSupply(Math.addExact(definition.supply(), amount));
        balances.put(key, destinationBalance);
        currencies.put(currency, updatedDefinition);
        appendTransaction(new LedgerTransaction(
                transactionId,
                System.currentTimeMillis(),
                currency,
                SYSTEM_ACCOUNT,
                destination,
                amount,
                reason
        ));
        setDirty();
    }

    public void transfer(
            CurrencyId currency,
            AccountId from,
            AccountId to,
            long amount,
            String reason
    ) {
        requirePositive(amount);
        requireCurrency(currency);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        long sourceBalance = balance(currency, from);
        if (sourceBalance < amount) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        long destinationBalance = Math.addExact(balance(currency, to), amount);
        balances.put(new AccountKey(currency, from), sourceBalance - amount);
        balances.put(new AccountKey(currency, to), destinationBalance);
        appendTransaction(new LedgerTransaction(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                currency,
                from,
                to,
                amount,
                reason
        ));
        setDirty();
    }

    /** Applies one source-to-many transfer atomically after validating every destination. */
    public void transferBatch(
            CurrencyId currency,
            AccountId from,
            Map<AccountId, Long> destinations,
            String reason
    ) {
        requireCurrency(currency);
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(destinations, "destinations");
        Objects.requireNonNull(reason, "reason");
        if (destinations.isEmpty()) {
            return;
        }
        long total = 0L;
        for (Map.Entry<AccountId, Long> entry : destinations.entrySet()) {
            AccountId destination = Objects.requireNonNull(entry.getKey(), "destination");
            long amount = Objects.requireNonNull(entry.getValue(), "amount");
            requirePositive(amount);
            if (from.equals(destination)) {
                throw new IllegalArgumentException("Source and destination accounts must differ");
            }
            Math.addExact(balance(currency, destination), amount);
            total = Math.addExact(total, amount);
        }
        long sourceBalance = balance(currency, from);
        if (sourceBalance < total) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        balances.put(new AccountKey(currency, from), sourceBalance - total);
        for (Map.Entry<AccountId, Long> entry : destinations.entrySet()) {
            AccountId destination = entry.getKey();
            long amount = entry.getValue();
            balances.put(new AccountKey(currency, destination),
                    Math.addExact(balance(currency, destination), amount));
            appendTransaction(new LedgerTransaction(
                    UUID.randomUUID(), System.currentTimeMillis(), currency, from, destination, amount, reason
            ));
        }
        setDirty();
    }

    /** Applies a source-to-many transfer once, allowing recurring jobs to retry safely after a restart. */
    public boolean transferBatchOnce(
            CurrencyId currency,
            AccountId from,
            Map<AccountId, Long> destinations,
            String reason,
            UUID jobId
    ) {
        Objects.requireNonNull(jobId, "jobId");
        if (completedTransferJobs.contains(jobId)) {
            return false;
        }
        transferBatch(currency, from, destinations, reason);
        completedTransferJobs.add(jobId);
        setDirty();
        return true;
    }

    public Deque<LedgerTransaction> transactions() {
        return new ArrayDeque<>(transactions);
    }

    public long nextRolePayrollAtMillis() {
        return nextRolePayrollAtMillis;
    }

    public void setNextRolePayrollAtMillis(long nextRolePayrollAtMillis) {
        if (nextRolePayrollAtMillis < 0) {
            throw new IllegalArgumentException("Payroll timestamp cannot be negative");
        }
        this.nextRolePayrollAtMillis = nextRolePayrollAtMillis;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag serializedCurrencies = new ListTag();
        currencies.values().stream()
                .sorted((first, second) -> first.id().value().compareTo(second.id().value()))
                .forEach(definition -> {
                    CompoundTag currency = new CompoundTag();
                    currency.putString("id", definition.id().value());
                    currency.putString("name", definition.name());
                    currency.putString("mode", definition.mode().name());
                    currency.putLong("supply", definition.supply());
                    currency.putLong("purchasing_power_bps", definition.purchasingPowerBps());
                    serializedCurrencies.add(currency);
                });
        tag.put("currencies", serializedCurrencies);

        ListTag serializedBalances = new ListTag();
        balances.entrySet().stream()
                .sorted((first, second) -> first.getKey().toString().compareTo(second.getKey().toString()))
                .forEach(entry -> {
                    CompoundTag balance = new CompoundTag();
                    balance.putString("currency", entry.getKey().currency().value());
                    balance.putString("account", entry.getKey().account().value());
                    balance.putLong("amount", entry.getValue());
                    serializedBalances.add(balance);
                });
        tag.put("balances", serializedBalances);

        ListTag serializedTransactions = new ListTag();
        transactions.forEach(transaction -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", transaction.id());
            value.putLong("timestamp", transaction.timestampMillis());
            value.putString("currency", transaction.currency().value());
            value.putString("from", transaction.from().value());
            value.putString("to", transaction.to().value());
            value.putLong("amount", transaction.amount());
            value.putString("reason", transaction.reason());
            serializedTransactions.add(value);
        });
        tag.put("transactions", serializedTransactions);
        ListTag serializedJobs = new ListTag();
        completedMintingJobs.stream().sorted().forEach(job -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", job);
            serializedJobs.add(value);
        });
        tag.put("completed_minting_jobs", serializedJobs);
        ListTag serializedTransfers = new ListTag();
        completedTransferJobs.stream().sorted().forEach(job -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", job);
            serializedTransfers.add(value);
        });
        tag.put("completed_transfer_jobs", serializedTransfers);
        ListTag redeemed = new ListTag();
        redeemedCashSerials.stream().sorted().forEach(serial -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", serial);
            redeemed.add(value);
        });
        tag.put("redeemed_cash_serials", redeemed);
        ListTag issued = new ListTag();
        issuedCash.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", entry.getKey());
                    value.putString("currency", entry.getValue().currency().value());
                    value.putLong("value", entry.getValue().value());
                    issued.add(value);
                });
        tag.put("issued_cash", issued);
        tag.putLong("next_role_payroll_at_millis", nextRolePayrollAtMillis);
        tag.putLong("next_inflation_at_millis", nextInflationAtMillis);
        return tag;
    }

    private CurrencyDefinition requireCurrency(CurrencyId id) {
        return Optional.ofNullable(currencies.get(Objects.requireNonNull(id, "id")))
                .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + id.value()));
    }

    private void appendTransaction(LedgerTransaction transaction) {
        transactions.addLast(transaction);
        while (transactions.size() > MAX_TRANSACTION_HISTORY) {
            transactions.removeFirst();
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private record AccountKey(CurrencyId currency, AccountId account) {
        private AccountKey {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(account, "account");
        }
    }

    private record CashIssue(CurrencyId currency, long value) {
        private CashIssue {
            Objects.requireNonNull(currency, "currency");
            requirePositive(value);
        }
    }
}
