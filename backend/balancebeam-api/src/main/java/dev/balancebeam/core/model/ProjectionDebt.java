package dev.balancebeam.core.model;

import java.util.Objects;

/**
 * Debt terms and optional opening interest for a payoff projection.
 *
 * @param debt                       debt whose balance includes any supplied
 *                                   opening unpaid interest
 * @param openingUnpaidInterestCents  unpaid interest already included in a student
 *                                   loan's balance; null means it was not supplied.
 *                                   Must be null for credit cards.
 */
public record ProjectionDebt(Debt debt, Long openingUnpaidInterestCents) {
    public ProjectionDebt {
        Objects.requireNonNull(debt, "debt cannot be null");

        if (openingUnpaidInterestCents != null) {
            if (debt.type() != DebtType.STUDENT_LOAN) {
                throw new IllegalArgumentException("openingUnpaidInterestCents applies only to student loans");
            }
            if (openingUnpaidInterestCents < 0) {
                throw new IllegalArgumentException("openingUnpaidInterestCents cannot be negative");
            }
            if (openingUnpaidInterestCents > debt.balanceCents()) {
                throw new IllegalArgumentException("openingUnpaidInterestCents cannot exceed the debt balance");
            }
        }
    }

    /**
     * Creates an input without an opening-interest breakdown. The projector can
     * distinguish this omission from an explicitly supplied zero.
     */
    public ProjectionDebt(Debt debt) {
        this(debt, null);
    }
}
