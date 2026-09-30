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

@DisplayName("CashFlowIndexStrategy")
class CashFlowIndexStrategyTest {

    private static Debt studentLoan(String id, long balanceCents, int aprBps, long minimumCents) {
        return new Debt(id, "Loan " + id, DebtType.STUDENT_LOAN,
                balanceCents, aprBps, minimumCents, 15, null);
    }

    @Nested
    @DisplayName("Allocation")
    class Allocation {
        @Test
        @DisplayName("Prioritizes lower balance-to-minimum ratio over APR and minimum size")
        void lowerIndexWithSmallerMinimum_receivesExtraFirst() {
            Debt higherMinimum = studentLoan("a", 100_000L, 2200, 10_000L);
            Debt lowerIndex = studentLoan("b", 30_000L, 600, 5_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(higherMinimum, lowerIndex), 10_000L);

            assertEquals(Map.of("b", 10_000L), result);
        }

        @Test
        @DisplayName("Targets lowest index even when extra cannot pay off any debt")
        void noDebtAffordable_partiallyPaysLowestIndex() {
            Debt lowerIndex = studentLoan("a", 50_000L, 600, 10_000L);
            Debt higherApr = studentLoan("b", 100_000L, 2400, 5_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(lowerIndex, higherApr), 5_000L);

            assertEquals(Map.of("a", 5_000L), result);
        }

        @Test
        @DisplayName("Moves remaining extra to the next index after a payoff")
        void firstDebtPaidOff_remainderGoesToNextIndex() {
            Debt first = studentLoan("a", 10_000L, 600, 5_000L);
            Debt second = studentLoan("b", 50_000L, 1200, 5_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(second, first), 15_000L);

            assertEquals(Map.of("a", 10_000L, "b", 5_000L), result);
        }
    }

    @Nested
    @DisplayName("Tie breaking")
    class TieBreaking {
        @Test
        @DisplayName("Uses higher APR when indexes are equal")
        void equalIndex_higherAprReceivesExtraFirst() {
            Debt lowerApr = studentLoan("a", 10_000L, 600, 1_000L);
            Debt higherApr = studentLoan("b", 20_000L, 2000, 2_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(lowerApr, higherApr), 5_000L);

            assertEquals(Map.of("b", 5_000L), result);
        }

        @Test
        @DisplayName("Uses lower balance when index and APR are equal")
        void equalIndexAndApr_lowerBalanceReceivesExtraFirst() {
            Debt larger = studentLoan("a", 20_000L, 1200, 2_000L);
            Debt smaller = studentLoan("b", 10_000L, 1200, 1_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(larger, smaller), 5_000L);

            assertEquals(Map.of("b", 5_000L), result);
        }

        @Test
        @DisplayName("Uses lower id when index, APR, and balance are equal")
        void equalIndexAprAndBalance_lowerIdReceivesExtraFirst() {
            Debt second = studentLoan("b", 10_000L, 1200, 1_000L);
            Debt first = studentLoan("a", 10_000L, 1200, 1_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(second, first), 5_000L);

            assertEquals(Map.of("a", 5_000L), result);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        @Test
        @DisplayName("Ranks a zero-minimum debt after one with a positive minimum")
        void zeroMinimum_positiveMinimumReceivesExtraFirst() {
            Debt zeroMinimum = studentLoan("a", 10_000L, 2400, 0L);
            Debt positiveMinimum = studentLoan("b", 20_000L, 600, 1_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(zeroMinimum, positiveMinimum), 5_000L);

            assertEquals(Map.of("b", 5_000L), result);
        }

        @Test
        @DisplayName("Uses APR when all remaining debts have zero minimums")
        void allMinimumsZero_higherAprReceivesExtraFirst() {
            Debt lowerApr = studentLoan("a", 10_000L, 600, 0L);
            Debt higherApr = studentLoan("b", 20_000L, 2200, 0L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(lowerApr, higherApr), 5_000L);

            assertEquals(Map.of("b", 5_000L), result);
        }

        @Test
        @DisplayName("Skips debts with zero balance")
        void zeroBalanceDebt_noAllocationToIt() {
            Debt paidOff = studentLoan("a", 0L, 2200, 1_000L);
            Debt active = studentLoan("b", 10_000L, 600, 1_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(paidOff, active), 5_000L);

            assertEquals(Map.of("b", 5_000L), result);
        }

        @Test
        @DisplayName("Returns no allocation for empty debts")
        void emptyDebts_returnsEmptyMap() {
            assertEquals(Map.of(), new CashFlowIndexStrategy().allocateExtra(List.of(), 5_000L));
        }

        @Test
        @DisplayName("Returns no allocation for zero extra")
        void zeroExtra_returnsEmptyMap() {
            Debt debt = studentLoan("a", 10_000L, 600, 1_000L);

            assertEquals(Map.of(), new CashFlowIndexStrategy().allocateExtra(List.of(debt), 0L));
        }

        @Test
        @DisplayName("Rejects negative extra")
        void negativeExtra_throwsIllegalArgumentException() {
            Debt debt = studentLoan("a", 10_000L, 600, 1_000L);

            assertThrows(IllegalArgumentException.class,
                    () -> new CashFlowIndexStrategy().allocateExtra(List.of(debt), -1L));
        }

        @Test
        @DisplayName("Caps allocations at total outstanding balances")
        void extraExceedsTotalBalance_allocatesOnlyBalances() {
            Debt first = studentLoan("a", 10_000L, 600, 5_000L);
            Debt second = studentLoan("b", 20_000L, 1200, 5_000L);

            Map<String, Long> result = new CashFlowIndexStrategy()
                    .allocateExtra(List.of(first, second), 40_000L);

            assertEquals(Map.of("a", 10_000L, "b", 20_000L), result);
        }
    }
}
