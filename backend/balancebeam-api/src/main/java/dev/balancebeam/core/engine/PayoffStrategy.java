package dev.balancebeam.core.engine;

import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Strategy interface for allocating extra funds toward paying off debts.
 *
 * Implementations define how additional money is distributed across a list of
 * debts using a specific payoff strategy (for example, avalanche or snowball).
 */
public interface PayoffStrategy {

    /**
     * Allocates extra funds toward one or more debts according to this strategy.
     *
     * @param debts      → debts to consider for allocation. May include
     *                   zero-balance debts, which implementations must skip and
     *                   exclude from the result.
     * @param extraCents → the amount in cents available to allocate. Must be >= 0.
     *
     * @return debtId → allocation cents, where all allocations are positive and the
     *         sum of values is <= extraCents
     *
     * @throws IllegalArgumentException if extraCents is negative
     */
    Map<String, Long> allocateExtra(List<Debt> debts, long extraCents);

}
