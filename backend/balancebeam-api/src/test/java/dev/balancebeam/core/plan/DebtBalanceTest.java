package dev.balancebeam.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DebtBalance")
class DebtBalanceTest {

    @Test
    @DisplayName("Includes principal and unpaid interest in the total balance")
    void principalAndUnpaidInterest_totalIncludesBoth() {
        DebtBalance balance = new DebtBalance(980_000L, 20_000L);

        assertEquals(1_000_000L, balance.totalBalanceCents());
    }

    @Test
    @DisplayName("Returns principal as the total when no interest is unpaid")
    void zeroUnpaidInterest_totalEqualsPrincipal() {
        DebtBalance balance = new DebtBalance(980_000L, 0L);

        assertEquals(980_000L, balance.totalBalanceCents());
    }

    @Test
    @DisplayName("Includes unpaid interest even when principal is zero")
    void zeroPrincipal_totalEqualsUnpaidInterest() {
        DebtBalance balance = new DebtBalance(0L, 20_000L);

        assertEquals(20_000L, balance.totalBalanceCents());
    }

    @Test
    @DisplayName("Returns zero for a fully paid-off debt")
    void zeroPrincipalAndInterest_totalIsZero() {
        DebtBalance balance = new DebtBalance(0L, 0L);

        assertEquals(0L, balance.totalBalanceCents());
    }

    @Test
    @DisplayName("Rejects negative principal")
    void negativePrincipal_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DebtBalance(-1L, 20_000L));
    }

    @Test
    @DisplayName("Rejects negative unpaid interest")
    void negativeUnpaidInterest_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DebtBalance(980_000L, -1L));
    }

    @Test
    @DisplayName("Accepts a total exactly at the largest long value")
    void totalEqualsLongMaximum_returnsExactTotal() {
        DebtBalance balance = new DebtBalance(Long.MAX_VALUE - 20_000L, 20_000L);

        assertEquals(Long.MAX_VALUE, balance.totalBalanceCents());
    }

    @Test
    @DisplayName("Rejects a total one cent beyond the largest long value")
    void totalExceedsLongMaximum_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DebtBalance(Long.MAX_VALUE, 1L));
    }
}
