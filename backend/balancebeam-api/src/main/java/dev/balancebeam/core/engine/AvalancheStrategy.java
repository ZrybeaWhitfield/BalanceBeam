package dev.balancebeam.core.engine;

import java.util.Comparator;
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
        return OrderedExtraAllocator.allocate(debts, extraCents, AVALANCHE_ORDER);
    }
}
