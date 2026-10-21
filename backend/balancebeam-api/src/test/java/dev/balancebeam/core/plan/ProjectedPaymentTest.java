package dev.balancebeam.core.plan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectedPayment")
class ProjectedPaymentTest {

    @Test
    @DisplayName("Accepts an extra payment without a payoff adjustment")
    void zeroAdjustment_constructsSuccessfully() {
        PaymentAction action = new PaymentAction("loan-1", 30_000L, LocalDate.of(2026, 10, 6));

        assertDoesNotThrow(() -> new ProjectedPayment(action, PaymentKind.EXTRA, 0L));
    }

    @Test
    @DisplayName("Accepts a minimum payment containing a payoff adjustment")
    void adjustmentWithinPayment_constructsSuccessfully() {
        PaymentAction action = new PaymentAction("loan-1", 37_095L, LocalDate.of(2026, 10, 13));

        assertDoesNotThrow(() -> new ProjectedPayment(action, PaymentKind.MINIMUM, 70L));
    }

    @Test
    @DisplayName("Accepts an adjustment equal to the actual payment amount")
    void adjustmentEqualsPayment_constructsSuccessfully() {
        PaymentAction action = new PaymentAction("loan-1", 70L, LocalDate.of(2026, 10, 13));

        assertDoesNotThrow(() -> new ProjectedPayment(action, PaymentKind.MINIMUM, 70L));
    }

    @Test
    @DisplayName("Accepts a one-cent payment")
    void oneCentPayment_constructsSuccessfully() {
        PaymentAction action = new PaymentAction("loan-1", 1L, LocalDate.of(2026, 10, 6));

        assertDoesNotThrow(() -> new ProjectedPayment(action, PaymentKind.EXTRA, 0L));
    }

    @Test
    @DisplayName("Rejects a missing payment action")
    void nullAction_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new ProjectedPayment(null, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a missing payment kind")
    void nullKind_throwsNullPointerException() {
        PaymentAction action = new PaymentAction("loan-1", 15_000L, LocalDate.of(2026, 10, 13));

        assertThrows(NullPointerException.class, () -> new ProjectedPayment(action, null, 0L));
    }

    @Test
    @DisplayName("Rejects a missing debt ID in the payment action")
    void nullDebtId_throwsNullPointerException() {
        PaymentAction action = new PaymentAction(null, 15_000L, LocalDate.of(2026, 10, 13));

        assertThrows(NullPointerException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects an empty debt ID in the payment action")
    void emptyDebtId_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("", 15_000L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a whitespace-only debt ID in the payment action")
    void whitespaceDebtId_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("   ", 15_000L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a missing payment date")
    void nullScheduledDate_throwsNullPointerException() {
        PaymentAction action = new PaymentAction("loan-1", 15_000L, null);

        assertThrows(NullPointerException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a zero payment amount")
    void zeroPaymentAmount_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("loan-1", 0L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a negative payment amount")
    void negativePaymentAmount_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("loan-1", -1L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 0L));
    }

    @Test
    @DisplayName("Rejects a negative payoff adjustment")
    void negativeAdjustment_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("loan-1", 37_095L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, -1L));
    }

    @Test
    @DisplayName("Rejects an adjustment one cent above the actual payment amount")
    void adjustmentExceedsPayment_throwsIllegalArgumentException() {
        PaymentAction action = new PaymentAction("loan-1", 37_095L, LocalDate.of(2026, 10, 13));

        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedPayment(action, PaymentKind.MINIMUM, 37_096L));
    }
}
