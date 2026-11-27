package com.heybroy0.movableblockentities.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for blocks that must be resolved and moved as a multi-block unit.
 * The built-in handlers use each block's encoded structure, never a generic
 * adjacent-block scan.
 */
public final class PistonMultiblockHandlers {
    private static final List<PistonMultiblockHandler> HANDLERS = new CopyOnWriteArrayList<>();

    static {
        register(PistonMultiblockHandlers::findDoorCompanion);
        register(PistonMultiblockHandlers::findBedCompanion);
    }

    private PistonMultiblockHandlers() {
    }

    /** Register a handler for a custom multi-block structure. */
    public static void register(PistonMultiblockHandler handler) {
        HANDLERS.addIfAbsent(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Returns the union of companion positions reported by registered
     * handlers. Duplicate positions and the original position are discarded.
     */
    public static List<BlockPos> findCompanions(
            LevelReader level, BlockPos position, BlockState state
    ) {
        List<BlockPos> result = new ArrayList<>();
        for (PistonMultiblockHandler handler : HANDLERS) {
            Collection<BlockPos> companions = handler.findCompanions(level, position, state);
            if (companions == null) {
                continue;
            }
            for (BlockPos companion : companions) {
                if (companion == null || companion.equals(position)) {
                    continue;
                }
                BlockPos immutable = companion.immutable();
                if (!result.contains(immutable)) {
                    result.add(immutable);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Collection<BlockPos> findDoorCompanion(
            LevelReader level, BlockPos position, BlockState state
    ) {
        if (!(state.getBlock() instanceof DoorBlock)) {
            return List.of();
        }

        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        BlockPos candidatePosition = half == DoubleBlockHalf.UPPER
                ? position.below() : position.above();
        BlockState candidate = level.getBlockState(candidatePosition);

        // Doors only pair vertically, with the same Block instance, opposite
        // HALF values, and identical FACING/OPEN/HINGE/POWERED values.
        if (candidate.getBlock() != state.getBlock()
                || candidate.getValue(DoorBlock.HALF) == half
                || !sameExcept(state, candidate, DoorBlock.HALF)) {
            return List.of();
        }
        return List.of(candidatePosition.immutable());
    }

    private static Collection<BlockPos> findBedCompanion(
            LevelReader level, BlockPos position, BlockState state
    ) {
        if (!(state.getBlock() instanceof AbstractBedBlock)) {
            return List.of();
        }

        BedPart part = state.getValue(AbstractBedBlock.PART);
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos candidatePosition = part == BedPart.HEAD
                ? position.relative(facing.getOpposite())
                : position.relative(facing);
        BlockState candidate = level.getBlockState(candidatePosition);

        // Beds pair only along FACING, with the same Block instance,
        // opposite PART values, and matching FACING/OCCUPIED values.
        if (candidate.getBlock() != state.getBlock()
                || candidate.getValue(AbstractBedBlock.PART) == part
                || !sameExcept(state, candidate, AbstractBedBlock.PART)) {
            return List.of();
        }
        return List.of(candidatePosition.immutable());
    }

    private static boolean sameExcept(BlockState first, BlockState second, Property<?> ignored) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }
        for (Property<?> property : first.getProperties()) {
            if (property != ignored
                    && !Objects.equals(first.getValues().get(property), second.getValues().get(property))) {
                return false;
            }
        }
        return true;
    }
}
