package io.github.heybroy0.cmbe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import io.github.heybroy0.cmbe.piston.PistonMovingBlockEntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = PistonBaseBlock.class, priority = 943)
public abstract class PistonBaseBlockMixin {
    @WrapOperation(
        method = "isPushable",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z"
        )
    )
    private static boolean cmbe$permitConfiguredBlockEntity(
        BlockState state,
        Operation<Boolean> original
    ) {
        if (ConfigStore.get(state).movement == BlockConfig.Movement.MOVABLE) return false;
        return original.call(state);
    }

    @Inject(
        method = "moveBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 1
        )
    )
    private void cmbe$capturePayloadAndTicks(
        Level level,
        BlockPos pistonPos,
        Direction direction,
        boolean extending,
        CallbackInfoReturnable<Boolean> cir,
        @Local(name = "pos") BlockPos pos,
        @Local(name = "pushDirection") Direction pushDirection,
        @Local(name = "toPushShapes") List<BlockState> toPushShapes,
        @Local(name = "i") int i,
        @Share(value = "cmbe$carriedBlockEntity", namespace = "cmbe") LocalRef<BlockEntity> carried
    ) {
        carried.set(null);
        BlockState movedState = toPushShapes.get(i);
        BlockPos source = pos.relative(pushDirection.getOpposite());

        if (ConfigStore.get(movedState).movement == BlockConfig.Movement.MOVABLE
            && level.getBlockEntity(source) instanceof BlockEntity blockEntity) {
            carried.set(blockEntity);
            level.removeBlockEntity(source);
        }

        cmbe$moveScheduledTicks(level, source, pos, movedState);
    }

    @WrapOperation(
        method = "moveBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/MovingPistonBlock;newMovingBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            ordinal = 0
        )
    )
    private BlockEntity cmbe$attachPayloadToMovingEntity(
        BlockPos position,
        BlockState blockState,
        BlockState movedState,
        Direction direction,
        boolean extending,
        boolean sourcePiston,
        Operation<BlockEntity> original,
        @Share(value = "cmbe$carriedBlockEntity", namespace = "cmbe") LocalRef<BlockEntity> carried
    ) {
        BlockEntity movingEntity = original.call(position, blockState, movedState, direction, extending, sourcePiston);
        if (movingEntity instanceof PistonMovingBlockEntityExtension extension) {
            extension.cmbe$setCarriedBlockEntity(carried.get());
        }
        return movingEntity;
    }

    @SuppressWarnings("unchecked")
    private static void cmbe$moveScheduledTicks(Level level, BlockPos source, BlockPos destination, BlockState movedState) {
        BlockConfig.ScheduledTicks mode = ConfigStore.get(movedState).scheduledTicks;
        if (mode == BlockConfig.ScheduledTicks.VANILLA || !(level instanceof ServerLevel serverLevel)) return;

        ServerLevelAccessor access = (ServerLevelAccessor) serverLevel;
        LevelTicks<Block> ticks = access.cmbe$getBlockTicks();
        BoundingBox sourceBox = new BoundingBox(
            source.getX(), source.getY(), source.getZ(),
            source.getX(), source.getY(), source.getZ()
        );
        Vec3i offset = new Vec3i(
            destination.getX() - source.getX(),
            destination.getY() - source.getY(),
            destination.getZ() - source.getZ()
        );

        ticks.copyArea(sourceBox, offset);
        if (mode == BlockConfig.ScheduledTicks.TRANSFER) ticks.clearArea(sourceBox);
    }
}
