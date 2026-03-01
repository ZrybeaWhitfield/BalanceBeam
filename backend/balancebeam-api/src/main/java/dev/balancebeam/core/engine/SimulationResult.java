package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SimulationResult(
        long totalInterestPaidCents,
        int periodsSimulated,
        LocalDate estimatedPayoffDate,
        Map<String, Long> endingBalances,
        List<PeriodSnapshot> snapshots) {
}