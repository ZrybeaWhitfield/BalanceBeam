package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    class AvalancheFallback {
        @Test
        @DisplayName("When no debt can be fully paid off by extra, fallback to avalanche strategy")
        void twoDebts_noBalanceCoveredByExtraCents_fallbackToAvalanche() {
            Debt debt1 = creditCard("d1", 10_000L, 900, 1_200L);
            Debt debt2 = creditCard("d2", 20_000L, 2100, 1_800L);
            long extraCents = 5_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d2", 5_000L), result);
        }

        @Test
        @DisplayName("After paying off highest-minimum debt, remainder falls back to avalanche ordering")
        void threeDebts_extraPaysOffFirstDebt_fallbackToAvalanche() {
            Debt debt1 = creditCard("d1", 10_000L, 1100, 1_200L);
            Debt debt2 = creditCard("d2", 20_000L, 650, 1_800L);
            Debt debt3 = studentLoan("d3", 40_000L, 950, 900L);
            long extraCents = 25_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2, debt3), extraCents);

            assertEquals(Map.of("d2", 20_000L, "d1", 5_000L), result);
        }
    }

    @Nested
    @DisplayName("Tie-Breaking")
    class TieBreaking {
        @Test
        @DisplayName("When two payable debts have the same minimum payment, allocate to lower balance")
        void twoDebtsSameMinPayment_allocateExtraToLowerBalance() {
            Debt debt1 = creditCard("d1", 15_000L, 650, 1_800L);
            Debt debt2 = studentLoan("d2", 12_000L, 950, 1_800L);
            long extraCents = 15_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d2", 12_000L, "d1", 3_000L), result);
        }

        @Test
        @DisplayName("When two payable debts have the same balance and minimum payment, allocate extra to the higher APR")
        void twoDebts_sameMinPaymentAndBalance_allocateToHigherApr() {
            Debt debt1 = creditCard("d1", 25_000L, 650, 1_800L);
            Debt debt2 = studentLoan("d2", 25_000L, 950, 1_800L);
            long extraCents = 25_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d2", 25_000L), result);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        @Test
        @DisplayName("Empty debts return empty map")
        void emptyDebtList_resultIsEmptyMap() {
            long extraCents = 5_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("When no debts have a balance, return an empty map")
        void twoDebts_zeroBalance_returnEmptyMap() {
            Debt debt1 = studentLoan("d1", 0, 1200, 0);
            Debt debt2 = creditCard("d2", 0, 1200, 0);
            long extraCents = 30_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("When there are no extra cents, return an empty map")
        void singleDebt_noExtraCents_returnEmptyMap() {
            Debt debt1 = creditCard("d1", 10_000L, 1200, 0);
            long extraCents = 0;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("When extraCents is less than zero, throw exception")
        void negativeExtraCents_throwsException() {
            Debt debt1 = creditCard("d1", 10_000L, 1200, 0);
            long extraCents = -1;

            MinReductionStrategy strategy = new MinReductionStrategy();

            assertThrows(IllegalArgumentException.class, () -> strategy.allocateExtra(List.of(debt1), extraCents));
        }

        @Test
        @DisplayName("Filter out zero-balance debts")
        void multipleDebts_zeroAndNonZeroBalances_zeroBalancesExcludedFromResult() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 0, 1800, 0);
            Debt debt3 = studentLoan("d3", 36_000L, 950, 0);
            Debt debt4 = studentLoan("d4", 0, 1000, 0);
            long extraCents = 40_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2, debt3, debt4), extraCents);

            assertEquals(Map.of("d1", 20_000L, "d3", 20_000L), result);
        }

        @Test
        @DisplayName("When extra exceeds total balance, allocation matches balance exactly")
        void extraCentsMoreThanBalance_allocationEqualsBalance() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            long extraCents = 30_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1), extraCents);

            assertEquals(Map.of("d1", 20_000L), result);
        }

        @Test
        @DisplayName("Debt with zero min payment is deprioritized in payoff phase")
        void minimumPaymentZero_deprioritizedInMinReduction() {
            Debt debt1 = creditCard("d1", 10_000L, 1200, 0);
            Debt debt2 = studentLoan("d2", 10_000L, 0, 1_000L);
            long extraCents = 10_000L;

            MinReductionStrategy strategy = new MinReductionStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d2", 10_000L), result);
        }
    }
}
