package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.plan.DebtBalance;
import dev.balancebeam.core.plan.PaymentAction;
import dev.balancebeam.core.plan.PaymentKind;
import dev.balancebeam.core.plan.ProjectedPayment;
import dev.balancebeam.core.plan.ProjectionWindow;

@DisplayName("BiweeklyWindowProjector")
class BiweeklyWindowProjectorTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 6);
    private final BiweeklyWindowProjector projector = new BiweeklyWindowProjector();

    @Nested
    @DisplayName("Payments and paycheck cash")
    class Payments {
        @Test
        @DisplayName("Completes the worked payoff with accrued interest and protected buffer")
        void workedPayoff_recordsActualPaymentAndInterest() {
            ProjectionDebt loan = workedLoan();
            Map<String, DebtInterestState> states = statesFor(loan);

            ProjectionWindow window = projector.project(List.of(loan.debt()), states,
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy());

            assertEquals(List.of(new ProjectedPayment(
                    new PaymentAction("loan-1", 37_095L, START.plusDays(7)), PaymentKind.MINIMUM, 70L)),
                    window.payments());
            assertEquals(Map.of("loan-1", 70L), window.interestChargedCents());
            assertEquals(Map.of("loan-1", new DebtBalance(0L, 0L)), window.endingBalances());
            assertEquals(37_095L, window.totalPaidCents());
            assertEquals(2_905L, window.unspentCents());
            assertEquals(10_000L, window.bufferCents());
            assertEquals(37_025L, window.totalMinimumsDueCents());
            assertFalse(window.hasMinimumShortfall());
            assertEquals(new DebtBalance(0L, 0L), states.get("loan-1").balance());
        }

        @Test
        @DisplayName("An extra payment on payday reduces principal before the day's interest")
        void extraOnPayday_accruesOnReducedPrincipal() {
            ProjectionDebt loan = loan("loan-1", 365_250L, 1000, 15_000L, 20);

            ProjectionWindow window = project(List.of(loan), 182_625L, new AvalancheStrategy());

            assertEquals(List.of(new ProjectedPayment(new PaymentAction("loan-1", 182_625L, START),
                    PaymentKind.EXTRA, 0L)), window.payments());
            assertEquals(Map.of("loan-1", 700L), window.interestChargedCents());
            assertEquals(new DebtBalance(182_625L, 700L), window.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("A reserved minimum leaves the full balance accruing until its due date")
        void laterMinimum_accruesBeforeActualPayment() {
            ProjectionDebt loan = loan("loan-1", 365_250L, 1000, 15_000L, 13);

            ProjectionWindow window = project(List.of(loan), 15_000L, new AvalancheStrategy());

            assertEquals(List.of(new ProjectedPayment(new PaymentAction("loan-1", 15_000L, START.plusDays(7)),
                    PaymentKind.MINIMUM, 0L)), window.payments());
            assertEquals(Map.of("loan-1", 1_373L), window.interestChargedCents());
            assertEquals(new DebtBalance(350_950L, 673L), window.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("Same-day minimum and extra payments both precede the day's single accrual")
        void minimumAndExtraOnPayday_accruesOnceAfterBoth() {
            ProjectionDebt loan = loan("loan-1", 365_250L, 1000, 15_000L, 6);

            ProjectionWindow window = project(List.of(loan), 182_625L, new AvalancheStrategy());

            assertEquals(List.of(
                    new ProjectedPayment(new PaymentAction("loan-1", 15_000L, START), PaymentKind.MINIMUM, 0L),
                    new ProjectedPayment(new PaymentAction("loan-1", 167_625L, START), PaymentKind.EXTRA, 0L)),
                    window.payments());
            assertEquals(Map.of("loan-1", 700L), window.interestChargedCents());
        }

        @Test
        @DisplayName("A short paycheck pays minimums in debt ID order and reports the unfunded amount")
        void sameDateMinimumShortfall_preservesDeterministicPriority() {
            ProjectionDebt first = loan("a", 100_000L, 0, 15_000L, 13);
            ProjectionDebt second = loan("z", 150_000L, 0, 10_000L, 13);

            ProjectionWindow window = project(List.of(second, first), 20_000L, new AvalancheStrategy());

            assertEquals(List.of(
                    new ProjectedPayment(new PaymentAction("a", 15_000L, START.plusDays(7)), PaymentKind.MINIMUM, 0L),
                    new ProjectedPayment(new PaymentAction("z", 5_000L, START.plusDays(7)), PaymentKind.MINIMUM, 0L)),
                    window.payments());
            assertEquals(5_000L, window.minimumShortfallCents());
            assertEquals(0L, window.unspentCents());
        }

        @Test
        @DisplayName("Zero available cash leaves minimums unfunded while interest continues accruing")
        void zeroAvailable_accruesWithoutPayments() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 13);

            ProjectionWindow window = project(List.of(loan), 0L, new AvalancheStrategy());

            assertEquals(List.of(), window.payments());
            assertEquals(10_000L, window.minimumShortfallCents());
            assertEquals(new DebtBalance(36_525L, 140L), window.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("A budget gap creates no payments or negative debt balances")
        void negativeAvailable_preservesBudgetGapAndDebt() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 13);

            ProjectionWindow window = project(List.of(loan), -5_000L, new AvalancheStrategy());

            assertEquals(-5_000L, window.availableCents());
            assertEquals(0L, window.totalPaidCents());
            assertEquals(0L, window.unspentCents());
            assertEquals(10_000L, window.minimumShortfallCents());
            assertEquals(new DebtBalance(36_525L, 140L), window.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("An empty portfolio leaves available cash unspent")
        void emptyPortfolio_returnsEmptyWindow() {
            ProjectionWindow window = project(List.of(), 40_000L, new AvalancheStrategy());

            assertEquals(List.of(), window.payments());
            assertEquals(Map.of(), window.endingBalances());
            assertEquals(Map.of(), window.interestChargedCents());
            assertEquals(40_000L, window.unspentCents());
        }

        @Test
        @DisplayName("An already-paid-off debt remains in the result without payments or interest")
        void zeroBalanceDebt_remainsPaidOff() {
            ProjectionDebt loan = loan("loan-1", 0L, 600, 15_000L, 13);

            ProjectionWindow window = project(List.of(loan), 40_000L, new AvalancheStrategy());

            assertEquals(List.of(), window.payments());
            assertEquals(0L, window.totalMinimumsDueCents());
            assertEquals(Map.of("loan-1", 0L), window.interestChargedCents());
            assertEquals(Map.of("loan-1", new DebtBalance(0L, 0L)), window.endingBalances());
        }
    }

    @Nested
    @DisplayName("Calendar boundaries and continuing state")
    class DatesAndState {
        @Test
        @DisplayName("A minimum due on the next payday belongs to the next window")
        void minimumOnWindowEnd_isExcluded() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 20);

            ProjectionWindow window = project(List.of(loan), 0L, new AvalancheStrategy());

            assertEquals(START.plusDays(14), window.endDate());
            assertEquals(0L, window.totalMinimumsDueCents());
            assertEquals(0L, window.minimumShortfallCents());
            assertEquals(Map.of("loan-1", 140L), window.interestChargedCents());
        }

        @Test
        @DisplayName("A January minimum is paid on its date after crossing the year boundary")
        void windowCrossesYear_paysOnJanuaryDueDate() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 40_000L, 1);
            LocalDate start = LocalDate.of(2026, 12, 25);

            ProjectionWindow window = projector.project(List.of(loan.debt()), statesFor(loan),
                    budgetWithAvailable(40_000L), start, new AvalancheStrategy());

            assertEquals(List.of(new ProjectedPayment(
                    new PaymentAction("loan-1", 36_595L, LocalDate.of(2027, 1, 1)), PaymentKind.MINIMUM, 70L)),
                    window.payments());
            assertEquals(LocalDate.of(2027, 1, 8), window.endDate());
        }

        @Test
        @DisplayName("Interest includes leap day before a March payoff")
        void windowCrossesLeapDay_countsActualElapsedDays() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 40_000L, 1);
            LocalDate start = LocalDate.of(2028, 2, 23);

            ProjectionWindow window = projector.project(List.of(loan.debt()), statesFor(loan),
                    budgetWithAvailable(40_000L), start, new AvalancheStrategy());

            assertEquals(Map.of("loan-1", 70L), window.interestChargedCents());
            assertEquals(LocalDate.of(2028, 3, 1), window.payments().getFirst().action().scheduledDate());
            assertEquals(LocalDate.of(2028, 3, 8), window.endDate());
        }

        @Test
        @DisplayName("The next window allocates against updated balances rather than original debt amounts")
        void continuingState_usesCurrentBalanceForPaydayAllocation() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 13);
            Map<String, DebtInterestState> states = statesFor(loan);
            projector.project(List.of(loan.debt()), states, budgetWithAvailable(0L), START, new AvalancheStrategy());

            ProjectionWindow second = projector.project(List.of(loan.debt()), states,
                    budgetWithAvailable(40_000L), START.plusDays(14), new AvalancheStrategy());

            assertEquals(List.of(new ProjectedPayment(new PaymentAction("loan-1", 36_665L, START.plusDays(14)),
                    PaymentKind.EXTRA, 0L)), second.payments());
            assertEquals(Map.of("loan-1", 0L), second.interestChargedCents());
            assertEquals(new DebtBalance(0L, 0L), states.get("loan-1").balance());
        }

        @Test
        @DisplayName("Each window reports only its own card interest charges")
        void continuingCardState_reportsInterestDeltas() {
            ProjectionDebt card = creditCard("card-1", 100_000L, 3650, 5_000L, 13);
            Map<String, DebtInterestState> states = statesFor(card);

            ProjectionWindow first = projector.project(List.of(card.debt()), states,
                    budgetWithAvailable(0L), START, new AvalancheStrategy());
            ProjectionWindow second = projector.project(List.of(card.debt()), states,
                    budgetWithAvailable(0L), START.plusDays(14), new AvalancheStrategy());

            assertEquals(Map.of("card-1", 1_409L), first.interestChargedCents());
            assertEquals(Map.of("card-1", 1_429L), second.interestChargedCents());
            assertEquals(new DebtBalance(100_000L, 2_838L), second.endingBalances().get("card-1"));
        }

        @Test
        @DisplayName("Fractional interest survives a window boundary")
        void continuingFractionalState_usesCumulativeRounding() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 25, 10_000L, 13);
            Map<String, DebtInterestState> states = statesFor(loan);

            ProjectionWindow first = projector.project(List.of(loan.debt()), states,
                    budgetWithAvailable(0L), START, new AvalancheStrategy());
            ProjectionWindow second = projector.project(List.of(loan.debt()), states,
                    budgetWithAvailable(0L), START.plusDays(14), new AvalancheStrategy());

            assertEquals(Map.of("loan-1", 4L), first.interestChargedCents());
            assertEquals(Map.of("loan-1", 3L), second.interestChargedCents());
            assertEquals(new DebtBalance(36_525L, 7L), second.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("Another debt's payment date does not change a card's interest charges")
        void unrelatedDebtPayment_preservesCardAccrual() {
            ProjectionDebt card = creditCard("card-1", 100_000L, 3650, 0L, 13);
            ProjectionDebt loan = loan("loan-1", 50_000L, 0, 10_000L, 13);
            PayoffStrategy noExtras = (debts, cash) -> Map.of();

            ProjectionWindow alone = project(List.of(card), 10_000L, noExtras);
            ProjectionWindow together = project(List.of(card, loan), 10_000L, noExtras);

            assertEquals(Long.valueOf(1_409L), alone.interestChargedCents().get("card-1"));
            assertEquals(alone.endingBalances().get("card-1"), together.endingBalances().get("card-1"));
        }
    }

    @Nested
    @DisplayName("Final-payment adjustments")
    class Adjustments {
        @Test
        @DisplayName("A payoff without spare cash leaves the interest gap outstanding")
        void noSpareCash_preservesRemainingDebtAndBuffer() {
            ProjectionWindow window = project(List.of(workedLoan()), 37_025L, new AvalancheStrategy());

            assertEquals(37_025L, window.totalPaidCents());
            assertEquals(0L, adjustmentFor(window, "loan-1"));
            assertEquals(new DebtBalance(70L, 0L), window.endingBalances().get("loan-1"));
            assertEquals(0L, window.unspentCents());
            assertEquals(10_000L, window.bufferCents());
        }

        @Test
        @DisplayName("A partial adjustment protects later reserved payments and excludes future payoffs")
        void limitedSpareCash_preservesLaterMinimum() {
            ProjectionDebt later = loan("later", 20_000L, 0, 20_000L, 17);
            List<List<Debt>> strategyInputs = new ArrayList<>();
            PayoffStrategy strategy = (debts, cash) -> {
                strategyInputs.add(List.copyOf(debts));
                return new AvalancheStrategy().allocateExtra(debts, cash);
            };

            ProjectionWindow window = project(List.of(workedLoan(), later), 57_060L, strategy);

            assertEquals(35L, adjustmentFor(window, "loan-1"));
            assertEquals(List.of(
                    new ProjectedPayment(new PaymentAction("loan-1", 37_060L, START.plusDays(7)), PaymentKind.MINIMUM, 35L),
                    new ProjectedPayment(new PaymentAction("later", 20_000L, START.plusDays(11)), PaymentKind.MINIMUM, 0L)),
                    window.payments());
            assertEquals(new DebtBalance(35L, 0L), window.endingBalances().get("loan-1"));
            assertEquals(0L, window.unspentCents());
            assertEquals(0L, window.minimumShortfallCents());
            assertEquals(2, strategyInputs.size());
            assertEquals(List.of("loan-1"), strategyInputs.getLast().stream().map(Debt::id).toList());
        }

        @Test
        @DisplayName("An ordinary partial payment is not eligible for a payoff adjustment")
        void partialPaymentPlan_leavesUnusedCashAlone() {
            ProjectionDebt loan = loan("loan-1", 100_000L, 600, 15_000L, 13);
            List<List<Debt>> strategyInputs = new ArrayList<>();
            PayoffStrategy noExtras = (debts, cash) -> {
                strategyInputs.add(List.copyOf(debts));
                return Map.of();
            };

            ProjectionWindow window = project(List.of(loan), 40_000L, noExtras);

            assertEquals(15_000L, window.totalPaidCents());
            assertEquals(0L, adjustmentFor(window, "loan-1"));
            assertEquals(25_000L, window.unspentCents());
            assertEquals(1, strategyInputs.size());
        }

        @Test
        @DisplayName("A payoff split between payday and a later minimum adjusts only the final payment")
        void extraThenMinimum_adjustsOnFinalScheduledDate() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 13);

            ProjectionWindow window = project(List.of(loan), 40_000L, new AvalancheStrategy());

            assertEquals(List.of(
                    new ProjectedPayment(new PaymentAction("loan-1", 26_525L, START), PaymentKind.EXTRA, 0L),
                    new ProjectedPayment(new PaymentAction("loan-1", 10_019L, START.plusDays(7)), PaymentKind.MINIMUM, 19L)),
                    window.payments());
            assertEquals(Map.of("loan-1", 19L), window.interestChargedCents());
            assertEquals(new DebtBalance(0L, 0L), window.endingBalances().get("loan-1"));
        }

        @Test
        @DisplayName("Avalanche funds the higher APR payoff gap even when its debt ID sorts last")
        void competingPayoffs_avalancheFollowsApr() {
            ProjectionDebt lowerApr = loan("a", 36_525L, 500, 36_525L, 13);
            ProjectionDebt higherApr = loan("z", 14_610L, 3000, 14_610L, 13);

            ProjectionWindow window = project(List.of(lowerApr, higherApr), 51_185L, new AvalancheStrategy());

            assertEquals(0L, adjustmentFor(window, "a"));
            assertEquals(50L, adjustmentFor(window, "z"));
        }

        @Test
        @DisplayName("Snowball ranks payoff gaps once after all same-date payments rather than using payday balances")
        void competingPayoffs_snowballUsesUpdatedBalances() {
            ProjectionDebt smallerOnPayday = loan("a", 14_610L, 3000, 14_610L, 13);
            ProjectionDebt smallerAtPayoff = loan("z", 36_525L, 500, 36_525L, 13);
            List<List<Debt>> strategyInputs = new ArrayList<>();
            PayoffStrategy strategy = (debts, cash) -> {
                strategyInputs.add(List.copyOf(debts));
                return new SnowballStrategy().allocateExtra(debts, cash);
            };

            ProjectionWindow window = project(List.of(smallerOnPayday, smallerAtPayoff), 51_185L, strategy);

            assertEquals(15L, adjustmentFor(window, "a"));
            assertEquals(35L, adjustmentFor(window, "z"));
            assertEquals(2, strategyInputs.size());
            assertEquals(List.of(
                    new Debt("a", "Student loan a", DebtType.STUDENT_LOAN, 84L, 3000, 14_610L, 13, null),
                    new Debt("z", "Student loan z", DebtType.STUDENT_LOAN, 35L, 500, 36_525L, 13, null)),
                    strategyInputs.getLast());
        }

        @Test
        @DisplayName("CFI uses the stated minimum rather than the capped final payment")
        void competingPayoffs_cfiPreservesOriginalMinimum() {
            ProjectionDebt betterIndex = loan("a", 14_610L, 3000, 100_000L, 13);
            ProjectionDebt smallerGap = loan("z", 36_525L, 500, 36_525L, 13);

            ProjectionWindow window = project(List.of(betterIndex, smallerGap), 51_185L, new CashFlowIndexStrategy());

            assertEquals(50L, adjustmentFor(window, "a"));
            assertEquals(0L, adjustmentFor(window, "z"));
        }

        @Test
        @DisplayName("CFI evaluates the remaining gaps rather than freezing the payday ranking")
        void competingPayoffs_cfiUsesUpdatedBalances() {
            ProjectionDebt betterOnPayday = loan("a", 36_525L, 1000, 100_000L, 13);
            ProjectionDebt betterAtPayoff = loan("z", 14_610L, 400, 20_000L, 13);

            ProjectionWindow window = project(List.of(betterOnPayday, betterAtPayoff), 51_155L, new CashFlowIndexStrategy());

            assertEquals(9L, adjustmentFor(window, "a"));
            assertEquals(11L, adjustmentFor(window, "z"));
        }
    }

    @Nested
    @DisplayName("Invalid strategy adjustments")
    class InvalidAdjustments {
        @Test
        @DisplayName("Rejects an adjustment for an ineligible debt")
        void unknownAdjustmentDebt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> project(List.of(workedLoan()), 40_000L,
                    adjustmentStrategy(Map.of("unknown-loan", 1L))));
        }

        @Test
        @DisplayName("Rejects a zero adjustment")
        void zeroAdjustment_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> project(List.of(workedLoan()), 40_000L,
                    adjustmentStrategy(Map.of("loan-1", 0L))));
        }

        @Test
        @DisplayName("Rejects a negative adjustment")
        void negativeAdjustment_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> project(List.of(workedLoan()), 40_000L,
                    adjustmentStrategy(Map.of("loan-1", -1L))));
        }

        @Test
        @DisplayName("Rejects an adjustment one cent above the remaining debt")
        void adjustmentExceedsBalance_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> project(List.of(workedLoan()), 40_000L,
                    adjustmentStrategy(Map.of("loan-1", 71L))));
        }

        @Test
        @DisplayName("Rejects an adjustment one cent above spare cash")
        void adjustmentExceedsSpareCash_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> project(List.of(workedLoan()), 37_060L,
                    adjustmentStrategy(Map.of("loan-1", 36L))));
        }

        @Test
        @DisplayName("Validates the combined adjustment budget before applying any adjustment")
        void combinedAdjustmentsExceedCash_appliesNeitherAdjustment() {
            ProjectionDebt first = loan("a", 36_525L, 1000, 36_525L, 13);
            ProjectionDebt second = loan("z", 36_525L, 1000, 36_525L, 13);
            Map<String, DebtInterestState> states = statesFor(first, second);

            assertThrows(IllegalArgumentException.class, () -> projector.project(
                    List.of(first.debt(), second.debt()), states, budgetWithAvailable(73_100L), START,
                    adjustmentStrategy(Map.of("a", 30L, "z", 30L))));
            assertEquals(new DebtBalance(70L, 0L), states.get("a").balance());
            assertEquals(new DebtBalance(70L, 0L), states.get("z").balance());
        }

        @Test
        @DisplayName("Rejects a missing adjustment map")
        void nullAdjustments_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> project(List.of(workedLoan()), 40_000L,
                    adjustmentStrategy(null)));
        }
    }

    @Nested
    @DisplayName("Input validation and boundaries")
    class Boundaries {
        @Test
        @DisplayName("Reordering input debts leaves the projected window unchanged")
        void reversedDebtInputs_produceSameWindow() {
            ProjectionDebt loan = loan("loan-1", 36_525L, 1000, 10_000L, 13);
            ProjectionDebt card = creditCard("card-1", 150_000L, 2400, 5_000L, 17);

            ProjectionWindow forward = project(List.of(loan, card), 40_000L, new AvalancheStrategy());
            ProjectionWindow reversed = project(List.of(card, loan), 40_000L, new AvalancheStrategy());

            assertEquals(forward, reversed);
        }

        @Test
        @DisplayName("Rejects missing debt terms")
        void nullDebts_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(null, Map.of(),
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects missing interest states")
        void nullStates_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), null,
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a missing budget")
        void nullBudget_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> projector.project(List.of(), Map.of(), null, START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a missing window start")
        void nullWindowStart_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projector.project(List.of(), Map.of(),
                    budgetWithAvailable(40_000L), null, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a missing strategy")
        void nullStrategy_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> projector.project(List.of(), Map.of(), budgetWithAvailable(40_000L), START, null));
        }

        @Test
        @DisplayName("Rejects a null debt entry")
        void nullDebtEntry_throwsNullPointerException() {
            List<Debt> debts = new ArrayList<>();
            debts.add(null);

            assertThrows(NullPointerException.class, () -> projector.project(debts, Map.of(),
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a null interest state")
        void nullStateEntry_throwsNullPointerException() {
            Map<String, DebtInterestState> states = new HashMap<>();
            states.put("loan-1", null);

            assertThrows(NullPointerException.class, () -> projector.project(List.of(workedLoan().debt()), states,
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects duplicate debt IDs")
        void duplicateDebtIds_throwsIllegalArgumentException() {
            ProjectionDebt loan = workedLoan();

            assertThrows(IllegalArgumentException.class, () -> projector.project(
                    List.of(loan.debt(), loan.debt()), statesFor(loan),
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects debt terms with no matching interest state")
        void missingDebtState_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projector.project(List.of(workedLoan().debt()), Map.of(),
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects an interest state with no matching debt terms")
        void additionalDebtState_throwsIllegalArgumentException() {
            ProjectionDebt loan = workedLoan();
            ProjectionDebt extra = loan("extra", 100_000L, 600, 15_000L, 13);

            assertThrows(IllegalArgumentException.class, () -> projector.project(List.of(loan.debt()),
                    statesFor(loan, extra), budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects two debts sharing one mutable interest state")
        void sharedInterestState_throwsIllegalArgumentException() {
            ProjectionDebt first = loan("a", 100_000L, 600, 15_000L, 13);
            ProjectionDebt second = loan("z", 100_000L, 600, 15_000L, 13);
            DebtInterestState shared = new DebtInterestState(first);

            assertThrows(IllegalArgumentException.class, () -> projector.project(
                    List.of(first.debt(), second.debt()), Map.of("a", shared, "z", shared),
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a budget gap that cannot be represented as long cents")
        void availableCashUnderflows_throwsArithmeticException() {
            Budget budget = new Budget(0L, Long.MAX_VALUE, Long.MAX_VALUE);

            assertThrows(ArithmeticException.class,
                    () -> projector.project(List.of(), Map.of(), budget, START, new AvalancheStrategy()));
        }

        @Test
        @DisplayName("Rejects a minimum total that exceeds the long range before applying payments")
        void totalMinimumsOverflow_throwsArithmeticException() {
            ProjectionDebt first = loan("a", Long.MAX_VALUE, 0, Long.MAX_VALUE, 13);
            ProjectionDebt second = loan("z", Long.MAX_VALUE, 0, Long.MAX_VALUE, 13);
            Map<String, DebtInterestState> states = statesFor(first, second);

            assertThrows(ArithmeticException.class, () -> projector.project(
                    List.of(first.debt(), second.debt()), states,
                    budgetWithAvailable(40_000L), START, new AvalancheStrategy()));
            assertEquals(new DebtBalance(Long.MAX_VALUE, 0L), states.get("a").balance());
            assertEquals(new DebtBalance(Long.MAX_VALUE, 0L), states.get("z").balance());
        }

        @Test
        @DisplayName("Rejects a window whose end is beyond the supported date range")
        void windowEndOverflows_throwsDateTimeException() {
            assertThrows(DateTimeException.class, () -> projector.project(List.of(), Map.of(),
                    budgetWithAvailable(40_000L), LocalDate.MAX.minusDays(13), new AvalancheStrategy()));
        }
    }

    private ProjectionWindow project(List<ProjectionDebt> inputs, long available, PayoffStrategy strategy) {
        return projector.project(inputs.stream().map(ProjectionDebt::debt).toList(),
                statesFor(inputs.toArray(ProjectionDebt[]::new)), budgetWithAvailable(available), START, strategy);
    }

    private static long adjustmentFor(ProjectionWindow window, String debtId) {
        return window.payments().stream().filter(payment -> payment.action().debtId().equals(debtId))
                .mapToLong(ProjectedPayment::payoffAdjustmentCents).sum();
    }

    private static PayoffStrategy adjustmentStrategy(Map<String, Long> adjustments) {
        return (debts, cash) -> debts.stream().allMatch(debt -> debt.balanceCents() == 0) ? Map.of() : adjustments;
    }

    private static ProjectionDebt workedLoan() {
        Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                37_025L, 1000, 40_000L, 13, null);
        return new ProjectionDebt(debt, 500L);
    }

    private static ProjectionDebt loan(String id, long balance, int apr, long minimum, int dueDay) {
        return new ProjectionDebt(new Debt(id, "Student loan " + id, DebtType.STUDENT_LOAN,
                balance, apr, minimum, dueDay, null), 0L);
    }

    private static ProjectionDebt creditCard(String id, long balance, int apr, long minimum, int dueDay) {
        return new ProjectionDebt(new Debt(id, "Credit card " + id, DebtType.CREDIT_CARD,
                balance, apr, minimum, dueDay, 500_000L));
    }

    private static Map<String, DebtInterestState> statesFor(ProjectionDebt... inputs) {
        Map<String, DebtInterestState> states = new HashMap<>();
        for (ProjectionDebt input : inputs) {
            states.put(input.debt().id(), new DebtInterestState(input));
        }
        return states;
    }

    private static Budget budgetWithAvailable(long available) {
        return new Budget(200_000L, 190_000L - available, 10_000L);
    }
}
