package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.api.PistonMovingBlockEntityInvoker;
import com.heybroy0.movableblockentities.api.PistonMovementMemberHolder;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the identity of the exact resolver member through movement and
 * landing. Companion coordination is based on the resolved relationship graph,
 * not on neighboring coordinates at landing time.
 */
@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityMixin
        implements PistonMovingBlockEntityInvoker, PistonMovementMemberHolder {

    @Unique
    private MovementGroups.PushMember movableblockentities$movementMember;

    @org.spongepowered.asm.mixin.gen.Invoker("finalTick")
    @Override
    public abstract void movableblockentities$invokeFinalTick();

    @Unique
    @Override
    public void movableblockentities$setMovementMember(MovementGroups.PushMember member) {
        this.movableblockentities$movementMember = member;
    }

    @Unique
    @Override
    public MovementGroups.PushMember movableblockentities$getMovementMember() {
        return this.movableblockentities$movementMember;
    }

    @Inject(method = "finalTick", at = @At("HEAD"))
    private void movableblockentities$coordinateMultiblockLanding(CallbackInfo callback) {
        MovementGroups.coordinateCompanions((PistonMovingBlockEntity) (Object) this);
    }
}
