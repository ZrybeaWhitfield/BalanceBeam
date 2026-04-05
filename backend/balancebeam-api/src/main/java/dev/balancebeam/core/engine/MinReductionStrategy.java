package dev.balancebeam.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Minimum Reduction payoff Strategy
 * 
 * Allocates extra payment to the highest minimum payment first, then cascades
 * any remaining to the next highest minimum payment debt.
 * 
 * Tie-break order:
 * 1) Highest minimumPayment
 * 2) Lowest balanceCents
 * 3) Highest aprBasisPoints
 * 4) Lowest id
 */
public final class MinReductionStrategy implements PayoffStrategy {
    private static final Comparator<Debt> MIN_REDUCTION_ORDER = Comparator
            .<Debt, Long>comparing(Debt::minimumPaymentCents).reversed()
            .thenComparingLong(Debt::balanceCents)
            .thenComparing(Comparator.comparingInt(Debt::aprBasisPoints).reversed())
            .thenComparing(Debt::id);

    /**
     * Allocates extra cents using minimum-reduction ordering.
     *
     * @param debts      → debts considered for extra allocation (zero-balance debts
     *                   are skipped)
     * @param extraCents → extra amount available in cents; must be >= 0
     * @return debtId → allocation cents
     */
    @Override
    public Map<String, Long> allocateExtra(List<Debt> debts, long extraCents) {
        if (extraCents < 0) {
            throw new IllegalArgumentException("extraCents must be >= 0");
        }

        if (debts.isEmpty() || extraCents == 0) {
            return Map.of();
        }

        List<Debt> eligibleDebts = new ArrayList<>(
                debts.stream().filter(debt -> debt.balanceCents() > 0).toList());

        Map<String, Long> result = new LinkedHashMap<>();
        long remaining = extraCents;
        while (true) {
            long remainingBudget = remaining;
            Debt target = eligibleDebts.stream()
                    .filter(debt -> debt.balanceCents() <= remainingBudget)
                    .min(MIN_REDUCTION_ORDER)
                    .orElse(null);

            if (target == null) {
                break;
            }

            result.put(target.id(), target.balanceCents());
            remaining -= target.balanceCents();
            eligibleDebts.remove(target);
        }

        if (remaining > 0 && !eligibleDebts.isEmpty()) {
            eligibleDebts.sort(AvalancheStrategy.AVALANCHE_ORDER);

            for (Debt debt : eligibleDebts) {
                if (remaining <= 0) {
                    break;
                }
                long allocation = Math.min(remaining, debt.balanceCents());
                result.put(debt.id(), allocation);
                remaining -= allocation;
            }
        }

        return Map.copyOf(result);
    }
}
