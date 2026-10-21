package dev.balancebeam.core.engine;

import java.math.BigInteger;
import java.util.Objects;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.plan.DebtBalance;

/** Mutable interest accounting for one debt during a projection. */
final class DebtInterestState {
    private static final BigInteger UNITS_PER_CENT = BigInteger.TEN.pow(18);
    private static final BigInteger STUDENT_LOAN_DAILY_RATE_DENOMINATOR = BigInteger.valueOf(3_652_500L);
    private static final BigInteger CREDIT_CARD_DAILY_RATE_DENOMINATOR = BigInteger.valueOf(3_650_000L);

    private final DebtType debtType;
    private final BigInteger aprBasisPoints;
    private long principalCents;
    private long unpaidInterestCents;
    private BigInteger accruedInterestUnits = BigInteger.ZERO;
    private long chargedInterestCents;

    DebtInterestState(ProjectionDebt input) {
        Objects.requireNonNull(input, "input cannot be null");
        Debt debt = input.debt();
        debtType = debt.type();
        aprBasisPoints = BigInteger.valueOf(debt.aprBasisPoints());
        unpaidInterestCents = input.openingUnpaidInterestCents() == null ? 0L : input.openingUnpaidInterestCents();
        principalCents = debt.balanceCents() - unpaidInterestCents;
    }

    void accrueDay() {
        if ((principalCents == 0 && unpaidInterestCents == 0) || aprBasisPoints.signum() == 0) {
            return;
        }

        BigInteger principalUnits = BigInteger.valueOf(principalCents).multiply(UNITS_PER_CENT);
        BigInteger dailyInterestUnits = switch (debtType) {
            case STUDENT_LOAN -> dailyInterest(principalUnits, STUDENT_LOAN_DAILY_RATE_DENOMINATOR);
            case CREDIT_CARD -> {
                BigInteger postedBalanceUnits = principalUnits
                        .add(BigInteger.valueOf(unpaidInterestCents).multiply(UNITS_PER_CENT));
                BigInteger fractionalCarry = accruedInterestUnits
                        .subtract(BigInteger.valueOf(chargedInterestCents).multiply(UNITS_PER_CENT));
                yield dailyInterest(postedBalanceUnits.add(fractionalCarry), CREDIT_CARD_DAILY_RATE_DENOMINATOR);
            }
        };
        accruedInterestUnits = accruedInterestUnits.add(dailyInterestUnits);
    }

    /** Posts cumulative rounded interest and returns the newly charged cents. */
    long postInterest() {
        long roundedTotal = HalfEvenRounding.divide(accruedInterestUnits, UNITS_PER_CENT).longValueExact();
        long newlyCharged = Math.subtractExact(roundedTotal, chargedInterestCents);
        long nextUnpaidInterest = Math.addExact(unpaidInterestCents, newlyCharged);
        if (nextUnpaidInterest > Long.MAX_VALUE - principalCents) {
            throw new ArithmeticException("total debt balance exceeds long range");
        }

        unpaidInterestCents = nextUnpaidInterest;
        chargedInterestCents = roundedTotal;
        return newlyCharged;
    }

    /** Posts interest, applies up to the amount owed, and returns the actual payment. */
    long applyPayment(long requestedCents) {
        if (requestedCents <= 0) {
            throw new IllegalArgumentException("requestedCents must be positive");
        }
        postInterest();

        long actualPayment = Math.min(requestedCents, Math.addExact(principalCents, unpaidInterestCents));
        long interestPayment = Math.min(actualPayment, unpaidInterestCents);
        unpaidInterestCents -= interestPayment;
        principalCents -= actualPayment - interestPayment;

        if (principalCents == 0 && unpaidInterestCents == 0) {
            accruedInterestUnits = BigInteger.valueOf(chargedInterestCents).multiply(UNITS_PER_CENT);
        }
        return actualPayment;
    }

    /** Returns posted cents; call postInterest first to include newly accrued interest. */
    DebtBalance balance() {
        return new DebtBalance(principalCents, unpaidInterestCents);
    }

    long totalInterestChargedCents() {
        return chargedInterestCents;
    }

    private BigInteger dailyInterest(BigInteger balanceUnits, BigInteger denominator) {
        return HalfEvenRounding.divide(balanceUnits.multiply(aprBasisPoints), denominator);
    }
}
