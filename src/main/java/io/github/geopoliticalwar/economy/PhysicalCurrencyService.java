package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Converts ledger balances to authenticated, denomination-bearing physical cash. */
public final class PhysicalCurrencyService {
    public static final String CURRENCY_TAG = "currency";
    public static final String VALUE_TAG = "value";
    public static final String SERIAL_TAG = "serial";
    public static final String ISSUED_TAG = "issued";
    public static final AccountId CASH_ACCOUNT = new AccountId("system:cash");
    private static final long[] NOTE_DENOMINATIONS = {1_000L, 100L, 10L, 1L};

    private PhysicalCurrencyService() {
    }

    public static long value(ItemStack stack) {
        return stack.getOrCreateTag().getLong(VALUE_TAG);
    }

    public static boolean isIssuedCash(ItemStack stack) {
        if (!(stack.is(ModContent.BANKNOTE.get()) || stack.is(ModContent.COIN.get()))) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(ISSUED_TAG) || !tag.hasUUID(SERIAL_TAG) || value(stack) <= 0) {
            return false;
        }
        try {
            taggedCurrency(stack);
            return true;
        } catch (IllegalArgumentException invalidCurrency) {
            return false;
        }
    }

    public static boolean isBlankBanknote(ItemStack stack) {
        return stack.is(ModContent.BANKNOTE.get())
                && (!stack.hasTag() || !stack.getOrCreateTag().getBoolean(ISSUED_TAG));
    }

    public static List<ItemStack> withdraw(ServerPlayer player, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        CurrencyId currency = currency();
        if (amount > ServerConfig.MAX_CASH_WITHDRAWAL_AMOUNT.get()) {
            throw new IllegalArgumentException("Withdrawal exceeds the configured cash limit");
        }
        EconomySavedData economy = EconomySavedData.get(player.serverLevel());
        List<ItemStack> cash = denominations(currency, amount);
        long emptySlots = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
        if (cash.size() > emptySlots) {
            throw new IllegalArgumentException("Not enough inventory space for that withdrawal");
        }
        List<ItemStack> before = player.getInventory().items.stream().map(ItemStack::copy).toList();
        List<UUID> registered = new ArrayList<>();
        economy.transfer(currency, playerAccount(player), CASH_ACCOUNT, amount, "cash withdrawal");
        try {
            for (ItemStack stack : cash) {
                UUID serial = stack.getOrCreateTag().getUUID(SERIAL_TAG);
                economy.registerCashIssue(serial, currency, value(stack));
                registered.add(serial);
            }
            for (ItemStack stack : cash) {
                if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                    throw new IllegalStateException("Cash could not be placed in inventory");
                }
            }
            return cash;
        } catch (RuntimeException failure) {
            for (int index = 0; index < before.size(); index++) {
                player.getInventory().items.set(index, before.get(index));
            }
            player.getInventory().setChanged();
            registered.forEach(economy::unregisterCashIssue);
            economy.transfer(currency, CASH_ACCOUNT, playerAccount(player), amount, "cash withdrawal rollback");
            throw failure;
        }
    }

    public static long depositHeld(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!isIssuedCash(held)) {
            throw new IllegalArgumentException("Hold an issued banknote or coin in your main hand");
        }
        long amount = value(held);
        EconomySavedData economy = EconomySavedData.get(player.serverLevel());
        UUID serial = held.getOrCreateTag().getUUID(SERIAL_TAG);
        CurrencyId currency = taggedCurrency(held);
        if (!economy.matchesCashIssue(serial, currency, amount)) {
            throw new IllegalArgumentException("That cash serial is not registered by this economy");
        }
        if (!economy.redeemCash(serial)) {
            throw new IllegalArgumentException("That cash serial has already been redeemed");
        }
        try {
            economy.transfer(currency, CASH_ACCOUNT, playerAccount(player), amount, "cash deposit");
            held.shrink(1);
        } catch (RuntimeException failure) {
            economy.unredeemCash(serial);
            throw failure;
        }
        return amount;
    }

    public static ItemStack issuedNote(CurrencyId currency, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Cash denomination must be positive");
        }
        ItemStack stack = new ItemStack(value >= 10 ? ModContent.BANKNOTE.get() : ModContent.COIN.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(CURRENCY_TAG, currency.value());
        tag.putLong(VALUE_TAG, value);
        tag.putUUID(SERIAL_TAG, UUID.randomUUID());
        tag.putBoolean(ISSUED_TAG, true);
        return stack;
    }

    private static List<ItemStack> denominations(CurrencyId currency, long amount) {
        List<ItemStack> result = new ArrayList<>();
        long remaining = amount;
        for (long denomination : NOTE_DENOMINATIONS) {
            while (remaining >= denomination) {
                ItemStack note = issuedNote(currency, denomination);
                result.add(note);
                remaining -= denomination;
            }
        }
        return result;
    }

    private static CurrencyId taggedCurrency(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.getString(CURRENCY_TAG).isBlank()) {
            throw new IllegalArgumentException("Cash item has no currency ID");
        }
        return new CurrencyId(tag.getString(CURRENCY_TAG));
    }

    private static CurrencyId currency() {
        return new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
    }

    private static AccountId playerAccount(ServerPlayer player) {
        return new AccountId("player:" + player.getUUID());
    }
}
