package dev.balancebeam.core.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectionDebt")
class ProjectionDebtTest {

    @Test
    @DisplayName("Accepts opening interest included in a student loan's total balance")
    void studentLoanWithOpeningInterest_constructsSuccessfully() {
        Debt debt = studentLoan(1_000_000L);

        assertDoesNotThrow(() -> new ProjectionDebt(debt, 20_000L));
    }

    @Test
    @DisplayName("Preserves omitted opening interest as unknown")
    void openingInterestOmitted_remainsNull() {
        ProjectionDebt input = new ProjectionDebt(studentLoan(1_000_000L));

        assertNull(input.openingUnpaidInterestCents());
    }

    @Test
    @DisplayName("Preserves explicitly supplied zero interest")
    void openingInterestExplicitlyZero_remainsSupplied() {
        ProjectionDebt input = new ProjectionDebt(studentLoan(1_000_000L), 0L);

        assertEquals(Long.valueOf(0L), input.openingUnpaidInterestCents());
    }

    @Test
    @DisplayName("Accepts a student loan balance consisting entirely of unpaid interest")
    void openingInterestEqualsBalance_constructsSuccessfully() {
        Debt debt = studentLoan(20_000L);

        assertDoesNotThrow(() -> new ProjectionDebt(debt, 20_000L));
    }

    @Test
    @DisplayName("Accepts a paid-off student loan with zero unpaid interest")
    void zeroBalanceAndZeroInterest_constructsSuccessfully() {
        Debt debt = studentLoan(0L);

        assertDoesNotThrow(() -> new ProjectionDebt(debt, 0L));
    }

    @Test
    @DisplayName("Rejects a missing debt")
    void nullDebt_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ProjectionDebt(null));
    }

    @Test
    @DisplayName("Rejects negative opening interest")
    void negativeOpeningInterest_throwsIllegalArgumentException() {
        Debt debt = studentLoan(1_000_000L);

        assertThrows(IllegalArgumentException.class, () -> new ProjectionDebt(debt, -1L));
    }

    @Test
    @DisplayName("Rejects opening interest above the total debt balance")
    void openingInterestExceedsBalance_throwsIllegalArgumentException() {
        Debt debt = studentLoan(1_000_000L);

        assertThrows(IllegalArgumentException.class, () -> new ProjectionDebt(debt, 1_000_001L));
    }

    @Test
    @DisplayName("Accepts a credit card without an opening-interest breakdown")
    void creditCardWithoutOpeningInterest_constructsSuccessfully() {
        assertDoesNotThrow(() -> new ProjectionDebt(creditCard()));
    }

    @Test
    @DisplayName("Rejects a supplied opening-interest amount for a credit card")
    void creditCardWithOpeningInterest_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionDebt(creditCard(), 1_000L));
    }

    @Test
    @DisplayName("Rejects even an explicit zero interest breakdown for a credit card")
    void creditCardWithExplicitZeroInterest_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionDebt(creditCard(), 0L));
    }

    private static Debt studentLoan(long balanceCents) {
        return new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                balanceCents, 600, 15_000L, 15, null);
    }

    private static Debt creditCard() {
        return new Debt("card-1", "Credit card", DebtType.CREDIT_CARD,
                150_000L, 2400, 5_000L, 15, 500_000L);
    }
}
