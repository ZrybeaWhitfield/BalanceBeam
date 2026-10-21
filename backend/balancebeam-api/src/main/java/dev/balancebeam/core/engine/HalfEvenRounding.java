package dev.balancebeam.core.engine;

import java.math.BigInteger;
import java.util.Objects;

final class HalfEvenRounding {
    private HalfEvenRounding() {
    }

    /** Divides a nonnegative numerator by a positive denominator, rounding ties to the nearest even integer. */
    static BigInteger divide(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator, "numerator cannot be null");
        Objects.requireNonNull(denominator, "denominator cannot be null");
        if (numerator.signum() < 0) {
            throw new IllegalArgumentException("numerator cannot be negative");
        }
        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }

        BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger quotient = division[0];
        BigInteger remainder = division[1];
        int comparisonToHalf = remainder.multiply(BigInteger.TWO).compareTo(denominator);
        boolean quotientIsOdd = quotient.testBit(0);

        if (comparisonToHalf > 0 || (comparisonToHalf == 0 && quotientIsOdd)) {
            return quotient.add(BigInteger.ONE);
        }
        return quotient;
    }
}
