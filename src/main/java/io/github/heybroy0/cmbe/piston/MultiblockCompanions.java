package io.github.heybroy0.cmbe.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vanilla structure adapters used by the piston resolver and landing coordinator.
 */
public final class MultiblockCompanions {
    private MultiblockCompanions() {}

    public static BlockPos findCompanion(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }

        if (state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
            return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }

        if (state.getBlock() instanceof AbstractBedBlock
            && state.hasProperty(AbstractBedBlock.PART)
            && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            BedPart part = state.getValue(AbstractBedBlock.PART);
            return part == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }

        if (state.getBlock() instanceof ChestBlock) {
            BlockPos partner = ChestBlock.getConnectedBlockPos(pos, state);
            return partner.equals(pos) ? null : partner;
        }

        return null;
    }

    /**
     * Adds complete vanilla companions to a resolved push group. This deliberately
     * fails closed if a companion is configured immovable, cannot be moved, or its
     * translated cell is blocked by a block outside the resolved group.
     */
    public static boolean includeVanillaCompanions(
        Level level,
        List<BlockPos> toPush,
        List<BlockPos> toDestroy,
        Direction pushDirection
    ) {
        Set<BlockPos> members = new HashSet<>(toPush);
        Set<BlockPos> destroyed = new HashSet<>(toDestroy);

        for (int index = 0; index < toPush.size(); index++) {
            BlockPos memberPos = toPush.get(index);
            BlockState memberState = level.getBlockState(memberPos);
            BlockPos companionPos = findCompanion(memberPos, memberState);
            if (companionPos == null || members.contains(companionPos)) continue;

            BlockState companionState = level.getBlockState(companionPos);
            if (companionState.isAir() || companionState.getBlock() != memberState.getBlock()) return false;
            if (companionState.getPistonPushReaction() != PushReaction.PUSH_PULL) return false;

            BlockPos companionDestination = companionPos.relative(pushDirection);
            BlockState destinationState = level.getBlockState(companionDestination);
            if (!destinationState.isAir()
                && !members.contains(companionDestination)
                && !destroyed.contains(companionDestination)) {
                return false;
            }

            if (toPush.size() >= PushCost.MAX_PHYSICAL_BLOCKS) return false;
            toPush.add(companionPos);
            members.add(companionPos);
        }

        return true;
    }
}
