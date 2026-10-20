package io.github.geopoliticalwar.block;

import io.github.geopoliticalwar.blockentity.MintingPressBlockEntity;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** Operator-controlled ledger minting press with a persisted one-slot job. */
public final class MintingPressBlock extends BaseEntityBlock {
    public MintingPressBlock(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new MintingPressBlockEntity(position, state);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            net.minecraft.world.entity.player.Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(position) instanceof MintingPressBlockEntity press)) {
            return InteractionResult.PASS;
        }
        NetworkHooks.openScreen(serverPlayer, press, position);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(position) instanceof MintingPressBlockEntity press) {
            Containers.dropContents(level, position, press.inventory());
        }
        super.onRemove(state, level, position, replacement, moving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModContent.MINTING_PRESS_BLOCK_ENTITY.get(), MintingPressBlockEntity::serverTick);
    }
}
