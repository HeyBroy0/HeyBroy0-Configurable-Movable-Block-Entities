package com.heybroy0.movableblockentities.api;

/**
 * Implemented at runtime by the piston moving block entity mixin. This ordinary
 * interface intentionally lives outside the configured Mixin package.
 */
public interface PistonMovingBlockEntityInvoker {
    void movableblockentities$invokeFinalTick();
}
