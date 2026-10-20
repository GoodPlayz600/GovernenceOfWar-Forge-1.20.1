package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.governance.NationMembershipService;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.TaxMaterialTreasurySavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Collects physical property-tax tender without creating a second monetary balance. */
public final class TaxPaymentService {
    private TaxPaymentService() {
    }

    public static boolean tryCollect(ServerLevel level, Claim claim, TaxPolicy policy, long required) {
        if (policy.mode() == TaxMode.LEDGER) {
            return false;
        }
        for (ServerPlayer player : payers(level, claim)) {
            if (collectFrom(level, player, policy, required)) {
                return true;
            }
        }
        return false;
    }

    public static boolean payNow(ServerLevel level, ServerPlayer payer, Claim claim, TaxPolicy policy, long required) {
        return policy.mode() != TaxMode.LEDGER && collectFrom(level, payer, policy, required);
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean collectFrom(ServerLevel level, ServerPlayer payer, TaxPolicy policy, long required) {
        if (required <= 0L) {
            return false;
        }
        String requiredItem = policy.itemId();
        long available = payer.getInventory().items.stream()
                .filter(stack -> !stack.isEmpty() && itemId(stack).equals(requiredItem))
                .mapToLong(ItemStack::getCount)
                .sum();
        if (available < required) {
            return false;
        }
        TaxMaterialTreasurySavedData treasury = TaxMaterialTreasurySavedData.get(level);
        if (treasury.total() > ServerConfig.MATERIAL_TAX_TREASURY_CAPACITY.get() - required) {
            return false;
        }

        java.util.List<ItemStack> before = payer.getInventory().items.stream().map(ItemStack::copy).toList();
        treasury.deposit(requiredItem, required, ServerConfig.MATERIAL_TAX_TREASURY_CAPACITY.get());
        try {
            long remaining = required;
            for (ItemStack stack : payer.getInventory().items) {
                if (remaining == 0L) {
                    break;
                }
                if (!stack.isEmpty() && itemId(stack).equals(requiredItem)) {
                    long removed = Math.min(remaining, stack.getCount());
                    stack.shrink((int) removed);
                    remaining -= removed;
                }
            }
            if (remaining != 0L) {
                throw new IllegalStateException("Tax inventory changed during payment");
            }
            payer.getInventory().setChanged();
            return true;
        } catch (RuntimeException failure) {
            for (int index = 0; index < before.size(); index++) {
                payer.getInventory().items.set(index, before.get(index));
            }
            payer.getInventory().setChanged();
            treasury.withdraw(requiredItem, required);
            throw failure;
        }
    }

    private static List<ServerPlayer> payers(ServerLevel level, Claim claim) {
        List<ServerPlayer> result = new ArrayList<>();
        String owner = claim.owner().value();
        if (owner.startsWith("player:")) {
            try {
                ServerPlayer player = level.getServer().getPlayerList()
                        .getPlayer(UUID.fromString(owner.substring("player:".length())));
                if (player != null) {
                    result.add(player);
                }
                return result;
            } catch (IllegalArgumentException invalid) {
                return result;
            }
        }
        if (owner.startsWith("nation:")) {
            try {
                Nation nation = NationsSavedData.get(level)
                        .get(new io.github.geopoliticalwar.domain.governance.NationId(
                                UUID.fromString(owner.substring("nation:".length()))
                        ))
                        .orElse(null);
                if (nation != null) {
                    level.getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> NationMembershipService.mayHoldOffice(level, player.getUUID(), nation.id()))
                        .forEach(result::add);
                }
                return result;
            } catch (IllegalArgumentException invalid) {
                return result;
            }
        }
        return result;
    }
}
