package dev.balancebeam.core.plan;

/**
 * Modeled principal and unpaid interest at a point in a payoff projection.
 * For credit cards, principal includes the entire opening balance; unpaid
 * interest tracks remaining interest charged during the projection.
 *
 * @param principalCents      nonnegative remaining principal in cents
 * @param unpaidInterestCents nonnegative unpaid interest in cents
 */
public record DebtBalance(long principalCents, long unpaidInterestCents) {
    public DebtBalance {
        if (principalCents < 0) {
            throw new IllegalArgumentException("principalCents cannot be negative");
        }
        if (unpaidInterestCents < 0) {
            throw new IllegalArgumentException("unpaidInterestCents cannot be negative");
        }
        if (principalCents > Long.MAX_VALUE - unpaidInterestCents) {
            throw new IllegalArgumentException("total balance cannot exceed Long.MAX_VALUE cents");
        }
    }

    /** Returns the total outstanding balance in cents. */
    public long totalBalanceCents() {
        return principalCents + unpaidInterestCents;
    }
}
