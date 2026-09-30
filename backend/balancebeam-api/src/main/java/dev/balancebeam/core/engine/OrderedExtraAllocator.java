package dev.balancebeam.core.engine;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Allocates extra cents across debts in the order supplied by a strategy.
 * Skips paid-off debts and caps each payment at the debt's remaining balance.
 * The returned map does not guarantee iteration order.
 */
final class OrderedExtraAllocator {
    private OrderedExtraAllocator() {
    }

    static Map<String, Long> allocate(List<Debt> debts, long extraCents, Comparator<Debt> order) {
        if (extraCents < 0) {
            throw new IllegalArgumentException("extraCents must be >= 0");
        }

        if (debts.isEmpty() || extraCents == 0) {
            return Map.of();
        }

        List<Debt> eligibleDebts = debts.stream()
                .filter(debt -> debt.balanceCents() > 0)
                .sorted(order)
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
