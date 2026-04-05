package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;

@DisplayName("MinReductionStrategy")
class MinReductionStrategyTest {
    private static Debt studentLoan(String id, long balanceCents, int aprBps, long minPayCents) {
        return new Debt(id, "Loan " + id, DebtType.STUDENT_LOAN, balanceCents, aprBps, minPayCents, 15, null);
    }

    private static Debt creditCard(String id, long balanceCents, int aprBps, long minPayCents) {
        return new Debt(id, "Card " + id, DebtType.CREDIT_CARD, balanceCents, aprBps, minPayCents, 15, 500_000L);
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {
        @Test
        @DisplayName("Allocates the extra cents to the single debt balance when extra is more than balance")
        void singleDebt_extraCentsAllocatedToFullBalance() {
            Debt debt = creditCard("d1", 8_000L, 0, 0);
            long extraCents = 10_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt), extraCents);

            assertEquals(Map.of("d1", 8_000L), result);
        }

        @Test
        @DisplayName("Allocates extra to debt with highest min payment first")
        void twoDebtsBothPayable_extraIsAllocatedToHighestMinPaymentFirst() {
            Debt debt1 = creditCard("d1", 10_000L, 0, 1_200L);
            Debt debt2 = creditCard("d2", 20_000L, 0 , 1_800L);
            long extraCents = 25_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d1", 5_000L, "d2", 20_000L ), result);
        }

        @Test
        @DisplayName("Fallback to avalanche strategy when extra doesn't cover all balances")
        void threeDebts_extraCoversTwoWithMinReduction_allocateRemainderWithAvalancheFallback() {
            Debt debt1 = creditCard("d1", 10_000L, 0, 1_200L);
            Debt debt2 = creditCard("d2", 20_000L, 0, 1_800L);
            Debt debt3 = studentLoan("d3", 40_000L, 950, 900L);
            long extraCents = 35_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2, debt3), extraCents);

            assertEquals(Map.of("d1", 10_000L, "d2", 20_000L, "d3", 5_000L), result);
        }

    }

    @Nested
    @DisplayName("Avalanche fallback")
    class AvalancheFallback {}

    @Nested
    @DisplayName("Tie-Breaking")
    class TieBreaking {}

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
    }
}
