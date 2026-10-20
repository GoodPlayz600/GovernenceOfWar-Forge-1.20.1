package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.war.LaborSite;
import io.github.geopoliticalwar.persistence.LaborSitesSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Creates and pays out bounded production from occupied-claim labor sites. */
public final class LaborSiteService {
    private LaborSiteService() {
    }

    public static LaborSite create(ServerLevel level, ServerPlayer actor, ClaimId claimId, String itemId, long outputPerCycle) {
        var occupation = OccupationsSavedData.get(level).get(claimId)
                .filter(value -> value.settledAtMillis().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("The claim is not under active occupation"));
        if (!actor.hasPermissions(2)
                && !io.github.geopoliticalwar.governance.NationMembershipService.mayHoldOffice(
                level, actor.getUUID(), occupation.occupyingNation())) {
            throw new IllegalArgumentException("Only the occupying nation may create labor sites");
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            throw new IllegalArgumentException("Unknown output item");
        }
        if (outputPerCycle <= 0L) {
            throw new IllegalArgumentException("Output per cycle must be positive");
        }
        LaborSite site = new LaborSite(UUID.randomUUID(), claimId, occupation.occupyingNation(),
                itemId, outputPerCycle, 0L, System.currentTimeMillis());
        LaborSitesSavedData.get(level).add(site);
        return site;
    }

    public static long produceForCaptive(ServerLevel level, ClaimId claimId, long now) {
        LaborSitesSavedData.get(level).produceForClaim(
                claimId, 1L, now, ServerConfig.LABOR_SITE_BUFFER_CAPACITY.get()
        );
        return 1L;
    }

    public static long collect(ServerLevel level, ServerPlayer actor, UUID siteId) {
        LaborSitesSavedData data = LaborSitesSavedData.get(level);
        LaborSite site = data.get(siteId).orElseThrow(() -> new IllegalArgumentException("Unknown labor site"));
        if (!actor.hasPermissions(2)
                && !io.github.geopoliticalwar.governance.NationMembershipService.mayHoldOffice(
                level, actor.getUUID(), site.controller())) {
            throw new IllegalArgumentException("Only the labor-site controller may collect output");
        }
        if (site.bufferedOutput() <= 0L) {
            throw new IllegalArgumentException("The labor site has no buffered output");
        }
        ResourceLocation id = ResourceLocation.tryParse(site.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || site.bufferedOutput() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Labor-site output item is invalid");
        }
        ItemStack output = new ItemStack(BuiltInRegistries.ITEM.get(id), (int) site.bufferedOutput());
        long amount = site.bufferedOutput();
        java.util.List<ItemStack> before = actor.getInventory().items.stream().map(ItemStack::copy).toList();
        data.set(site.withdraw(amount));
        if (!actor.getInventory().add(output) || !output.isEmpty()) {
            for (int index = 0; index < before.size(); index++) {
                actor.getInventory().items.set(index, before.get(index));
            }
            actor.getInventory().setChanged();
            data.set(site);
            throw new IllegalArgumentException("Not enough inventory space for labor output");
        }
        return amount;
    }
}
