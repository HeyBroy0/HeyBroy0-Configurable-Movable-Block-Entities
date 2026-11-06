package io.github.heybroy0.cmbe.piston;

import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class PushCost {
    public static final int VANILLA_BUDGET = 12;
    public static final int MAX_PHYSICAL_BLOCKS = 128;

    private PushCost() {}

    public static boolean isWithinBudget(Level level, Collection<BlockPos> positions) {
        Set<BlockPos> unique = new HashSet<>(positions);
        if (unique.size() > MAX_PHYSICAL_BLOCKS) return false;

        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        BigInteger budget = BigInteger.valueOf(VANILLA_BUDGET);

        for (BlockPos pos : unique) {
            int divisor = ConfigStore.get(level.getBlockState(pos)).pushDenominator;
            BigInteger next = BigInteger.valueOf(divisor);
            numerator = numerator.multiply(next).add(denominator);
            denominator = denominator.multiply(next);
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
            if (numerator.compareTo(denominator.multiply(budget)) > 0) return false;
        }
        return true;
    }
}
