package io.github.heybroy0.cmbe.config;

public final class BlockConfig {
    public enum Movement { VANILLA, MOVABLE, BREAKABLE, IMMOVABLE }
    public enum ScheduledTicks { VANILLA, TRANSFER, DUPLICATE }
    public enum Adhesion { VANILLA, ALL, NONE, HITBOX, ADVANCED }

    public Movement movement = Movement.VANILLA;
    public ScheduledTicks scheduledTicks = ScheduledTicks.VANILLA;
    public int pushDenominator = 1;
    public Adhesion adhesion = Adhesion.VANILLA;

    // Stored ahead of the functional HITBOX/ADVANCED editor.
    public int hitboxMinimumSquare = 3;
    public int hitboxDepth = 2;

    public BlockConfig copy() {
        BlockConfig copy = new BlockConfig();
        copy.movement = movement == null ? Movement.VANILLA : movement;
        copy.scheduledTicks = scheduledTicks == null ? ScheduledTicks.VANILLA : scheduledTicks;
        copy.pushDenominator = sanitizeDenominator(pushDenominator);
        copy.adhesion = adhesion == null ? Adhesion.VANILLA : adhesion;
        copy.hitboxMinimumSquare = clamp(hitboxMinimumSquare, 1, 16);
        copy.hitboxDepth = clamp(hitboxDepth, 1, 16);
        return copy;
    }

    public boolean isDefault() {
        return movement == Movement.VANILLA
            && scheduledTicks == ScheduledTicks.VANILLA
            && pushDenominator == 1
            && adhesion == Adhesion.VANILLA
            && hitboxMinimumSquare == 3
            && hitboxDepth == 2;
    }

    public static int sanitizeDenominator(int value) {
        return clamp(value, 1, 1_000_000);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
