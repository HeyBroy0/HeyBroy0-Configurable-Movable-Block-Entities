package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import com.heybroy0.movableblockentities.api.PistonMultiblockHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds registered multi-block companions through the resolver's own line and
 * branching routines, so companion push chains receive the same vanilla
 * pushability, collision and resolver-limit checks as ordinary blocks.
 */
@Mixin(PistonStructureResolver.class)
abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private Direction pushDirection;

    @Invoker("addBlockLine")
    protected abstract boolean movableblockentities$invokeAddBlockLine(BlockPos position, Direction direction);

    @Invoker("addBranchingBlocks")
    protected abstract boolean movableblockentities$invokeAddBranchingBlocks(BlockPos position);

    @ModifyConstant(
            method = {"resolve", "addBlockLine", "addBranchingBlocks"},
            constant = @Constant(intValue = 12),
            require = 0
    )
    private int movableblockentities$extendResolverWalk(int vanillaLimit) {
        return Math.max(vanillaLimit, PistonBehavior.resolverLimit());
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void movableblockentities$checkExpandedPush(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) {
            return;
        }

        if (!movableblockentities$expandMultiblockCompanions()) {
            callback.setReturnValue(false);
            return;
        }

        if (!PistonBehavior.withinPushCapacity(this.level, this.toPush)) {
            callback.setReturnValue(false);
            return;
        }
        MovementGroups.captureResolved(this.level, this.toPush, this.pushDirection);
    }

    private boolean movableblockentities$expandMultiblockCompanions() {
        // The original resolver has already processed branching for its own
        // entries. Anything appended below must receive equivalent handling.
        Set<BlockPos> originallyResolved = new HashSet<>(this.toPush);
        Set<BlockPos> queued = new HashSet<>(this.toPush);
        List<BlockPos> pending = new ArrayList<>(this.toPush);

        for (int index = 0; index < pending.size(); index++) {
            BlockPos position = pending.get(index);
            BlockState state = this.level.getBlockState(position);

            if (!originallyResolved.contains(position) && PistonBehavior.isSticky(state)) {
                if (!this.movableblockentities$invokeAddBranchingBlocks(position)) {
                    return false;
                }
                movableblockentities$appendNewEntries(pending, queued);
            }

            for (BlockPos companion : PistonMultiblockHandlers.findCompanions(
                    this.level, position, state
            )) {
                if (queued.contains(companion)) {
                    continue;
                }

                // Resolve the companion as a fresh line, rather than merely
                // appending its coordinate. This also resolves blocks in front
                // of it and fails if the complete structure cannot move.
                if (!this.movableblockentities$invokeAddBlockLine(companion, this.pushDirection)
                        || !this.toPush.contains(companion)) {
                    return false;
                }
                movableblockentities$appendNewEntries(pending, queued);
            }
        }
        return true;
    }

    private void movableblockentities$appendNewEntries(
            List<BlockPos> pending, Set<BlockPos> queued
    ) {
        for (BlockPos position : this.toPush) {
            BlockPos immutable = position.immutable();
            if (queued.add(immutable)) {
                pending.add(immutable);
            }
        }
    }
}
