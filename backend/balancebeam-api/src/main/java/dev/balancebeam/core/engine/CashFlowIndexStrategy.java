package dev.balancebeam.core.engine;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

/**
 * Prioritizes the lowest balance-to-minimum-payment ratio to free monthly cash flow.
 * Debts with no minimum payment rank last because their index is undefined.
 * Equal indexes are ordered by higher APR, lower balance, then lower id.
 */
public final class CashFlowIndexStrategy implements PayoffStrategy {
    private static final Comparator<Debt> TIE_BREAK_ORDER = Comparator
            .comparingInt(Debt::aprBasisPoints).reversed()
            .thenComparingLong(Debt::balanceCents)
            .thenComparing(Debt::id);

    private static final Comparator<Debt> CASH_FLOW_INDEX_ORDER = (first, second) -> {
        long firstMinimum = first.minimumPaymentCents();
        long secondMinimum = second.minimumPaymentCents();

        if (firstMinimum == 0 || secondMinimum == 0) {
            if (firstMinimum == 0 && secondMinimum != 0) {
                return 1;
            }
            if (secondMinimum == 0 && firstMinimum != 0) {
                return -1;
            }
        } else {
            int indexComparison = BigInteger.valueOf(first.balanceCents())
                    .multiply(BigInteger.valueOf(secondMinimum))
                    .compareTo(BigInteger.valueOf(second.balanceCents())
                            .multiply(BigInteger.valueOf(firstMinimum)));
            if (indexComparison != 0) {
                return indexComparison;
            }
        }

        return TIE_BREAK_ORDER.compare(first, second);
    };

    @Override
    public Map<String, Long> allocateExtra(List<Debt> debts, long extraCents) {
        return OrderedExtraAllocator.allocate(debts, extraCents, CASH_FLOW_INDEX_ORDER);
    }
}
