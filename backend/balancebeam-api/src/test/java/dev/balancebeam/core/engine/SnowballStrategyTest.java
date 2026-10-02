package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;

@DisplayName("SnowballStrategy")
class SnowballStrategyTest {

    @Test
    @DisplayName("Targets the smaller balance even when another debt has higher APR")
    void smallerBalanceAndLowerApr_receivesExtraFirst() {
        Debt larger = studentLoan("larger", 80_000L, 2200);
        Debt smaller = studentLoan("smaller", 20_000L, 600);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(larger, smaller), 5_000L);

        assertEquals(Map.of("smaller", 5_000L), result);
    }

    @Test
    @DisplayName("Moves remaining extra to the next balance after paying off the smallest debt")
    void smallestDebtPaidOff_remainderGoesToNextDebt() {
        Debt larger = studentLoan("larger", 80_000L, 2200);
        Debt smaller = studentLoan("smaller", 20_000L, 600);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(larger, smaller), 30_000L);

        assertEquals(Map.of("smaller", 20_000L, "larger", 10_000L), result);
    }

    @Test
    @DisplayName("Targets higher APR when balances are equal")
    void equalBalances_higherAprReceivesExtraFirst() {
        Debt lowerApr = studentLoan("a", 20_000L, 600);
        Debt higherApr = studentLoan("b", 20_000L, 2200);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(lowerApr, higherApr), 5_000L);

        assertEquals(Map.of("b", 5_000L), result);
    }

    @Test
    @DisplayName("Targets lexicographically lower ID when balances and APR are equal")
    void equalBalancesAndApr_lowerIdReceivesExtraFirst() {
        Debt higherId = studentLoan("z", 20_000L, 1200);
        Debt lowerId = studentLoan("a", 20_000L, 1200);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(higherId, lowerId), 5_000L);

        assertEquals(Map.of("a", 5_000L), result);
    }

    @Test
    @DisplayName("Returns no allocation when there are no debts")
    void emptyDebts_returnsEmptyMap() {
        assertEquals(Map.of(), new SnowballStrategy().allocateExtra(List.of(), 5_000L));
    }

    @Test
    @DisplayName("Returns no allocation when every debt is paid off")
    void allDebtsPaidOff_returnsEmptyMap() {
        Debt first = studentLoan("a", 0L, 600);
        Debt second = studentLoan("b", 0L, 2200);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(first, second), 5_000L);

        assertEquals(Map.of(), result);
    }

    @Test
    @DisplayName("Skips a paid-off debt when another debt has a balance")
    void paidOffAndActiveDebts_allocatesOnlyToActiveDebt() {
        Debt paidOff = studentLoan("a", 0L, 2200);
        Debt active = studentLoan("b", 20_000L, 600);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(paidOff, active), 5_000L);

        assertEquals(Map.of("b", 5_000L), result);
    }

    @Test
    @DisplayName("Returns no allocation when there is no extra cash")
    void zeroExtra_returnsEmptyMap() {
        Debt debt = studentLoan("a", 20_000L, 1200);

        assertEquals(Map.of(), new SnowballStrategy().allocateExtra(List.of(debt), 0L));
    }

    @Test
    @DisplayName("Rejects a negative extra amount")
    void negativeExtra_throwsIllegalArgumentException() {
        Debt debt = studentLoan("a", 20_000L, 1200);

        assertThrows(IllegalArgumentException.class,
                () -> new SnowballStrategy().allocateExtra(List.of(debt), -1L));
    }

    @Test
    @DisplayName("Caps each allocation when extra exceeds all outstanding balances")
    void extraExceedsTotalBalance_allocatesOnlyOutstandingBalances() {
        Debt smaller = studentLoan("a", 20_000L, 600);
        Debt larger = studentLoan("b", 80_000L, 2200);

        Map<String, Long> result = new SnowballStrategy()
                .allocateExtra(List.of(larger, smaller), 120_000L);

        assertEquals(Map.of("a", 20_000L, "b", 80_000L), result);
    }

    private static Debt studentLoan(String id, long balanceCents, int aprBps) {
        return new Debt(id, "Loan " + id, DebtType.STUDENT_LOAN,
                balanceCents, aprBps, 5_000L, 15, null);
    }
}
