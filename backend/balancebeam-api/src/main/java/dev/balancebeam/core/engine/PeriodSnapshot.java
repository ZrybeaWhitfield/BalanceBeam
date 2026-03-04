package dev.balancebeam.core.engine;

import java.util.Map;

public record PeriodSnapshot(
        int periodIndex,
        Map<String, Long> balancesAfter,
        Map<String, Long> interestCharged,
        Map<String, Long> principalApplied) {

    public PeriodSnapshot {
        balancesAfter = Map.copyOf(balancesAfter);
        interestCharged = Map.copyOf(interestCharged);
        principalApplied = Map.copyOf(principalApplied);
    }
}