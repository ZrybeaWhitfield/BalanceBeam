package dev.balancebeam.core.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Payments, interest charges, and ending balances for one 14-day paycheck window.
 * Payments are ordered by date, debt ID, and kind (minimum before extra).
 * Both debt maps contain the same IDs, including paid-off debts and zero charges,
 * and iterate in debt ID order. All collections are immutable copies.
 *
 * @param startDate            inclusive window start
 * @param endDate              exclusive window end, 14 days after the start
 * @param payments             actual payments, including any payoff adjustments
 * @param interestChargedCents new interest charged per debt during this window,
 *                             whether paid or still outstanding
 * @param endingBalances       balances per debt at the exclusive window end
 * @param availableCents       cash after essentials and buffer; may be negative,
 *                             in which case no payments are allowed
 * @param bufferCents          nonnegative protected buffer, already excluded from
 *                             available cash
 * @param totalMinimumsDueCents nonnegative total minimums due for this window
 * @param minimumShortfallCents unmet minimums, from zero through total minimums due
 */
public record ProjectionWindow(
        LocalDate startDate,
        LocalDate endDate,
        List<ProjectedPayment> payments,
        Map<String, Long> interestChargedCents,
        Map<String, DebtBalance> endingBalances,
        long availableCents,
        long bufferCents,
        long totalMinimumsDueCents,
        long minimumShortfallCents) {
    public ProjectionWindow {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        Objects.requireNonNull(payments, "payments cannot be null");

        if (ChronoUnit.DAYS.between(startDate, endDate) != 14) {
            throw new IllegalArgumentException("window must span exactly 14 days");
        }
        if (bufferCents < 0) {
            throw new IllegalArgumentException("bufferCents cannot be negative");
        }
        if (totalMinimumsDueCents < 0) {
            throw new IllegalArgumentException("totalMinimumsDueCents cannot be negative");
        }
        if (minimumShortfallCents < 0 || minimumShortfallCents > totalMinimumsDueCents) {
            throw new IllegalArgumentException("minimumShortfallCents must be between zero and total minimums due");
        }

        interestChargedCents = immutableDebtMap(interestChargedCents, "interestChargedCents");
        endingBalances = immutableDebtMap(endingBalances, "endingBalances");
        if (!interestChargedCents.keySet().equals(endingBalances.keySet())) {
            throw new IllegalArgumentException("interest charges and ending balances must contain the same debt IDs");
        }
        for (long interest : interestChargedCents.values()) {
            if (interest < 0) {
                throw new IllegalArgumentException("interest charges cannot be negative");
            }
        }

        payments = List.copyOf(payments);
        for (ProjectedPayment payment : payments) {
            PaymentAction action = payment.action();
            if (action.scheduledDate().isBefore(startDate) || !action.scheduledDate().isBefore(endDate)) {
                throw new IllegalArgumentException("payment dates must fall within the window");
            }
            if (!endingBalances.containsKey(action.debtId())) {
                throw new IllegalArgumentException("payment debt IDs must exist in the ending balances");
            }
        }
        if (sumPayments(payments) > Math.max(availableCents, 0L)) {
            throw new IllegalArgumentException("payments cannot exceed available cash");
        }
        payments = payments.stream()
                .sorted(Comparator.comparing((ProjectedPayment payment) -> payment.action().scheduledDate())
                        .thenComparing(payment -> payment.action().debtId())
                        .thenComparing(ProjectedPayment::kind))
                .toList();
    }

    /**
     * Returns actual payments in cents, counting each payoff adjustment once.
     * Throws {@link ArithmeticException} if the sum exceeds the long range.
     */
    public long totalPaidCents() {
        return sumPayments(payments);
    }

    /**
     * Returns newly charged interest, including any portion still unpaid.
     * Throws {@link ArithmeticException} if the sum exceeds the long range.
     */
    public long totalInterestChargedCents() {
        return interestChargedCents.values().stream().reduce(0L, Math::addExact);
    }

    /** Returns unused payment cash; a negative available amount provides no cash. */
    public long unspentCents() {
        return Math.max(availableCents, 0L) - totalPaidCents();
    }

    /** Returns whether any required minimum amount was left unfunded. */
    public boolean hasMinimumShortfall() {
        return minimumShortfallCents > 0;
    }

    private static long sumPayments(List<ProjectedPayment> payments) {
        long total = 0L;
        for (ProjectedPayment payment : payments) {
            total = Math.addExact(total, payment.action().paymentAmountCents());
        }
        return total;
    }

    private static <T> Map<String, T> immutableDebtMap(Map<String, T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " cannot be null");
        Map<String, T> copy = new TreeMap<>();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            String debtId = Objects.requireNonNull(entry.getKey(), fieldName + " debt IDs cannot be null");
            if (debtId.isBlank()) {
                throw new IllegalArgumentException(fieldName + " debt IDs cannot be blank");
            }
            T value = Objects.requireNonNull(entry.getValue(), fieldName + " values cannot be null");
            copy.put(debtId, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
