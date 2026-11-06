package io.github.heybroy0.cmbe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import io.github.heybroy0.cmbe.piston.PistonMovingBlockEntityExtension;
import io.github.heybroy0.cmbe.piston.PistonPushGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin extends BlockEntity implements PistonMovingBlockEntityExtension {
    @Shadow private BlockState movedState;

    @Unique private static final Codec<BlockEntityType<?>> CMBE_TYPE_CODEC =
        BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec();
    @Unique private static final String CMBE_PAYLOAD_KEY = "cmbe_carried_block_entity";
    @Unique private @Nullable BlockEntity cmbe$carried;

    protected PistonMovingBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void cmbe$setCarriedBlockEntity(@Nullable BlockEntity blockEntity) {
        this.cmbe$carried = blockEntity;
    }

    @Override
    public @Nullable BlockEntity cmbe$takeCarriedBlockEntity() {
        BlockEntity value = this.cmbe$carried;
        this.cmbe$carried = null;
        return value;
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cmbe$loadCarriedBlockEntity(ValueInput input, CallbackInfo ci) {
        Optional<ValueInput> child = input.child(CMBE_PAYLOAD_KEY);
        if (child.isEmpty()) return;
        ValueInput payload = child.get();
        Optional<BlockEntityType<?>> type = payload.read("id", CMBE_TYPE_CODEC);
        if (type.isEmpty()) return;
        BlockPos savedPos = new BlockPos(
            payload.getIntOr("x", this.worldPosition.getX()),
            payload.getIntOr("y", this.worldPosition.getY()),
            payload.getIntOr("z", this.worldPosition.getZ())
        );
        BlockEntity restored = type.get().create(savedPos, this.movedState);
        if (restored != null) {
            restored.loadWithComponents(payload);
            this.cmbe$carried = restored;
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cmbe$saveCarriedBlockEntity(ValueOutput output, CallbackInfo ci) {
        if (this.cmbe$carried != null) {
            this.cmbe$carried.saveWithFullMetadata(output.child(CMBE_PAYLOAD_KEY));
        }
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 1
        )
    )
    private static boolean cmbe$landAgainstPushGroup(
        Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original
    ) {
        boolean placed = PistonPushGroups.duringLanding(pos, () -> {
            boolean result = original.call(level, pos, state, flags);
            if (result) PistonPushGroups.fastLandMultiblockCompanion(level, pos);
            return result;
        });
        if (placed) PistonPushGroups.memberLanded(pos);
        return placed;
    }

    @WrapOperation(
        method = "finalTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean cmbe$fastLandAgainstPushGroup(
        Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original
    ) {
        boolean placed = PistonPushGroups.duringLanding(pos, () -> {
            boolean result = original.call(level, pos, state, flags);
            if (result) PistonPushGroups.fastLandMultiblockCompanion(level, pos);
            return result;
        });
        if (placed) PistonPushGroups.memberLanded(pos);
        return placed;
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            shift = At.Shift.AFTER,
            ordinal = 1
        )
    )
    private static void cmbe$restoreOnAnimatedLanding(
        Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity moving, CallbackInfo ci
    ) {
        if (level.isClientSide()) return;
        cmbe$restore(level, pos, ((PistonMovingBlockEntityExtension) moving).cmbe$takeCarriedBlockEntity());
    }

    @Inject(
        method = "finalTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            shift = At.Shift.AFTER
        )
    )
    private void cmbe$restoreOnFastLanding(CallbackInfo ci) {
        if (this.level == null || this.level.isClientSide()) return;
        cmbe$restore(this.level, this.worldPosition,
            ((PistonMovingBlockEntityExtension) this).cmbe$takeCarriedBlockEntity());
    }

    @Unique
    private static void cmbe$restore(Level level, BlockPos pos, @Nullable BlockEntity payload) {
        if (payload == null) return;
        level.removeBlockEntity(pos);
        payload.setLevel(level);
        payload.worldPosition = pos.immutable();
        payload.clearRemoved();
        level.setBlockEntity(payload);
        payload.setChanged();
    }
}
