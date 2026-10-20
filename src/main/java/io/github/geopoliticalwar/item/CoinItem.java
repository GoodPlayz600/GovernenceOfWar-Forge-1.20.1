package io.github.geopoliticalwar.item;

import net.minecraft.world.item.Item;

public final class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties.stacksTo(64));
    }
}
