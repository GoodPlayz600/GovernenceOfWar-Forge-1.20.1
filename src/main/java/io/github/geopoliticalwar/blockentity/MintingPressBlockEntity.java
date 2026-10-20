package io.github.geopoliticalwar.blockentity;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.menu.MintingPressMenu;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.economy.PhysicalCurrencyService;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;

import java.util.UUID;

public final class MintingPressBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            MintingPressBlockEntity.this.setChanged();
            super.setChanged();
        }
    };
    private final SimpleContainerData data = new SimpleContainerData(2);
    private UUID destination;
    private UUID jobId;
    private long amount;

    public MintingPressBlockEntity(BlockPos position, BlockState state) {
        super(ModContent.MINTING_PRESS_BLOCK_ENTITY.get(), position, state);
    }

    public Container inventory() {
        return inventory;
    }

    public ContainerData data() {
        return data;
    }

    public int progress() {
        return data.get(0);
    }

    public boolean active() {
        return data.get(1) == 1;
    }

    public boolean startJob(ServerPlayer player) {
        if (level == null || !(level instanceof ServerLevel serverLevel)
                || (!player.hasPermissions(2)
                && !io.github.geopoliticalwar.governance.RolePermissionService.has(
                serverLevel,
                player,
                "economy.mint",
                java.util.Optional.empty()
        ))) {
            return false;
        }
        if (destination != null || !PhysicalCurrencyService.isBlankBanknote(inventory.getItem(0))) {
            return false;
        }
        inventory.removeItem(0, 1);
        destination = player.getUUID();
        jobId = UUID.randomUUID();
        amount = ServerConfig.MINTING_NOTE_VALUE.get();
        data.set(0, 0);
        data.set(1, 1);
        setChanged();
        player.sendSystemMessage(Component.literal("Minting press job started."));
        return true;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos position, BlockState state, MintingPressBlockEntity press) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (press.destination == null) {
            return;
        }
        int progress = Math.min(ServerConfig.MINTING_SPEED_TICKS.get(), press.data.get(0) + 1);
        press.data.set(0, progress);
        if (progress < ServerConfig.MINTING_SPEED_TICKS.get()) {
            return;
        }
        boolean completed = false;
        try {
            EconomySavedData.get(serverLevel).mintOnce(
                    new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get()),
                    new AccountId("player:" + press.destination),
                    press.amount,
                    "minting press job",
                    press.jobId
            );
            completed = true;
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(press.destination);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Minting press job completed: " + press.amount + " credits."));
            }
        } catch (RuntimeException failure) {
            GeopoliticalWarfare.LOGGER.error("Minting press job failed", failure);
        }
        if (completed) {
            press.destination = null;
            press.jobId = null;
            press.amount = 0L;
            press.data.set(0, 0);
            press.data.set(1, 0);
            press.setChanged();
        } else {
            press.data.set(0, Math.max(0, ServerConfig.MINTING_SPEED_TICKS.get() - 1));
            press.setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.geopoliticalwar.minting_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MintingPressMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", saveInventory());
        if (destination != null) {
            tag.putUUID("destination", destination);
            if (jobId != null) {
                tag.putUUID("job_id", jobId);
            }
            tag.putLong("amount", amount);
        }
        tag.putInt("progress", data.get(0));
        tag.putBoolean("active", data.get(1) == 1);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory", net.minecraft.nbt.Tag.TAG_LIST)) {
            loadInventory(tag.getList("inventory", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
        destination = tag.hasUUID("destination") ? tag.getUUID("destination") : null;
        jobId = tag.hasUUID("job_id") ? tag.getUUID("job_id") : null;
        amount = tag.getLong("amount");
        data.set(0, tag.getInt("progress"));
        data.set(1, tag.getBoolean("active") ? 1 : 0);
        if (data.get(1) == 0) {
            destination = null;
            jobId = null;
            amount = 0L;
        } else if (destination != null && jobId == null) {
            jobId = UUID.randomUUID();
            setChanged();
        }
    }

    private net.minecraft.nbt.ListTag saveInventory() {
        net.minecraft.nbt.ListTag values = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag value = new CompoundTag();
                value.putByte("Slot", (byte) slot);
                stack.save(value);
                values.add(value);
            }
        }
        return values;
    }

    private void loadInventory(net.minecraft.nbt.ListTag values) {
        inventory.clearContent();
        for (net.minecraft.nbt.Tag raw : values) {
            CompoundTag value = (CompoundTag) raw;
            int slot = value.getByte("Slot") & 255;
            if (slot < inventory.getContainerSize()) {
                inventory.setItem(slot, ItemStack.of(value));
            }
        }
    }
}
