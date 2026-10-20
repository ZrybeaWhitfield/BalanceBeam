package dev.balancebeam.core.plan;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Calculation rules and opening-interest assumptions used by a payoff projection.
 * The projector records student loans whose omitted opening unpaid interest was
 * treated as zero. Loans with an explicitly supplied zero and credit cards are
 * excluded from that set.
 *
 * @param modelVersion               nonblank identifier for the calculation rules used
 * @param assumedZeroInterestDebtIds  student-loan IDs with assumed zero opening unpaid
 *                                    interest; copied into an immutable set that iterates
 *                                    in debt ID order; may be empty
 */
public record ProjectionAssumptions(String modelVersion, Set<String> assumedZeroInterestDebtIds) {
    public ProjectionAssumptions {
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(assumedZeroInterestDebtIds, "assumedZeroInterestDebtIds cannot be null");

        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion cannot be blank");
        }

        Set<String> debtIds = new TreeSet<>();
        for (String debtId : assumedZeroInterestDebtIds) {
            Objects.requireNonNull(debtId, "assumedZeroInterestDebtIds cannot contain null");
            if (debtId.isBlank()) {
                throw new IllegalArgumentException("assumedZeroInterestDebtIds cannot contain blank IDs");
            }
            debtIds.add(debtId);
        }
        assumedZeroInterestDebtIds = Collections.unmodifiableSet(debtIds);
    }
}
