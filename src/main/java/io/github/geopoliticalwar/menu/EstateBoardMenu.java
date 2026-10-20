package io.github.geopoliticalwar.menu;

import io.github.geopoliticalwar.economy.PropertyStatusSnapshot;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Read-only estate status menu; mutations remain command/service controlled. */
public final class EstateBoardMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final PropertyStatusSnapshot snapshot;

    public EstateBoardMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(ModContent.ESTATE_BOARD_MENU.get(), id);
        var position = data.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(position)
                instanceof io.github.geopoliticalwar.blockentity.StrategicLinkBlockEntity)) {
            throw new IllegalArgumentException("Estate board is not present at the requested position");
        }
        this.access = ContainerLevelAccess.create(inventory.player.level(), position);
        this.snapshot = PropertyStatusSnapshot.read(data);
    }

    public EstateBoardMenu(int id, Inventory inventory, PropertyStatusSnapshot snapshot,
                           net.minecraft.core.BlockPos position) {
        super(ModContent.ESTATE_BOARD_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inventory.player.level(), position);
        this.snapshot = snapshot;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModContent.ESTATE_BOARD.get());
    }

    public PropertyStatusSnapshot snapshot() {
        return snapshot;
    }
}
