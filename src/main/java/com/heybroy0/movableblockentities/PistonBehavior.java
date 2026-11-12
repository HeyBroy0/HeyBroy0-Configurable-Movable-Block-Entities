package com.heybroy0.movableblockentities;

import com.heybroy0.movableblockentities.core.RuntimeConfigCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Runtime interpretation of the GUI-independent cache schema. */
public final class PistonBehavior {
    private static final BigInteger PUSH_CAPACITY = BigInteger.valueOf(12);
    private static final int MAX_RESOLVER_BLOCKS = 4092;

    private PistonBehavior() {
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getResourceKey(state.getBlock())
                .map(key -> key.identifier().toString())
                .orElse("");
    }

    public static RuntimeConfigCache.BlockSettings settings(BlockState state) {
        return RuntimeConfigCache.get(blockId(state));
    }

    public static Movement movement(BlockState state) {
        try {
            return Movement.valueOf(settings(state).movement());
        } catch (IllegalArgumentException exception) {
            return Movement.VANILLA;
        }
    }

    public static Adhesion adhesion(BlockState state) {
        try {
            return Adhesion.valueOf(settings(state).adhesion());
        } catch (IllegalArgumentException exception) {
            return Adhesion.VANILLA;
        }
    }

    /** Vanilla-compatible reaction values used by the piston resolver. */
    public static net.minecraft.world.level.material.PushReaction pushReaction(
            BlockState state, net.minecraft.world.level.material.PushReaction vanilla
    ) {
        return switch (movement(state)) {
            case MOVABLE -> net.minecraft.world.level.material.PushReaction.NORMAL;
            case BREAKABLE -> net.minecraft.world.level.material.PushReaction.DESTROY;
            case IMMOVABLE -> net.minecraft.world.level.material.PushReaction.BLOCK;
            case VANILLA -> vanilla;
        };
    }

    public static boolean isSticky(BlockState state) {
        return switch (adhesion(state)) {
            case ADHESIVE -> true;
            case GLAZED -> false;
            case VANILLA, HITBOX, ADVANCED ->
                    state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
        };
    }

    public static boolean canStickToEachOther(BlockState first, BlockState second) {
        Adhesion firstAdhesion = adhesion(first);
        Adhesion secondAdhesion = adhesion(second);
        if (firstAdhesion == Adhesion.GLAZED || secondAdhesion == Adhesion.GLAZED) {
            return false;
        }

        boolean vanillaSlimeHoneyConflict =
                first.is(Blocks.SLIME_BLOCK) && second.is(Blocks.HONEY_BLOCK)
                        || first.is(Blocks.HONEY_BLOCK) && second.is(Blocks.SLIME_BLOCK);
        boolean explicitlyAdhesive = firstAdhesion == Adhesion.ADHESIVE
                || secondAdhesion == Adhesion.ADHESIVE;
        return !vanillaSlimeHoneyConflict || explicitlyAdhesive;
    }

    /**
     * The resolver's vanilla list bound is a physical-block guard, not the
     * weighted capacity itself. Allow enough physical entries for the largest
     * configured denominator, capped to avoid pathological resolver walks.
     */
    public static int resolverLimit() {
        int largestDenominator = 1;
        for (RuntimeConfigCache.BlockSettings entry : RuntimeConfigCache.snapshot().values()) {
            if (!"VANILLA".equals(entry.movement())) {
                largestDenominator = Math.max(largestDenominator, Math.max(1, entry.pushCost()));
            }
        }
        return 12 * Math.min(largestDenominator, Math.max(1, MAX_RESOLVER_BLOCKS / 12));
    }

    /**
     * Push cost is the rational sum of 1/denominator per physical block.
     * Door/bed companions count once even when the resolver lists both halves.
     */
    public static boolean withinPushCapacity(Level level, Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return true;
        }

        Set<BlockPos> physicalBlocks = new LinkedHashSet<>();
        for (BlockPos position : positions) {
            BlockPos immutable = position.immutable();
            physicalBlocks.add(immutable);
            addMultiblockCompanion(level, immutable, level.getBlockState(immutable), physicalBlocks);
        }

        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        for (BlockPos position : physicalBlocks) {
            int divisor = pushCostDenominator(level.getBlockState(position));
            BigInteger blockDenominator = BigInteger.valueOf(divisor);
            numerator = numerator.multiply(blockDenominator).add(denominator);
            denominator = denominator.multiply(blockDenominator);

            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);

            if (numerator.compareTo(denominator.multiply(PUSH_CAPACITY)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static int pushCostDenominator(BlockState state) {
        RuntimeConfigCache.BlockSettings entry = settings(state);
        // Vanilla movement keeps the exact vanilla 12-block capacity rules.
        return "VANILLA".equals(entry.movement()) ? 1 : Math.max(1, entry.pushCost());
    }

    private static void addMultiblockCompanion(
            Level level, BlockPos position, BlockState state, Set<BlockPos> physicalBlocks
    ) {
        BlockPos companion = null;
        if (state.getBlock() instanceof DoorBlock) {
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            BlockPos candidate = half == DoubleBlockHalf.UPPER ? position.below() : position.above();
            BlockState other = level.getBlockState(candidate);
            if (other.getBlock() == state.getBlock()
                    && other.getValue(DoorBlock.HALF) != half) {
                companion = candidate;
            }
        } else if (state.getBlock() instanceof AbstractBedBlock) {
            BedPart part = state.getValue(AbstractBedBlock.PART);
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos candidate = part == BedPart.HEAD
                    ? position.relative(facing.getOpposite())
                    : position.relative(facing);
            BlockState other = level.getBlockState(candidate);
            if (other.getBlock() == state.getBlock()
                    && other.getValue(AbstractBedBlock.PART) != part) {
                companion = candidate;
            }
        }

        if (companion != null) {
            physicalBlocks.add(companion.immutable());
        }
    }

    public static boolean isInsidePistonMovementBounds(Level level, BlockPos source, BlockPos destination) {
        return source.getY() >= level.getMinY() && source.getY() < level.getMaxY()
                && destination.getY() >= level.getMinY() && destination.getY() < level.getMaxY()
                && level.getWorldBorder().isWithinBounds(source)
                && level.getWorldBorder().isWithinBounds(destination);
    }

    public enum Movement {
        VANILLA,
        MOVABLE,
        BREAKABLE,
        IMMOVABLE
    }

    public enum Adhesion {
        VANILLA,
        ADHESIVE,
        GLAZED,
        HITBOX,
        ADVANCED
    }
}
