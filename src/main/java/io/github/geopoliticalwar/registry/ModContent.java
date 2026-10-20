package io.github.geopoliticalwar.registry;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.block.MintingPressBlock;
import io.github.geopoliticalwar.block.StrategicLinkBlock;
import io.github.geopoliticalwar.blockentity.MintingPressBlockEntity;
import io.github.geopoliticalwar.blockentity.StrategicLinkBlockEntity;
import io.github.geopoliticalwar.menu.MintingPressMenu;
import io.github.geopoliticalwar.menu.EstateBoardMenu;
import io.github.geopoliticalwar.item.BanknoteItem;
import io.github.geopoliticalwar.item.CoinItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;

/** Base blocks and items used by the geopolitical systems. */
public final class ModContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            GeopoliticalWarfare.MOD_ID
    );
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            GeopoliticalWarfare.MOD_ID
    );
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            GeopoliticalWarfare.MOD_ID
    );
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES,
            GeopoliticalWarfare.MOD_ID
    );
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES,
            GeopoliticalWarfare.MOD_ID
    );

    public static final RegistryObject<Block> CLAIM_ANCHOR = registerBlock(
            "claim_anchor",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(8.0F, 12.0F),
            StrategicLinkBlock::new
    );
    public static final RegistryObject<Block> SIEGE_ANCHOR = registerBlock(
            "siege_anchor",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(10.0F, 16.0F),
            StrategicLinkBlock::new
    );
    public static final RegistryObject<Block> CAPITAL_VAULT = registerBlock(
            "capital_vault",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(20.0F, 32.0F),
            StrategicLinkBlock::new
    );
    public static final RegistryObject<Block> MINTING_PRESS = registerBlock(
            "minting_press",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 8.0F),
            MintingPressBlock::new
    );
    public static final RegistryObject<Block> ESTATE_BOARD = registerBlock(
            "estate_board",
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F),
            StrategicLinkBlock::new
    );
    public static final RegistryObject<Block> CAPTURE_BEACON = registerBlock(
            "capture_beacon",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(6.0F, 10.0F),
            StrategicLinkBlock::new
    );
    public static final RegistryObject<Block> RESERVE_VAULT = registerBlock(
            "reserve_vault",
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(12.0F, 20.0F),
            StrategicLinkBlock::new
    );

    public static final RegistryObject<Item> SURVEYOR = ITEMS.register(
            "surveyor",
            () -> new Item(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> BANKNOTE = ITEMS.register(
            "banknote",
            () -> new BanknoteItem(new Item.Properties())
    );
    public static final RegistryObject<Item> COIN = ITEMS.register(
            "coin",
            () -> new CoinItem(new Item.Properties())
    );

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<StrategicLinkBlockEntity>> STRATEGIC_LINK_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "strategic_link",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    StrategicLinkBlockEntity::new,
                    CLAIM_ANCHOR.get(), SIEGE_ANCHOR.get(), CAPITAL_VAULT.get(), ESTATE_BOARD.get(), CAPTURE_BEACON.get(), RESERVE_VAULT.get()
            ).build(null)
    );
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MintingPressBlockEntity>> MINTING_PRESS_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "minting_press",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MintingPressBlockEntity::new, MINTING_PRESS.get()).build(null)
    );
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MintingPressMenu>> MINTING_PRESS_MENU = MENUS.register(
            "minting_press",
            () -> IForgeMenuType.create(MintingPressMenu::new)
    );
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<EstateBoardMenu>> ESTATE_BOARD_MENU = MENUS.register(
            "estate_board",
            () -> IForgeMenuType.create(EstateBoardMenu::new)
    );

    public static final RegistryObject<Item> CLAIM_ANCHOR_ITEM = registerBlockItem("claim_anchor", CLAIM_ANCHOR);
    public static final RegistryObject<Item> SIEGE_ANCHOR_ITEM = registerBlockItem("siege_anchor", SIEGE_ANCHOR);
    public static final RegistryObject<Item> CAPITAL_VAULT_ITEM = registerBlockItem("capital_vault", CAPITAL_VAULT);
    public static final RegistryObject<Item> MINTING_PRESS_ITEM = registerBlockItem("minting_press", MINTING_PRESS);
    public static final RegistryObject<Item> ESTATE_BOARD_ITEM = registerBlockItem("estate_board", ESTATE_BOARD);
    public static final RegistryObject<Item> CAPTURE_BEACON_ITEM = registerBlockItem("capture_beacon", CAPTURE_BEACON);
    public static final RegistryObject<Item> RESERVE_VAULT_ITEM = registerBlockItem("reserve_vault", RESERVE_VAULT);

    public static final RegistryObject<CreativeModeTab> GEOPOLITICAL_TAB = TABS.register(
            "geopolitical",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> CLAIM_ANCHOR_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CLAIM_ANCHOR_ITEM.get());
                        output.accept(SIEGE_ANCHOR_ITEM.get());
                        output.accept(CAPITAL_VAULT_ITEM.get());
                        output.accept(MINTING_PRESS_ITEM.get());
                        output.accept(ESTATE_BOARD_ITEM.get());
                        output.accept(CAPTURE_BEACON_ITEM.get());
                        output.accept(RESERVE_VAULT_ITEM.get());
                        output.accept(SURVEYOR.get());
                        output.accept(BANKNOTE.get());
                        output.accept(COIN.get());
                    })
                    .build()
    );

    private ModContent() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        TABS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
    }

    private static RegistryObject<Block> registerBlock(
            String name,
            BlockBehaviour.Properties properties,
            java.util.function.Function<BlockBehaviour.Properties, Block> factory
    ) {
        return BLOCKS.register(name, () -> factory.apply(properties));
    }

    private static RegistryObject<Item> registerBlockItem(
            String name,
            RegistryObject<Block> block
    ) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
