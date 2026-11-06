package io.github.heybroy0.cmbe.config;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockConfig {
    public enum Movement { VANILLA, MOVABLE, BREAKABLE, IMMOVABLE }
    public enum ScheduledTicks { VANILLA, TRANSFER, DUPLICATE }
    public enum Adhesion { VANILLA, ADHESIVE, GLAZED, HITBOX, ADVANCED }
    public enum GeometrySource { OUTLINE, COLLISION }

    public Movement movement = Movement.VANILLA;
    public ScheduledTicks scheduledTicks = ScheduledTicks.VANILLA;
    public int pushDenominator = 1;
    public Adhesion adhesion = Adhesion.VANILLA;

    public GeometrySource geometrySource = GeometrySource.OUTLINE;
    public int hitboxMinimumSquare = 3;
    public int hitboxDepth = 2;

    // Advanced mode uses an optional Direction-valued property as the local front.
    // An empty property means the six manual faces are absolute world directions.
    public String orientationProperty = "";
    public boolean manualFront;
    public boolean manualBack;
    public boolean manualLeft;
    public boolean manualRight;
    public boolean manualUp;
    public boolean manualDown;

    public BlockConfig copy() {
        BlockConfig copy = new BlockConfig();
        copy.movement = movement == null ? Movement.VANILLA : movement;
        copy.scheduledTicks = scheduledTicks == null ? ScheduledTicks.VANILLA : scheduledTicks;
        copy.pushDenominator = sanitizeDenominator(pushDenominator);
        copy.adhesion = adhesion == null ? Adhesion.VANILLA : adhesion;
        copy.geometrySource = geometrySource == null ? GeometrySource.OUTLINE : geometrySource;
        copy.hitboxMinimumSquare = clamp(hitboxMinimumSquare == 0 ? 3 : hitboxMinimumSquare, 1, 16);
        copy.hitboxDepth = clamp(hitboxDepth == 0 ? 2 : hitboxDepth, 1, 16);
        copy.orientationProperty = orientationProperty == null ? "" : orientationProperty;
        copy.manualFront = manualFront;
        copy.manualBack = manualBack;
        copy.manualLeft = manualLeft;
        copy.manualRight = manualRight;
        copy.manualUp = manualUp;
        copy.manualDown = manualDown;
        return copy;
    }

    public boolean manualFaceEnabled(Direction worldFace, BlockState state) {
        Direction front = resolveManualFront(state);
        if (front == null || front.getAxis() == Direction.Axis.Y) {
            return switch (worldFace) {
                case DOWN -> manualDown;
                case UP -> manualUp;
                case NORTH -> manualFront;
                case SOUTH -> manualBack;
                case WEST -> manualLeft;
                case EAST -> manualRight;
            };
        }

        Direction back = front.getOpposite();
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        if (worldFace == front) return manualFront;
        if (worldFace == back) return manualBack;
        if (worldFace == left) return manualLeft;
        if (worldFace == right) return manualRight;
        return worldFace == Direction.UP ? manualUp : manualDown;
    }

    private Direction resolveManualFront(BlockState state) {
        if (orientationProperty.isBlank()) return null;
        Property<?> rawProperty = state.getBlock().getStateDefinition().getProperty(orientationProperty);
        if (rawProperty == null || !state.hasProperty(rawProperty)) return null;
        return directionValue(state, rawProperty);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Direction directionValue(BlockState state, Property property) {
        Object value = state.getValue(property);
        return value instanceof Direction direction ? direction : null;
    }

    public boolean isDefault() {
        return movement == Movement.VANILLA
            && scheduledTicks == ScheduledTicks.VANILLA
            && pushDenominator == 1
            && adhesion == Adhesion.VANILLA
            && geometrySource == GeometrySource.OUTLINE
            && hitboxMinimumSquare == 3
            && hitboxDepth == 2
            && orientationProperty.isBlank()
            && !manualFront && !manualBack && !manualLeft
            && !manualRight && !manualUp && !manualDown;
    }

    public static int sanitizeDenominator(int value) {
        return clamp(value, 1, 1_000_000);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
