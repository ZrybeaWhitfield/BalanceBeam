package dev.balancebeam.core.engine;

import java.util.List;

import dev.balancebeam.core.plan.PaymentAction;

/**
 * Result of a single paycheck allocation.
 *
 * @param minimumPaymentActions minimum payments scheduled for debts due in the
 *                              pay window, ordered by due date
 * @param extraPaymentActions   extra payments allocated by the chosen strategy,
 *                              scheduled on pay date
 * @param availableCents        paycheckNet - essentials - buffer (can be
 *                              negative)
 * @param reservedBufferCents   buffer amount held back from the paycheck
 * @param totalMinimumsDueCents sum of minimum payments for debts due in the
 *                              window
 * @param shortfallCents        positive if available couldn't cover all
 *                              minimums, zero otherwise
 */
public record AllocationResult(
    List<PaymentAction> minimumPaymentActions,
    List<PaymentAction> extraPaymentActions,
    long availableCents,
    long reservedBufferCents,
    long totalMinimumsDueCents,
    long shortfallCents
) {
    public AllocationResult {
        minimumPaymentActions = List.copyOf(minimumPaymentActions);
        extraPaymentActions = List.copyOf(extraPaymentActions);
    }
}
