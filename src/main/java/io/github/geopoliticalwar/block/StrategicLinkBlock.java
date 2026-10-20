package io.github.geopoliticalwar.block;

import io.github.geopoliticalwar.blockentity.StrategicLinkBlockEntity;
import io.github.geopoliticalwar.persistence.CapturePointsSavedData;
import io.github.geopoliticalwar.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;

/** Functional non-inventory strategic block that stores only a durable link. */
public final class StrategicLinkBlock extends BaseEntityBlock {
    public StrategicLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new StrategicLinkBlockEntity(position, state);
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
                || !(level.getBlockEntity(position) instanceof StrategicLinkBlockEntity link)) {
            return InteractionResult.PASS;
        }
        if (state.is(ModContent.CAPTURE_BEACON.get())) {
            if (!serverPlayer.hasPermissions(2)) {
                serverPlayer.sendSystemMessage(Component.literal("Only an operator may configure a capture beacon."));
                return InteractionResult.FAIL;
            }
            if (link.capturePoint().isEmpty()) {
                CapturePointsSavedData.get((ServerLevel) level).all().values().stream()
                        .filter(point -> point.dimension().value().equals(level.dimension().location().toString()))
                        .filter(point -> point.contains(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D))
                        .min(Comparator.comparingDouble(point -> {
                            double dx = point.center().x() - position.getX();
                            double dy = point.center().y() - position.getY();
                            double dz = point.center().z() - position.getZ();
                            return dx * dx + dy * dy + dz * dz;
                        }))
                        .ifPresentOrElse(point -> {
                            link.linkCapturePoint(point.id());
                            serverPlayer.sendSystemMessage(Component.literal("Capture beacon linked to " + point.name() + "."));
                        }, () -> serverPlayer.sendSystemMessage(Component.literal(
                                "No capture point overlaps this beacon. Create one at this location first."
                        )));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Capture beacon linked to " + link.capturePoint().get() + "."
                ));
            }
        } else if (state.is(ModContent.SIEGE_ANCHOR.get())) {
            if (!link.tryLinkSiege(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "This siege anchor must be placed inside an active conquest target by a participating attacker."
                ));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Siege anchor " + link.linkSummary() + " progress=" + link.progressSeconds() + " seconds."
                ));
            }
        } else if (state.is(ModContent.ESTATE_BOARD.get())) {
            NetworkHooks.openScreen(serverPlayer, link, buffer ->
                    {
                        buffer.writeBlockPos(position);
                        io.github.geopoliticalwar.economy.PropertyStatusSnapshot.at((ServerLevel) level, position)
                                .write(buffer);
                    }
            );
        } else {
            serverPlayer.sendSystemMessage(Component.literal(
                    "Strategic link " + link.linkSummary() + "."
            ));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModContent.STRATEGIC_LINK_BLOCK_ENTITY.get(), StrategicLinkBlockEntity::serverTick);
    }
}
