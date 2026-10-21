package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HalfEvenRounding")
class HalfEvenRoundingTest {

    @Nested
    @DisplayName("Division and halfway cases")
    class Rounding {
        @Test
        @DisplayName("Returns zero for a zero numerator")
        void zeroNumerator_returnsZero() {
            assertEquals(BigInteger.ZERO, HalfEvenRounding.divide(BigInteger.ZERO, BigInteger.TEN));
        }

        @Test
        @DisplayName("Preserves an exact integer quotient")
        void exactDivision_returnsQuotient() {
            assertEquals(BigInteger.valueOf(12L),
                    HalfEvenRounding.divide(BigInteger.valueOf(120L), BigInteger.TEN));
        }

        @Test
        @DisplayName("Preserves the numerator when dividing by one")
        void denominatorIsOne_returnsNumerator() {
            assertEquals(BigInteger.valueOf(37_095L),
                    HalfEvenRounding.divide(BigInteger.valueOf(37_095L), BigInteger.ONE));
        }

        @Test
        @DisplayName("Rounds 11.4 down even though the lower integer is odd")
        void belowHalfWithOddQuotient_roundsDown() {
            assertEquals(BigInteger.valueOf(11L),
                    HalfEvenRounding.divide(BigInteger.valueOf(114L), BigInteger.TEN));
        }

        @Test
        @DisplayName("Rounds 10.6 up even though the lower integer is even")
        void aboveHalfWithEvenQuotient_roundsUp() {
            assertEquals(BigInteger.valueOf(11L),
                    HalfEvenRounding.divide(BigInteger.valueOf(106L), BigInteger.TEN));
        }

        @Test
        @DisplayName("Rounds 10.5 down to the even integer 10")
        void halfwayWithEvenQuotient_roundsDown() {
            assertEquals(BigInteger.TEN,
                    HalfEvenRounding.divide(BigInteger.valueOf(105L), BigInteger.TEN));
        }

        @Test
        @DisplayName("Rounds 11.5 up to the even integer 12")
        void halfwayWithOddQuotient_roundsUp() {
            assertEquals(BigInteger.valueOf(12L),
                    HalfEvenRounding.divide(BigInteger.valueOf(115L), BigInteger.TEN));
        }

        @Test
        @DisplayName("Rounds exactly one half to zero")
        void oneHalf_returnsZero() {
            assertEquals(BigInteger.ZERO, HalfEvenRounding.divide(BigInteger.ONE, BigInteger.TWO));
        }

        @Test
        @DisplayName("Rounds four thirds down without treating it as a tie")
        void oddDenominatorBelowHalf_roundsDown() {
            assertEquals(BigInteger.ONE,
                    HalfEvenRounding.divide(BigInteger.valueOf(4L), BigInteger.valueOf(3L)));
        }

        @Test
        @DisplayName("Rounds two thirds up")
        void oddDenominatorAboveHalf_roundsUp() {
            assertEquals(BigInteger.ONE,
                    HalfEvenRounding.divide(BigInteger.TWO, BigInteger.valueOf(3L)));
        }
    }

    @Nested
    @DisplayName("Precision and large numbers")
    class Precision {
        @Test
        @DisplayName("Rounds one internal unit below 11.5 down at the projection scale")
        void oneUnitBelowHalf_roundsDown() {
            BigInteger numerator = new BigInteger("11499999999999999999");
            BigInteger denominator = new BigInteger("1000000000000000000");

            assertEquals(BigInteger.valueOf(11L), HalfEvenRounding.divide(numerator, denominator));
        }

        @Test
        @DisplayName("Rounds one internal unit above 10.5 up at the projection scale")
        void oneUnitAboveHalf_roundsUp() {
            BigInteger numerator = new BigInteger("10500000000000000001");
            BigInteger denominator = new BigInteger("1000000000000000000");

            assertEquals(BigInteger.valueOf(11L), HalfEvenRounding.divide(numerator, denominator));
        }

        @Test
        @DisplayName("Rounds a halfway quotient beyond long range down to an even integer")
        void largeEvenQuotientAtHalf_roundsDownExactly() {
            BigInteger numerator = new BigInteger("200000000000000000001");

            assertEquals(new BigInteger("100000000000000000000"),
                    HalfEvenRounding.divide(numerator, BigInteger.TWO));
        }

        @Test
        @DisplayName("Rounds a halfway quotient beyond long range up to an even integer")
        void largeOddQuotientAtHalf_roundsUpExactly() {
            BigInteger numerator = new BigInteger("200000000000000000003");

            assertEquals(new BigInteger("100000000000000000002"),
                    HalfEvenRounding.divide(numerator, BigInteger.TWO));
        }
    }

    @Nested
    @DisplayName("Invalid inputs")
    class InvalidInputs {
        @Test
        @DisplayName("Rejects a missing numerator")
        void nullNumerator_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> HalfEvenRounding.divide(null, BigInteger.TEN));
        }

        @Test
        @DisplayName("Rejects a missing denominator")
        void nullDenominator_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> HalfEvenRounding.divide(BigInteger.TEN, null));
        }

        @Test
        @DisplayName("Rejects a negative numerator")
        void negativeNumerator_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> HalfEvenRounding.divide(BigInteger.ONE.negate(), BigInteger.TEN));
        }

        @Test
        @DisplayName("Rejects division by zero")
        void zeroDenominator_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> HalfEvenRounding.divide(BigInteger.TEN, BigInteger.ZERO));
        }

        @Test
        @DisplayName("Rejects a negative denominator")
        void negativeDenominator_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> HalfEvenRounding.divide(BigInteger.TEN, BigInteger.ONE.negate()));
        }
    }
}
