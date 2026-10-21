package dev.balancebeam.core.plan;

import java.util.Objects;

/**
 * An actual dated payment in a payoff projection, including any adjustment
 * added toward settling a planned payoff. The adjustment is already included
 * in the action's payment amount and must not be added again when totaling payments.
 *
 * @param action                debt ID, positive actual payment amount, and payment date
 * @param kind                  purpose of the originally scheduled payment
 * @param payoffAdjustmentCents portion of the actual amount added toward the planned
 *                              payoff, from zero through the actual payment amount
 */
public record ProjectedPayment(
        PaymentAction action,
        PaymentKind kind,
        long payoffAdjustmentCents) {
    public ProjectedPayment {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(action.debtId(), "action debtId cannot be null");
        Objects.requireNonNull(action.scheduledDate(), "action scheduledDate cannot be null");

        if (action.debtId().isBlank()) {
            throw new IllegalArgumentException("action debtId cannot be blank");
        }
        if (action.paymentAmountCents() <= 0) {
            throw new IllegalArgumentException("action paymentAmountCents must be positive");
        }
        if (payoffAdjustmentCents < 0) {
            throw new IllegalArgumentException("payoffAdjustmentCents cannot be negative");
        }
        if (payoffAdjustmentCents > action.paymentAmountCents()) {
            throw new IllegalArgumentException("payoffAdjustmentCents cannot exceed the actual payment amount");
        }
    }
}
