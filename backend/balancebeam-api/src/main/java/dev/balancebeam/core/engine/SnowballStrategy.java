package dev.balancebeam.core.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Allocates extra payments to the lowest-balance debt first, then cascades to
 * the next debt after a payoff. Equal balances are ordered by higher APR, then
 * lexicographically lower debt ID.
 */
public final class SnowballStrategy implements PayoffStrategy {
    private static final Comparator<Debt> SNOWBALL_ORDER = Comparator
            .comparingLong(Debt::balanceCents)
            .thenComparing(Comparator.comparingInt(Debt::aprBasisPoints).reversed())
            .thenComparing(Debt::id);

    @Override
    public Map<String, Long> allocateExtra(List<Debt> debts, long extraCents) {
        return OrderedExtraAllocator.allocate(debts, extraCents, SNOWBALL_ORDER);
    }
}
