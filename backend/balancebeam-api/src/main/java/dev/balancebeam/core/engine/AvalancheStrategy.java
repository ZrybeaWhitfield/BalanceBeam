package dev.balancebeam.core.engine;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Avalanche payoff strategy.
 *
 * Allocates extra payment to the highest-APR debt first, then cascades any
 * remaining amount to the next highest APR debt.
 *
 * Tie-break order (deterministic):
 * 1) Highest aprBasisPoints (descending)
 * 2) Lowest balanceCents (ascending)
 * 3) Lowest id (ascending, lexicographic)
 */
public final class AvalancheStrategy implements PayoffStrategy {
    static final Comparator<Debt> AVALANCHE_ORDER = Comparator.comparingInt(Debt::aprBasisPoints).reversed()
            .thenComparingLong(Debt::balanceCents)
            .thenComparing(Debt::id);

    /**
     * Allocates extra cents using avalanche ordering.
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

        List<Debt> eligibleDebts = debts.stream().filter(debt -> debt.balanceCents() > 0).sorted(AVALANCHE_ORDER)
                .toList();

        Map<String, Long> result = new LinkedHashMap<>();
        long remaining = extraCents;

        for (Debt debt : eligibleDebts) {
            long allocation = Math.min(remaining, debt.balanceCents());
            result.put(debt.id(), allocation);
            remaining -= allocation;
            if (remaining <= 0) {
                break;
            }
        }

        return Map.copyOf(result);
    }
}
