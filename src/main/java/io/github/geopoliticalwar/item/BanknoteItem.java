package io.github.geopoliticalwar.item;

import net.minecraft.world.item.Item;

public final class BanknoteItem extends Item {
    public BanknoteItem(Properties properties) {
        super(properties.stacksTo(16));
    }
}
