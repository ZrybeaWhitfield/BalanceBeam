package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.model.ProjectionOptions;
import dev.balancebeam.core.plan.DebtBalance;
import dev.balancebeam.core.plan.PayoffProjection;
import dev.balancebeam.core.plan.PaymentAction;
import dev.balancebeam.core.plan.PaymentKind;
import dev.balancebeam.core.plan.ProjectedPayment;
import dev.balancebeam.core.plan.ProjectionAssumptions;
import dev.balancebeam.core.plan.ProjectionStatus;
import dev.balancebeam.core.plan.ProjectionWindow;

@DisplayName("BiweeklyPayoffProjector")
class BiweeklyPayoffProjectorTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 6);
    private static final PaySchedule SCHEDULE = new PaySchedule(PayFrequency.BIWEEKLY, START);
    private static final Budget BUDGET = new Budget(200_000L, 150_000L, 10_000L);
    private final BiweeklyPayoffProjector projector = new BiweeklyPayoffProjector();

    @Nested
    @DisplayName("Payoff and repeated windows")
    class Payoff {
        @Test
        @DisplayName("Returns the worked payoff's actual date, adjustment, and reconciled totals")
        void workedPayoff_returnsActualPaymentDateAndTotals() {
            ProjectionDebt loan = new ProjectionDebt(
                    loan("loan-1", 37_025L, 1000, 40_000L, 13).debt(), 500L);

            PayoffProjection projection = project(List.of(loan), 40_000L, 10);

            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(START.plusDays(7), projection.payoffDate());
            assertEquals(1, projection.windows().size());
            assertEquals(List.of(new ProjectedPayment(
                    new PaymentAction("loan-1", 37_095L, START.plusDays(7)), PaymentKind.MINIMUM, 70L)),
                    projection.windows().getFirst().payments());
            assertEquals(Map.of("loan-1", new DebtBalance(36_525L, 500L)), projection.openingBalances());
            assertEquals(Map.of("loan-1", new DebtBalance(0L, 0L)), projection.endingBalances());
            assertEquals(37_095L, projection.totalPaidCents());
            assertEquals(70L, projection.totalInterestChargedCents());
            assertEquals(2_905L, projection.windows().getFirst().unspentCents());
            assertFalse(projection.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Recalculates against the remaining balance and stops after the second paycheck")
        void debtNeedsTwoPaychecks_stopsAfterSecondWindow() {
            ProjectionDebt loan = loan("loan-1", 60_000L, 0, 10_000L, 6);

            PayoffProjection projection = project(List.of(loan), 40_000L, 10);

            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(2, projection.windows().size());
            assertEquals(List.of(START, START.plusDays(14)),
                    projection.windows().stream().map(ProjectionWindow::startDate).toList());
            assertEquals(START.plusDays(28), projection.windows().getLast().endDate());
            assertEquals(40_000L, projection.windows().getFirst().totalPaidCents());
            assertEquals(new DebtBalance(20_000L, 0L),
                    projection.windows().getFirst().endingBalances().get("loan-1"));
            assertEquals(List.of(new ProjectedPayment(new PaymentAction("loan-1", 20_000L, START.plusDays(14)),
                    PaymentKind.EXTRA, 0L)), projection.windows().getLast().payments());
            assertEquals(START.plusDays(14), projection.payoffDate());
            assertEquals(60_000L, projection.totalPaidCents());
            assertEquals(20_000L, projection.windows().getLast().unspentCents());
        }

        @Test
        @DisplayName("Freed payment capacity goes to the remaining debt without reviving a paid-off debt")
        void firstDebtPaidOff_laterPaychecksFundRemainingDebt() {
            ProjectionDebt smaller = loan("small", 20_000L, 0, 5_000L, 6);
            ProjectionDebt larger = loan("large", 80_000L, 0, 5_000L, 6);

            PayoffProjection projection = projector.project(List.of(larger, smaller), BUDGET, SCHEDULE,
                    new SnowballStrategy(), new ProjectionOptions(10));

            assertEquals(3, projection.windows().size());
            assertEquals(Map.of("small", new DebtBalance(0L, 0L), "large", new DebtBalance(60_000L, 0L)),
                    projection.windows().getFirst().endingBalances());
            assertEquals(List.of(new ProjectedPayment(new PaymentAction("large", 40_000L, START.plusDays(14)),
                    PaymentKind.EXTRA, 0L)), projection.windows().get(1).payments());
            ProjectionWindow last = projection.windows().getLast();
            assertEquals(5_000L, last.totalMinimumsDueCents());
            assertTrue(last.payments().stream().allMatch(payment -> payment.action().debtId().equals("large")));
            assertEquals(Map.of("small", new DebtBalance(0L, 0L), "large", new DebtBalance(0L, 0L)),
                    projection.endingBalances());
            assertEquals(100_000L, projection.totalPaidCents());
            assertEquals(LocalDate.of(2026, 11, 6), projection.payoffDate());
        }

        @Test
        @DisplayName("Uses the supplied strategy with updated balances on later paydays")
        void customStrategy_receivesUpdatedBalancesEachWindow() {
            List<Long> targetBalances = new ArrayList<>();
            PayoffStrategy targetLastId = (debts, cash) -> {
                Debt target = debts.stream().filter(debt -> debt.id().equals("z")).findFirst().orElseThrow();
                targetBalances.add(target.balanceCents());
                return Map.of("z", Math.min(target.balanceCents(), cash));
            };

            PayoffProjection projection = projector.project(List.of(
                    loan("a", 100_000L, 0, 0L, 6), loan("z", 100_000L, 0, 0L, 6)),
                    BUDGET, SCHEDULE, targetLastId, new ProjectionOptions(2));

            assertEquals(List.of(100_000L, 60_000L), targetBalances);
            assertEquals(Map.of("a", new DebtBalance(100_000L, 0L), "z", new DebtBalance(20_000L, 0L)),
                    projection.endingBalances());
        }

        @Test
        @DisplayName("A minimum on the next payday is scheduled in the second window")
        void minimumOnWindowBoundary_paidInFollowingWindow() {
            PayoffProjection projection = projector.project(List.of(loan("loan-1", 50_000L, 0, 10_000L, 20)),
                    budgetWithAvailable(10_000L), SCHEDULE, (debts, cash) -> Map.of(), new ProjectionOptions(2));

            assertEquals(List.of(), projection.windows().getFirst().payments());
            assertEquals(0L, projection.windows().getFirst().totalMinimumsDueCents());
            assertEquals(List.of(new ProjectedPayment(new PaymentAction("loan-1", 10_000L, START.plusDays(14)),
                    PaymentKind.MINIMUM, 0L)), projection.windows().getLast().payments());
            assertEquals(new DebtBalance(40_000L, 0L), projection.endingBalances().get("loan-1"));
        }
    }

    @Nested
    @DisplayName("Completion boundaries")
    class Completion {
        @Test
        @DisplayName("Returns remaining debt and no payoff date when the limit is reached")
        void debtRemainsAtLimit_returnsHorizonReached() {
            PayoffProjection projection = project(List.of(loan("loan-1", 60_000L, 0, 10_000L, 6)), 40_000L, 1);

            assertEquals(ProjectionStatus.HORIZON_REACHED, projection.status());
            assertNull(projection.payoffDate());
            assertEquals(1, projection.windows().size());
            assertEquals(new DebtBalance(20_000L, 0L), projection.endingBalances().get("loan-1"));
            assertEquals(40_000L, projection.totalPaidCents());
        }

        @Test
        @DisplayName("Reports payoff when the final allowed window clears the debt")
        void payoffOnLastAllowedWindow_returnsPaidOff() {
            PayoffProjection projection = project(List.of(loan("loan-1", 60_000L, 0, 10_000L, 6)), 40_000L, 2);

            assertEquals(2, projection.windows().size());
            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(START.plusDays(14), projection.payoffDate());
        }

        @Test
        @DisplayName("An empty portfolio is paid off on the start date without generating windows")
        void emptyPortfolio_returnsDebtFreeStart() {
            PayoffProjection projection = project(List.of(), 40_000L, 10);

            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(START, projection.payoffDate());
            assertEquals(List.of(), projection.windows());
            assertEquals(Map.of(), projection.endingBalances());
            assertEquals(0L, projection.totalPaidCents());
            assertEquals(0L, projection.totalInterestChargedCents());
        }

        @Test
        @DisplayName("An already-paid-off portfolio creates no payments or interest windows")
        void zeroBalancePortfolio_returnsDebtFreeStart() {
            PayoffProjection projection = project(List.of(loan("loan-1", 0L, 1000, 10_000L, 13)), 40_000L, 10);

            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(START, projection.payoffDate());
            assertEquals(List.of(), projection.windows());
            assertEquals(Map.of("loan-1", new DebtBalance(0L, 0L)), projection.endingBalances());
            assertEquals(0L, projection.totalPaidCents());
            assertEquals(0L, projection.totalInterestChargedCents());
        }

        @Test
        @DisplayName("The default horizon calculates 780 windows when debt remains")
        void defaultOptionsWithUnpaidDebt_stopsAfter780Windows() {
            PayoffProjection projection = projector.project(List.of(loan("loan-1", 100_000L, 0, 10_000L, 13)),
                    budgetWithAvailable(0L), SCHEDULE, new AvalancheStrategy(), new ProjectionOptions());

            assertEquals(780, projection.windows().size());
            assertEquals(START.plusDays(10_920), projection.windows().getLast().endDate());
            assertEquals(ProjectionStatus.HORIZON_REACHED, projection.status());
            assertNull(projection.payoffDate());
        }
    }

    @Nested
    @DisplayName("Shortfalls and interest continuity")
    class Continuity {
        @Test
        @DisplayName("Continues after a minimum shortfall and retains the warning after payoff")
        void earlyMinimumShortfall_continuesToPayoff() {
            PayoffProjection projection = project(List.of(loan("loan-1", 60_000L, 0, 50_000L, 6)), 20_000L, 10);

            assertEquals(3, projection.windows().size());
            assertEquals(30_000L, projection.windows().getFirst().minimumShortfallCents());
            assertEquals(0L, projection.windows().getLast().minimumShortfallCents());
            assertTrue(projection.hasMinimumShortfall());
            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(60_000L, projection.totalPaidCents());
            assertEquals(new DebtBalance(0L, 0L), projection.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("A budget gap continues accruing interest without creating payments")
        void negativeAvailable_continuesWithoutPayments() {
            PayoffProjection projection = project(List.of(loan("loan-1", 36_525L, 1000, 10_000L, 13)), -5_000L, 2);

            assertEquals(2, projection.windows().size());
            for (ProjectionWindow window : projection.windows()) {
                assertEquals(-5_000L, window.availableCents());
                assertEquals(List.of(), window.payments());
                assertEquals(0L, window.unspentCents());
            }
            assertEquals(ProjectionStatus.HORIZON_REACHED, projection.status());
            assertTrue(projection.hasMinimumShortfall());
            assertEquals(280L, projection.totalInterestChargedCents());
            assertEquals(new DebtBalance(36_525L, 280L), projection.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("Zero available cash preserves card compounding and reports each window's new charges")
        void zeroAvailableCard_carriesCompoundingAcrossWindows() {
            PayoffProjection projection = project(List.of(card("card-1", 100_000L, 3650)), 0L, 2);

            assertEquals(0L, projection.totalPaidCents());
            assertEquals(Map.of("card-1", 1_409L), projection.windows().getFirst().interestChargedCents());
            assertEquals(Map.of("card-1", 1_429L), projection.windows().getLast().interestChargedCents());
            assertEquals(2_838L, projection.totalInterestChargedCents());
            assertEquals(new DebtBalance(100_000L, 2_838L), projection.endingBalances().get("card-1"));
            assertEquals(ProjectionStatus.HORIZON_REACHED, projection.status());
            assertTrue(projection.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Fractional interest survives repeated windows without being rounded independently")
        void fractionalInterestAcrossWindows_preservesCumulativeRounding() {
            PayoffProjection projection = project(List.of(loan("loan-1", 36_525L, 25, 10_000L, 13)), 0L, 2);

            assertEquals(Map.of("loan-1", 4L), projection.windows().getFirst().interestChargedCents());
            assertEquals(Map.of("loan-1", 3L), projection.windows().getLast().interestChargedCents());
            assertEquals(7L, projection.totalInterestChargedCents());
            assertEquals(new DebtBalance(36_525L, 7L), projection.endingBalances().get("loan-1"));
        }
    }

    @Nested
    @DisplayName("Opening balances and repeatability")
    class Inputs {
        @Test
        @DisplayName("Only omitted student-loan interest creates an assumption, while supplied splits are preserved")
        void mixedOpeningInputs_resolvesBalancesAndAssumptions() {
            ProjectionDebt omitted = new ProjectionDebt(loan("omitted", 100_000L, 0, 10_000L, 13).debt());
            ProjectionDebt explicitZero = loan("zero", 200_000L, 0, 10_000L, 13);
            ProjectionDebt supplied = new ProjectionDebt(loan("supplied", 105_000L, 0, 10_000L, 13).debt(), 5_000L);

            PayoffProjection projection = project(List.of(omitted, explicitZero, supplied, card("card", 100_000L, 0)),
                    0L, 1);

            assertEquals(new ProjectionAssumptions("biweekly-v1", Set.of("omitted")), projection.assumptions());
            assertEquals(Map.of(
                    "omitted", new DebtBalance(100_000L, 0L), "zero", new DebtBalance(200_000L, 0L),
                    "supplied", new DebtBalance(100_000L, 5_000L), "card", new DebtBalance(100_000L, 0L)),
                    projection.openingBalances());
        }

        @Test
        @DisplayName("A loan consisting entirely of opening interest pays off without charging it again")
        void interestOnlyOpeningBalance_createsNoNewInterest() {
            ProjectionDebt loan = new ProjectionDebt(loan("loan-1", 5_000L, 1000, 5_000L, 13).debt(), 5_000L);

            PayoffProjection projection = project(List.of(loan), 5_000L, 2);

            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
            assertEquals(START.plusDays(7), projection.payoffDate());
            assertEquals(5_000L, projection.totalPaidCents());
            assertEquals(0L, projection.totalInterestChargedCents());
            assertEquals(new DebtBalance(0L, 5_000L), projection.openingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("Reordered inputs and repeated calls on the same projector produce identical forecasts")
        void repeatedAndReorderedInputs_produceSameProjection() {
            ProjectionDebt loan = loan("loan-1", 100_000L, 600, 10_000L, 13);
            ProjectionDebt card = card("card-1", 150_000L, 2400);

            PayoffProjection original = project(List.of(loan, card), 40_000L, 3);

            assertEquals(original, project(List.of(card, loan), 40_000L, 3));
            assertEquals(original, project(List.of(loan, card), 40_000L, 3));
        }
    }

    @Nested
    @DisplayName("Invalid inputs and date boundaries")
    class Validation {
        @Test
        @DisplayName("Rejects missing debts")
        void nullDebts_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(null, BUDGET, SCHEDULE,
                    new AvalancheStrategy(), new ProjectionOptions()));
        }

        @Test
        @DisplayName("Rejects a missing budget")
        void nullBudget_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), null, SCHEDULE,
                    new AvalancheStrategy(), new ProjectionOptions()));
        }

        @Test
        @DisplayName("Rejects a missing pay schedule")
        void nullSchedule_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), BUDGET, null,
                    new AvalancheStrategy(), new ProjectionOptions()));
        }

        @Test
        @DisplayName("Rejects a missing strategy")
        void nullStrategy_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), BUDGET, SCHEDULE,
                    null, new ProjectionOptions()));
        }

        @Test
        @DisplayName("Rejects missing projection options")
        void nullOptions_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), BUDGET, SCHEDULE,
                    new AvalancheStrategy(), null));
        }

        @Test
        @DisplayName("Rejects a null entry in the debt list")
        void nullDebtEntry_throwsNullPointerException() {
            List<ProjectionDebt> debts = Arrays.asList(loan("loan-1", 50_000L, 0, 10_000L, 13), null);

            assertThrows(NullPointerException.class, () -> project(debts, 40_000L, 1));
        }

        @Test
        @DisplayName("Rejects different debts sharing the same ID")
        void duplicateDebtIds_throwsIllegalArgumentException() {
            List<ProjectionDebt> debts = List.of(
                    loan("duplicate", 50_000L, 0, 10_000L, 13), card("duplicate", 100_000L, 2400));

            assertThrows(IllegalArgumentException.class, () -> project(debts, 40_000L, 1));
        }

        @Test
        @DisplayName("Rejects a non-biweekly pay schedule")
        void monthlySchedule_throwsIllegalArgumentException() {
            PaySchedule monthly = new PaySchedule(PayFrequency.MONTHLY, START);

            assertThrows(IllegalArgumentException.class, () -> projector.project(List.of(), BUDGET, monthly,
                    new AvalancheStrategy(), new ProjectionOptions()));
        }

        @Test
        @DisplayName("Rejects an unrepresentable full horizon before calculating the first window")
        void requestedHorizonOverflows_throwsBeforeStrategyRuns() {
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.MAX.minusDays(20));
            PayoffStrategy unexpectedCall = (debts, cash) -> fail("Strategy must not run for an invalid date range");

            assertThrows(DateTimeException.class, () -> projector.project(
                    List.of(loan("loan-1", 50_000L, 0, 0L, 28)), BUDGET, schedule,
                    unexpectedCall, new ProjectionOptions(2)));
        }

        @Test
        @DisplayName("Allows a requested horizon ending exactly at the maximum supported date")
        void horizonEndsAtMaximumDate_returnsProjection() {
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.MAX.minusDays(14));

            PayoffProjection projection = projector.project(List.of(loan("loan-1", 10_000L, 0, 10_000L, 28)),
                    BUDGET, schedule, new AvalancheStrategy(), new ProjectionOptions(1));

            assertEquals(LocalDate.MAX, projection.windows().getLast().endDate());
            assertEquals(LocalDate.MAX.withDayOfMonth(28), projection.payoffDate());
            assertEquals(ProjectionStatus.PAID_OFF, projection.status());
        }
    }

    private PayoffProjection project(List<ProjectionDebt> debts, long available, int maxWindows) {
        return projector.project(debts, budgetWithAvailable(available), SCHEDULE,
                new AvalancheStrategy(), new ProjectionOptions(maxWindows));
    }

    private static Budget budgetWithAvailable(long available) {
        return new Budget(200_000L, 190_000L - available, 10_000L);
    }

    private static ProjectionDebt loan(String id, long balance, int apr, long minimum, int dueDay) {
        return new ProjectionDebt(new Debt(id, "Student loan " + id, DebtType.STUDENT_LOAN,
                balance, apr, minimum, dueDay, null), 0L);
    }

    private static ProjectionDebt card(String id, long balance, int apr) {
        return new ProjectionDebt(new Debt(id, "Credit card " + id, DebtType.CREDIT_CARD,
                balance, apr, 5_000L, 13, 500_000L));
    }
}
