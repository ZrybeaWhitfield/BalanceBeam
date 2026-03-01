package dev.balancebeam.core.plan;

import java.time.LocalDate;

public record PlanSummary(
        long totalDebtCents,
        long totalMonthlyMinimumCents,
        LocalDate projectedPayoffDate) {
}