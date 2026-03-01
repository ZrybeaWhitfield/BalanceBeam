package dev.balancebeam.core.plan;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@DisplayName("PlanSummary")
class PlanSummaryTest {

    @Test
    @DisplayName("constructs with all fields accessible")
    void planSummary_constructsAndAccessorsReturnExpectedValues() {
        LocalDate payoffDate = LocalDate.of(2030, 6, 1);
        PlanSummary summary = new PlanSummary(250000L, 12500L, payoffDate);

        Assertions.assertEquals(250000L, summary.totalDebtCents());
        Assertions.assertEquals(12500L, summary.totalMonthlyMinimumCents());
        Assertions.assertEquals(payoffDate, summary.projectedPayoffDate());
    }

    @Test
    @DisplayName("null projectedPayoffDate is accepted")
    void nullProjectedPayoffDate_isAccepted() {
        Assertions.assertDoesNotThrow(() -> new PlanSummary(0L, 0L, null));
    }

    @Test
    @DisplayName("equal PlanSummary records with the same values are equal")
    void equalPlanSummaries_areEqual() {
        LocalDate payoffDate = LocalDate.of(2030, 6, 1);
        PlanSummary a = new PlanSummary(250000L, 12500L, payoffDate);
        PlanSummary b = new PlanSummary(250000L, 12500L, payoffDate);

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("PlanSummaries with different totalDebtCents are not equal")
    void differentTotalDebt_areNotEqual() {
        LocalDate payoffDate = LocalDate.of(2030, 6, 1);
        PlanSummary a = new PlanSummary(250000L, 12500L, payoffDate);
        PlanSummary b = new PlanSummary(300000L, 12500L, payoffDate);

        Assertions.assertNotEquals(a, b);
    }
}
