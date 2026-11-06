package io.github.heybroy0.cmbe.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Vanilla adapters for the common two-part structures. */
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
}
