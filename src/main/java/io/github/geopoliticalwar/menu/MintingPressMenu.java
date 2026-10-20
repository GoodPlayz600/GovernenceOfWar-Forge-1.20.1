package io.github.geopoliticalwar.menu;

import io.github.geopoliticalwar.blockentity.MintingPressBlockEntity;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class MintingPressMenu extends AbstractContainerMenu {
    private final MintingPressBlockEntity press;
    private final ContainerLevelAccess access;

    public MintingPressMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, resolve(inventory, data));
    }

    public MintingPressMenu(int id, Inventory inventory, MintingPressBlockEntity press) {
        super(ModContent.MINTING_PRESS_MENU.get(), id);
        this.press = press;
        this.access = ContainerLevelAccess.create(press.getLevel(), press.getBlockPos());
        addSlot(new Slot(press.inventory(), 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModContent.BANKNOTE.get());
            }
        });
        addPlayerInventory(inventory);
        addDataSlots(press.data());
    }

    private static MintingPressBlockEntity resolve(Inventory inventory, FriendlyByteBuf data) {
        var position = data.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(position) instanceof MintingPressBlockEntity press)) {
            throw new IllegalArgumentException("Minting press is not present at the requested position");
        }
        return press;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!stillValid(access, player, ModContent.MINTING_PRESS.get())) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            return ClaimAccessService.canInteract(serverPlayer, level, press.getBlockPos());
        }
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != 0 || !(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) {
            return false;
        }
        return press.startJob(serverPlayer);
    }

    public int progress() {
        return press.progress();
    }

    public boolean active() {
        return press.active();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return empty;
        }
        ItemStack original = slot.getItem();
        ItemStack moved = original.copy();
        if (slotIndex == 0) {
            if (!moveItemStackTo(original, 1, slots.size(), true)) {
                return empty;
            }
        } else if (original.is(ModContent.BANKNOTE.get())) {
            if (!moveItemStackTo(original, 0, 1, false)) {
                return empty;
            }
        } else {
            return empty;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
