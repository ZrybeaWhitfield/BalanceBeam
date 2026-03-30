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

@DisplayName("AvalancheStrategy")
class AvalancheStrategyTest {

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
        @DisplayName("Allocates all extra to single debt when extra is less than balance")
        void singleDebt_extraCentsLessThanBalance_allocateExtraToThatDebt() {
            Debt debt = creditCard("d1", 20_000L, 0, 0);
            long extraCents = 4_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt), extraCents);

            assertEquals(Map.of("d1", 4_000L), result);

        }

        @Test
        @DisplayName("Allocates extra to higher APR debt first")
        void twoDebtsDifferentApr_extraCentsIsLessThanBalance_allocateExtraToHigherApr() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 80_000L, 1800, 0);
            long extraCents = 4_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1,debt2), extraCents);

            assertEquals(Map.of("d2", 4_000L), result);

        }

        @Test
        @DisplayName("Extra covers first debt completely and remainder is allocated to second debt")
        void twoDebts_extraCentsCoversFirstDebt_remainingIsAllocatedToSecondDebt() {
            Debt debt1 = creditCard("d1",5_000L, 1200, 0);
            Debt debt2 = studentLoan("d2", 18_000L, 1000, 0);
            long extraCents = 10_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d1", 5_000L, "d2", 5_000L), result);
        }

        @Test
        @DisplayName("Allocates the balance exactly for each debt")
        void multipleDebts_extraCentsExceedsAllBalance_allocateExtraToEachBalance() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 80_000L, 1800, 0);
            Debt debt3 = studentLoan("d3",36_000L, 950, 0);
            long extraCents = 140_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2, debt3), extraCents);

            assertEquals(Map.of("d2", 80_000L, "d1", 20_000L, "d3", 36_000L), result);
        }
    }

    @Nested
    @DisplayName("Tie-breaking")
    class TieBreaking {
        @Test
        @DisplayName("Allocate extra to lower balance when APR is the same")
        void twoDebtsSameApr_extraCoversOneBalance_lowerBalanceTargetedFirst() {
            Debt debt1 = creditCard("d1", 80_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 20_000L, 1200, 0);
            long extraCents = 30_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1,debt2), extraCents);

            assertEquals(Map.of("d1",10_000L, "d2", 20_000L), result);
        }

        @Test
        @DisplayName("When debts are equal, lexicographically lower debt is first")
        void twoDebtsSameAprSameBalance_extraCoversOneBalance_lowerIdIsTargeted() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 20_000L, 1200, 0);
            long extraCents = 30_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of("d1", 20_000L, "d2", 10_000L), result);
        }

    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        @Test
        @DisplayName("Empty Debts result in empty map")
        void emptyDebtList_resultIsEmptyMap() {
            long extraCents = 5_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("When all debts have a zero balance return an empty map")
        void twoDebts_zeroBalance_returnEmptyMap() {
            Debt debt1 = creditCard("d1", 0, 1200, 0);
            Debt debt2 = creditCard("d2", 0, 1200, 0);
            long extraCents = 30_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("Return an empty map when there are no extra cents")
        void singleDebt_noExtraCents_returnEmptyMap() {
            Debt debt1 = creditCard("d1", 10_000L, 1200, 0);
            long extraCents = 0;

            AvalancheStrategy strategy = new AvalancheStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1), extraCents);

            assertEquals(Map.of(), result);
        }

        @Test
        @DisplayName("Throws exception when extraCents is less than zero")
        void singleDebt_extraCentsLessThanZero_throwsException() {
            Debt debt1 = creditCard("d1", 10_000L, 1200, 0);
            long extraCents = -1;

            AvalancheStrategy strategy = new AvalancheStrategy();

            assertThrows(IllegalArgumentException.class, () -> strategy.allocateExtra(List.of(debt1), extraCents));
        }

        @Test
        @DisplayName("Filter out debts with zero balances")
        void multipleDebts_zeroAndNonZeroBalances_zeroBalancesExcludedFromResult() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 0, 1800, 0);
            Debt debt3 = studentLoan("d3", 36_000L, 950, 0);
            Debt debt4 = studentLoan("d4", 0, 1000, 0);
            long extraCents = 40_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1, debt2, debt3, debt4), extraCents);

            assertEquals(Map.of("d1", 20_000L, "d3", 20_000L), result);
        }

        @Test
        @DisplayName("When extraCents exceeds the balance of a debt allocation matches balance exactly")
        void singleDebt_extraCentsMoreThanBalance_allocationEqualsBalance() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            long extraCents = 30_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt1), extraCents);

            assertEquals(Map.of("d1", 20_000L), result);
        }
    }
}
