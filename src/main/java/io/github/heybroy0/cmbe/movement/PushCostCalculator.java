package io.github.heybroy0.cmbe.movement;

import io.github.heybroy0.cmbe.config.BlockSettings;
import io.github.heybroy0.cmbe.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates the custom cost as an exact rational number.
 *
 * A denominator of N means one physical block consumes 1/N of a vanilla
 * push slot. BigInteger avoids floating-point rounding changing a 12-block
 * boundary decision.
 */
public final class PushCostCalculator {
    private static final BigInteger VANILLA_CAPACITY = BigInteger.valueOf(12);

    private PushCostCalculator() {
    }

    public static boolean isWithinVanillaCapacity(Level level, List<BlockPos> positions) {
        Set<BlockPos> counted = new HashSet<>();
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;

        for (BlockPos position : positions) {
            if (!counted.add(position)) {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
            BlockSettings settings = ConfigManager.get(blockId);
            if (settings.movement != BlockSettings.MovementMode.MOVABLE) {
                // Vanilla blocks retain vanilla push-limit accounting.
                continue;
            }

            BigInteger nextDenominator = BigInteger.valueOf(settings.pushCostDenominator);
            numerator = numerator.multiply(nextDenominator).add(denominator);
            denominator = denominator.multiply(nextDenominator);

            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);

            if (numerator.compareTo(denominator.multiply(VANILLA_CAPACITY)) > 0) {
                return false;
            }
        }

        return true;
    }
}
