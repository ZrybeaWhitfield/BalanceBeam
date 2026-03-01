package dev.balancebeam.core.plan;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@DisplayName("PaymentAction")
class PaymentActionTest {

    @Test
    @DisplayName("constructs with all fields accessible")
    void paymentAction_constructsAndAccessorsReturnExpectedValues() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        PaymentAction action = new PaymentAction("debt-1", 50000L, date);

        Assertions.assertEquals("debt-1", action.debtId());
        Assertions.assertEquals(50000L, action.paymentAmountCents());
        Assertions.assertEquals(date, action.scheduledDate());
    }

    @Test
    @DisplayName("equal PaymentAction records with the same values are equal")
    void equalPaymentActions_areEqual() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        PaymentAction a = new PaymentAction("debt-1", 50000L, date);
        PaymentAction b = new PaymentAction("debt-1", 50000L, date);

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("PaymentActions with different amounts are not equal")
    void differentPaymentAmounts_areNotEqual() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        PaymentAction a = new PaymentAction("debt-1", 50000L, date);
        PaymentAction b = new PaymentAction("debt-1", 25000L, date);

        Assertions.assertNotEquals(a, b);
    }
}
