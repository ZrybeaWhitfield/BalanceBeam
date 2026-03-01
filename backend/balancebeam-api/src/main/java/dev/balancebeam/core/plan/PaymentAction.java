package dev.balancebeam.core.plan;

import java.time.LocalDate;

public record PaymentAction(
        String debtId,
        long paymentAmountCents,
        LocalDate scheduledDate) {

}