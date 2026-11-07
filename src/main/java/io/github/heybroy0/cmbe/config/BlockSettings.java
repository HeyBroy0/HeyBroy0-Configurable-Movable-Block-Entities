package io.github.heybroy0.cmbe.config;

public final class BlockSettings {
    public MovementMode movement = MovementMode.VANILLA;
    public ScheduledTickMode scheduledTicks = ScheduledTickMode.VANILLA;
    public int pushCostDenominator = 1;

    public BlockSettings copy() {
        BlockSettings copy = new BlockSettings();
        copy.movement = movement;
        copy.scheduledTicks = scheduledTicks;
        copy.pushCostDenominator = pushCostDenominator;
        return copy;
    }

    public BlockSettings normalized() {
        BlockSettings normalized = copy();
        if (normalized.movement == null) {
            normalized.movement = MovementMode.VANILLA;
        }
        if (normalized.scheduledTicks == null) {
            normalized.scheduledTicks = ScheduledTickMode.VANILLA;
        }
        normalized.pushCostDenominator = Math.max(1, normalized.pushCostDenominator);
        return normalized;
    }

    public boolean isDefault() {
        return movement == MovementMode.VANILLA
            && scheduledTicks == ScheduledTickMode.VANILLA
            && pushCostDenominator == 1;
    }

    public enum MovementMode {
        VANILLA,
        MOVABLE,
        BREAKABLE,
        IMMOVABLE
    }

    public enum ScheduledTickMode {
        VANILLA,
        TRANSFER,
        DUPLICATE
    }
}
