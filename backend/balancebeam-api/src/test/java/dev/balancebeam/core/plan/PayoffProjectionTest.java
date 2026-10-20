package dev.balancebeam.core.plan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.ProjectionOptions;

@DisplayName("PayoffProjection")
class PayoffProjectionTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 6);
    private static final ProjectionAssumptions NO_ASSUMPTIONS =
            new ProjectionAssumptions("biweekly-v1", Set.of());

    @Nested
    @DisplayName("Completed forecasts and summaries")
    class Summaries {
        @Test
        @DisplayName("Sums payments across debts and windows with each adjustment counted once")
        void multipleWindows_totalPaidIncludesAllActualPayments() {
            PayoffProjection projection = twoWindowPayoff();

            assertEquals(97_995L, projection.totalPaidCents());
        }

        @Test
        @DisplayName("Sums new interest across windows without counting opening unpaid interest")
        void openingUnpaidInterest_totalInterestIncludesOnlyNewCharges() {
            PayoffProjection projection = twoWindowPayoff();

            assertEquals(970L, projection.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Returns balances from the final window")
        void multipleWindows_endingBalancesComeFromLastWindow() {
            PayoffProjection projection = twoWindowPayoff();

            assertEquals(Map.of("loan-1", new DebtBalance(0L, 0L),
                    "card-1", new DebtBalance(0L, 0L)), projection.endingBalances());
        }

        @Test
        @DisplayName("Reports no minimum shortfall when every window is funded")
        void fullyFundedWindows_reportsNoShortfall() {
            assertFalse(twoWindowPayoff().hasMinimumShortfall());
        }

        @Test
        @DisplayName("Retains an earlier minimum shortfall after eventual payoff")
        void earlierShortfallAndLaterPayoff_reportsShortfall() {
            ProjectionWindow first = new ProjectionWindow(START, START.plusDays(14),
                    List.of(minimumPayment("loan-1", 20_000L, START.plusDays(7), 0L)),
                    Map.of("loan-1", 0L), loanBalances(30_000L, 0L),
                    20_000L, 10_000L, 25_000L, 5_000L);
            ProjectionWindow second = loanWindow(START.plusDays(14), 0L, new DebtBalance(0L, 0L),
                    extraPayment("loan-1", 30_000L, START.plusDays(14)));
            PayoffProjection projection = projection(loanBalances(50_000L, 0L), List.of(first, second),
                    ProjectionStatus.PAID_OFF, START.plusDays(14), 780);

            assertTrue(projection.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Returns zero totals and no ending debts for an empty portfolio")
        void emptyPortfolio_returnsEmptyForecast() {
            PayoffProjection projection = projection(Map.of(), List.of(), ProjectionStatus.PAID_OFF, START, 780);

            assertEquals(0L, projection.totalPaidCents());
            assertEquals(0L, projection.totalInterestChargedCents());
            assertEquals(Map.of(), projection.endingBalances());
            assertFalse(projection.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Retains already-paid-off debts without creating windows")
        void initiallyPaidOff_endingBalancesComeFromOpeningBalances() {
            PayoffProjection projection = projection(loanBalances(0L, 0L), List.of(),
                    ProjectionStatus.PAID_OFF, START, 780);

            assertEquals(loanBalances(0L, 0L), projection.endingBalances());
            assertEquals(0L, projection.totalPaidCents());
            assertEquals(0L, projection.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Accepts payoff on the final allowed window")
        void payoffAtWindowLimit_constructsSuccessfully() {
            assertDoesNotThrow(() -> projection(loanBalances(50_000L, 0L), List.of(payoffWindow()),
                    ProjectionStatus.PAID_OFF, START, 1));
        }

        @Test
        @DisplayName("Reports interest still unpaid when the forecast limit is reached")
        void horizonWithUnpaidInterest_includesChargesAndRemainingBalance() {
            ProjectionWindow window = loanWindow(START, 1_500L, new DebtBalance(100_000L, 1_500L));
            PayoffProjection projection = projection(loanBalances(100_000L, 0L), List.of(window),
                    ProjectionStatus.HORIZON_REACHED, null, 1);

            assertEquals(1_500L, projection.totalInterestChargedCents());
            assertEquals(0L, projection.totalPaidCents());
            assertEquals(loanBalances(100_000L, 1_500L), projection.endingBalances());
        }

        @Test
        @DisplayName("Accepts assumptions for a subset of the portfolio's debts")
        void knownAssumedDebtId_constructsSuccessfully() {
            Map<String, DebtBalance> balances = Map.of(
                    "loan-1", new DebtBalance(100_000L, 0L), "card-1", new DebtBalance(150_000L, 0L));
            ProjectionWindow window = new ProjectionWindow(START, START.plusDays(14), List.of(),
                    Map.of("loan-1", 0L, "card-1", 0L), balances, 40_000L, 10_000L, 0L, 0L);

            assertDoesNotThrow(() -> new PayoffProjection(START, balances, List.of(window),
                    ProjectionStatus.HORIZON_REACHED, null, new ProjectionOptions(1),
                    new ProjectionAssumptions("biweekly-v1", Set.of("loan-1"))));
        }
    }

    @Nested
    @DisplayName("Completion status and payoff dates")
    class Completion {
        @Test
        @DisplayName("Rejects a paid-off status while debt remains")
        void paidOffWithRemainingDebt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START)), ProjectionStatus.PAID_OFF, START, 1));
        }

        @Test
        @DisplayName("Rejects a missing payoff date for a paid-off forecast")
        void paidOffWithoutDate_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projection(loanBalances(50_000L, 0L),
                    List.of(payoffWindow()), ProjectionStatus.PAID_OFF, null, 1));
        }

        @Test
        @DisplayName("Rejects a payoff date different from the final payment date")
        void incorrectPayoffDate_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(50_000L, 0L),
                    List.of(payoffWindow()), ProjectionStatus.PAID_OFF, START.plusDays(1), 1));
        }

        @Test
        @DisplayName("Rejects a debt-free payoff date different from the start date")
        void debtFreeWithDifferentPayoffDate_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(Map.of(), List.of(),
                    ProjectionStatus.PAID_OFF, START.plusDays(1), 780));
        }

        @Test
        @DisplayName("Rejects a horizon status when every debt is paid off")
        void horizonWithZeroBalances_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(50_000L, 0L),
                    List.of(payoffWindow()), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects a horizon status before the configured window limit")
        void horizonBeforeLimit_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START)), ProjectionStatus.HORIZON_REACHED, null, 2));
        }

        @Test
        @DisplayName("Rejects an unprojected outstanding debt labeled as reaching the limit")
        void horizonWithNoWindows_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects a payoff date for a forecast with debt remaining at the limit")
        void horizonWithPayoffDate_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START)), ProjectionStatus.HORIZON_REACHED, START, 1));
        }

        @Test
        @DisplayName("Rejects more windows than the configured limit")
        void tooManyWindows_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START), unchangedWindow(START.plusDays(14))),
                    ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects extra windows after the portfolio is paid off")
        void windowAfterPayoff_throwsIllegalArgumentException() {
            ProjectionWindow extraWindow = loanWindow(START.plusDays(14), 0L, new DebtBalance(0L, 0L));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(50_000L, 0L),
                    List.of(payoffWindow(), extraWindow), ProjectionStatus.PAID_OFF, START, 2));
        }

        @Test
        @DisplayName("Rejects windows for an initially paid-off portfolio")
        void initiallyPaidOffWithWindow_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(0L, 0L));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(0L, 0L),
                    List.of(window), ProjectionStatus.PAID_OFF, START, 1));
        }
    }

    @Nested
    @DisplayName("Window continuity and balance reconciliation")
    class Reconciliation {
        @Test
        @DisplayName("Rejects a first window that does not begin on the projection start date")
        void firstWindowStartsLate_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START.plusDays(1))), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects a gap between windows")
        void gapBetweenWindows_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START), unchangedWindow(START.plusDays(15))),
                    ProjectionStatus.HORIZON_REACHED, null, 2));
        }

        @Test
        @DisplayName("Rejects overlapping windows")
        void overlappingWindows_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START), unchangedWindow(START.plusDays(13))),
                    ProjectionStatus.HORIZON_REACHED, null, 2));
        }

        @Test
        @DisplayName("Rejects a debt omitted from a window")
        void missingWindowDebt_throwsIllegalArgumentException() {
            Map<String, DebtBalance> opening = Map.of("loan-1", new DebtBalance(100_000L, 0L),
                    "card-1", new DebtBalance(150_000L, 0L));

            assertThrows(IllegalArgumentException.class, () -> projection(opening,
                    List.of(unchangedWindow(START)), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects a new debt appearing in a later window")
        void additionalWindowDebt_throwsIllegalArgumentException() {
            ProjectionWindow second = new ProjectionWindow(START.plusDays(14), START.plusDays(28), List.of(),
                    Map.of("loan-1", 0L, "card-1", 0L),
                    Map.of("loan-1", new DebtBalance(100_000L, 0L), "card-1", new DebtBalance(150_000L, 0L)),
                    40_000L, 10_000L, 0L, 0L);

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(unchangedWindow(START), second), ProjectionStatus.HORIZON_REACHED, null, 2));
        }

        @Test
        @DisplayName("Rejects a balance one cent away from the reconciled amount")
        void endingBalanceOffByOneCent_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 1_500L, new DebtBalance(80_000L, 1_501L),
                    extraPayment("loan-1", 20_000L, START));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Reconciles later windows against the previous ending balance")
        void laterWindowUsesWrongOpeningBalance_throwsIllegalArgumentException() {
            ProjectionWindow first = loanWindow(START, 0L, new DebtBalance(80_000L, 0L),
                    extraPayment("loan-1", 20_000L, START));
            ProjectionWindow second = loanWindow(START.plusDays(14), 0L, new DebtBalance(80_000L, 0L),
                    extraPayment("loan-1", 20_000L, START.plusDays(14)));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(first, second), ProjectionStatus.HORIZON_REACHED, null, 2));
        }

        @Test
        @DisplayName("Rejects offsetting errors that leave the portfolio total unchanged")
        void perDebtErrorsCancelOut_throwsIllegalArgumentException() {
            Map<String, DebtBalance> opening = Map.of("loan-1", new DebtBalance(100_000L, 0L),
                    "card-1", new DebtBalance(150_000L, 0L));
            ProjectionWindow window = new ProjectionWindow(START, START.plusDays(14),
                    List.of(extraPayment("loan-1", 20_000L, START)), Map.of("loan-1", 0L, "card-1", 0L),
                    Map.of("loan-1", new DebtBalance(90_000L, 0L), "card-1", new DebtBalance(140_000L, 0L)),
                    40_000L, 10_000L, 0L, 0L);

            assertThrows(IllegalArgumentException.class, () -> projection(opening,
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Combines multiple payments to the same debt when reconciling")
        void multiplePaymentsToSameDebt_constructsSuccessfully() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(50_000L, 0L),
                    extraPayment("loan-1", 20_000L, START),
                    extraPayment("loan-1", 30_000L, START.plusDays(7)));

            assertDoesNotThrow(() -> projection(loanBalances(100_000L, 0L), List.of(window),
                    ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects interest charged to a debt already at zero")
        void paidOffDebtAccruesInterest_throwsIllegalArgumentException() {
            Map<String, DebtBalance> opening = Map.of("loan-1", new DebtBalance(100_000L, 0L),
                    "card-1", new DebtBalance(0L, 0L));
            ProjectionWindow window = new ProjectionWindow(START, START.plusDays(14), List.of(),
                    Map.of("loan-1", 0L, "card-1", 70L),
                    Map.of("loan-1", new DebtBalance(100_000L, 0L), "card-1", new DebtBalance(0L, 70L)),
                    40_000L, 10_000L, 0L, 0L);

            assertThrows(IllegalArgumentException.class, () -> projection(opening,
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects charging and paying interest on a paid-off debt even when it nets to zero")
        void paidOffDebtReceivesPayment_throwsIllegalArgumentException() {
            Map<String, DebtBalance> opening = Map.of("loan-1", new DebtBalance(100_000L, 0L),
                    "card-1", new DebtBalance(0L, 0L));
            ProjectionWindow window = new ProjectionWindow(START, START.plusDays(14),
                    List.of(extraPayment("card-1", 70L, START)),
                    Map.of("loan-1", 0L, "card-1", 70L), opening, 40_000L, 10_000L, 0L, 0L);

            assertThrows(IllegalArgumentException.class, () -> projection(opening,
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }
    }

    @Nested
    @DisplayName("Principal and interest allocation boundaries")
    class BalanceComponents {
        @Test
        @DisplayName("Rejects opening interest moved into principal without activity")
        void interestMovedIntoPrincipal_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(101_000L, 0L));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects principal moved into interest without a payment")
        void principalReducedWithoutPayment_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(99_999L, 1L));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 0L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Accepts principal reduced by exactly the payment left after opening interest")
        void principalReductionEqualsAvailablePayment_constructsSuccessfully() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(96_000L, 0L),
                    extraPayment("loan-1", 5_000L, START));

            assertDoesNotThrow(() -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects principal reduction one cent beyond the payment left after opening interest")
        void principalReductionExceedsAvailablePayment_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(95_999L, 1L),
                    extraPayment("loan-1", 5_000L, START));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Accepts a payment that covers only part of the opening interest")
        void paymentBelowOpeningInterest_constructsSuccessfully() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(100_000L, 500L),
                    extraPayment("loan-1", 500L, START));

            assertDoesNotThrow(() -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Rejects principal reduction while opening interest remains uncovered")
        void paymentBelowOpeningInterestReducesPrincipal_throwsIllegalArgumentException() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(99_999L, 501L),
                    extraPayment("loan-1", 500L, START));

            assertThrows(IllegalArgumentException.class, () -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Accepts a payment that exactly clears opening interest without reducing principal")
        void paymentEqualsOpeningInterest_constructsSuccessfully() {
            ProjectionWindow window = loanWindow(START, 0L, new DebtBalance(100_000L, 0L),
                    extraPayment("loan-1", 1_000L, START));

            assertDoesNotThrow(() -> projection(loanBalances(100_000L, 1_000L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Accepts reduced principal with new interest remaining after a payday payment")
        void principalPaymentWithLaterInterest_constructsSuccessfully() {
            ProjectionWindow window = loanWindow(START, 1_000L, new DebtBalance(90_000L, 1_000L),
                    extraPayment("loan-1", 10_000L, START));

            assertDoesNotThrow(() -> projection(loanBalances(100_000L, 0L),
                    List.of(window), ProjectionStatus.HORIZON_REACHED, null, 1));
        }
    }

    @Nested
    @DisplayName("Required inputs and assumption IDs")
    class Inputs {
        @Test
        @DisplayName("Rejects a missing projection start date")
        void nullStartDate_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> new PayoffProjection(null, Map.of(), List.of(),
                    ProjectionStatus.PAID_OFF, START, new ProjectionOptions(), NO_ASSUMPTIONS));
        }

        @Test
        @DisplayName("Rejects missing opening balances")
        void nullOpeningBalances_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> projection(null, List.of(), ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects a missing window list")
        void nullWindows_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> projection(Map.of(), null, ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects a missing completion status")
        void nullStatus_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> projection(Map.of(), List.of(), null, START, 780));
        }

        @Test
        @DisplayName("Rejects missing options")
        void nullOptions_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> new PayoffProjection(START, Map.of(), List.of(),
                    ProjectionStatus.PAID_OFF, START, null, NO_ASSUMPTIONS));
        }

        @Test
        @DisplayName("Rejects missing assumptions")
        void nullAssumptions_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> new PayoffProjection(START, Map.of(), List.of(),
                    ProjectionStatus.PAID_OFF, START, new ProjectionOptions(), null));
        }

        @Test
        @DisplayName("Rejects a null entry in the window list")
        void nullWindowEntry_throwsNullPointerException() {
            List<ProjectionWindow> windows = new ArrayList<>();
            windows.add(null);

            assertThrows(NullPointerException.class,
                    () -> projection(Map.of(), windows, ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects a null opening debt ID")
        void nullOpeningDebtId_throwsNullPointerException() {
            Map<String, DebtBalance> opening = new HashMap<>();
            opening.put(null, new DebtBalance(0L, 0L));

            assertThrows(NullPointerException.class,
                    () -> projection(opening, List.of(), ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects an empty opening debt ID")
        void emptyOpeningDebtId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(
                    Map.of("", new DebtBalance(0L, 0L)), List.of(), ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects a whitespace-only opening debt ID")
        void whitespaceOpeningDebtId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> projection(
                    Map.of("   ", new DebtBalance(0L, 0L)), List.of(), ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects a null opening balance")
        void nullOpeningBalance_throwsNullPointerException() {
            Map<String, DebtBalance> opening = new HashMap<>();
            opening.put("loan-1", null);

            assertThrows(NullPointerException.class,
                    () -> projection(opening, List.of(), ProjectionStatus.PAID_OFF, START, 780));
        }

        @Test
        @DisplayName("Rejects an assumption referencing a debt outside the portfolio")
        void unknownAssumedDebtId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new PayoffProjection(START,
                    loanBalances(100_000L, 0L), List.of(unchangedWindow(START)),
                    ProjectionStatus.HORIZON_REACHED, null, new ProjectionOptions(1),
                    new ProjectionAssumptions("biweekly-v1", Set.of("unknown-loan"))));
        }
    }

    @Nested
    @DisplayName("Collection ownership and ordering")
    class Collections {
        @Test
        @DisplayName("Copies opening balances independently of the caller's map")
        void sourceOpeningBalancesChanged_projectionRetainsSnapshot() {
            Map<String, DebtBalance> opening = new HashMap<>(loanBalances(100_000L, 0L));
            PayoffProjection projection = projection(opening, List.of(unchangedWindow(START)),
                    ProjectionStatus.HORIZON_REACHED, null, 1);

            opening.put("loan-1", new DebtBalance(0L, 0L));

            assertEquals(loanBalances(100_000L, 0L), projection.openingBalances());
        }

        @Test
        @DisplayName("Copies windows independently of the caller's list")
        void sourceWindowsChanged_projectionRetainsSnapshot() {
            ProjectionWindow window = payoffWindow();
            List<ProjectionWindow> windows = new ArrayList<>(List.of(window));
            PayoffProjection projection = projection(loanBalances(50_000L, 0L), windows,
                    ProjectionStatus.PAID_OFF, START, 1);

            windows.clear();

            assertEquals(List.of(window), projection.windows());
            assertEquals(50_000L, projection.totalPaidCents());
        }

        @Test
        @DisplayName("Prevents changes through the returned opening balance map")
        void returnedOpeningBalancesModified_throwsUnsupportedOperationException() {
            PayoffProjection projection = twoWindowPayoff();

            assertThrows(UnsupportedOperationException.class, () -> projection.openingBalances().clear());
        }

        @Test
        @DisplayName("Prevents changes through the returned window list")
        void returnedWindowsModified_throwsUnsupportedOperationException() {
            PayoffProjection projection = twoWindowPayoff();

            assertThrows(UnsupportedOperationException.class, () -> projection.windows().clear());
        }

        @Test
        @DisplayName("Prevents changes through ending balances from the final window")
        void returnedEndingBalancesModified_throwsUnsupportedOperationException() {
            PayoffProjection projection = twoWindowPayoff();

            assertThrows(UnsupportedOperationException.class, () -> projection.endingBalances().clear());
        }

        @Test
        @DisplayName("Prevents changes through ending balances when there are no windows")
        void debtFreeEndingBalancesModified_throwsUnsupportedOperationException() {
            PayoffProjection projection = projection(loanBalances(0L, 0L), List.of(),
                    ProjectionStatus.PAID_OFF, START, 780);

            assertThrows(UnsupportedOperationException.class, () -> projection.endingBalances().clear());
        }

        @Test
        @DisplayName("Iterates opening balances in debt ID order regardless of insertion order")
        void reverseInsertedOpeningBalances_iterateInSortedOrder() {
            Map<String, DebtBalance> opening = new LinkedHashMap<>();
            opening.put("loan-2", new DebtBalance(0L, 0L));
            opening.put("loan-10", new DebtBalance(0L, 0L));
            opening.put("loan-1", new DebtBalance(0L, 0L));
            PayoffProjection projection = projection(opening, List.of(), ProjectionStatus.PAID_OFF, START, 780);

            assertEquals(List.of("loan-1", "loan-10", "loan-2"),
                    new ArrayList<>(projection.openingBalances().keySet()));
        }
    }

    @Nested
    @DisplayName("Large-number boundaries")
    class Overflow {
        @Test
        @DisplayName("Reconciles valid net changes even when opening balance plus interest exceeds long range")
        void largeOpeningBalanceAndInterest_constructsSuccessfully() {
            ProjectionWindow window = largeWindow(START, 2_000L, 1_500L,
                    new DebtBalance(Long.MAX_VALUE - 500L, 0L));

            assertDoesNotThrow(() -> projection(loanBalances(Long.MAX_VALUE, 0L), List.of(window),
                    ProjectionStatus.HORIZON_REACHED, null, 1));
        }

        @Test
        @DisplayName("Returns a payment total exactly at the largest long value across windows")
        void totalPaidEqualsLongMaximum_returnsExactTotal() {
            ProjectionWindow first = largeWindow(START, Long.MAX_VALUE - 15_000L, 0L,
                    new DebtBalance(15_000L, 0L));
            ProjectionWindow second = largeWindow(START.plusDays(14), 15_000L, 0L,
                    new DebtBalance(0L, 0L));
            PayoffProjection projection = projection(loanBalances(Long.MAX_VALUE, 0L), List.of(first, second),
                    ProjectionStatus.PAID_OFF, START.plusDays(21), 2);

            assertEquals(Long.MAX_VALUE, projection.totalPaidCents());
        }

        @Test
        @DisplayName("Rejects a combined payment total one cent beyond the largest long value")
        void totalPaidExceedsLongMaximum_throwsArithmeticException() {
            ProjectionWindow first = largeWindow(START, Long.MAX_VALUE - 15_000L, 0L,
                    new DebtBalance(15_000L, 0L));
            ProjectionWindow second = largeWindow(START.plusDays(14), 15_001L, 1L,
                    new DebtBalance(0L, 0L));
            PayoffProjection projection = projection(loanBalances(Long.MAX_VALUE, 0L), List.of(first, second),
                    ProjectionStatus.PAID_OFF, START.plusDays(21), 2);

            assertThrows(ArithmeticException.class, projection::totalPaidCents);
        }

        @Test
        @DisplayName("Returns an interest total exactly at the largest long value across windows")
        void totalInterestEqualsLongMaximum_returnsExactTotal() {
            DebtBalance ending = new DebtBalance(10_000L, Long.MAX_VALUE - 25_000L);
            ProjectionWindow first = largeWindow(START, 10_000L, Long.MAX_VALUE - 15_000L, ending);
            ProjectionWindow second = largeWindow(START.plusDays(14), 15_000L, 15_000L, ending);
            PayoffProjection projection = projection(loanBalances(10_000L, 0L), List.of(first, second),
                    ProjectionStatus.HORIZON_REACHED, null, 2);

            assertEquals(Long.MAX_VALUE, projection.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Rejects a combined interest total one cent beyond the largest long value")
        void totalInterestExceedsLongMaximum_throwsArithmeticException() {
            DebtBalance ending = new DebtBalance(10_000L, Long.MAX_VALUE - 25_000L);
            ProjectionWindow first = largeWindow(START, 10_000L, Long.MAX_VALUE - 15_000L, ending);
            ProjectionWindow second = largeWindow(START.plusDays(14), 15_001L, 15_001L, ending);
            PayoffProjection projection = projection(loanBalances(10_000L, 0L), List.of(first, second),
                    ProjectionStatus.HORIZON_REACHED, null, 2);

            assertThrows(ArithmeticException.class, projection::totalInterestChargedCents);
        }
    }

    private static PayoffProjection twoWindowPayoff() {
        Map<String, DebtBalance> opening = Map.of("loan-1", new DebtBalance(36_525L, 500L),
                "card-1", new DebtBalance(60_000L, 0L));
        ProjectionWindow first = new ProjectionWindow(START, START.plusDays(14), List.of(
                extraPayment("card-1", 20_000L, START),
                minimumPayment("loan-1", 37_095L, START.plusDays(7), 70L)),
                Map.of("loan-1", 70L, "card-1", 600L),
                Map.of("loan-1", new DebtBalance(0L, 0L), "card-1", new DebtBalance(40_000L, 600L)),
                60_000L, 10_000L, 37_025L, 0L);
        ProjectionWindow second = new ProjectionWindow(START.plusDays(14), START.plusDays(28),
                List.of(minimumPayment("card-1", 40_900L, START.plusDays(21), 300L)),
                Map.of("loan-1", 0L, "card-1", 300L),
                Map.of("loan-1", new DebtBalance(0L, 0L), "card-1", new DebtBalance(0L, 0L)),
                60_000L, 10_000L, 40_600L, 0L);
        return projection(opening, List.of(first, second), ProjectionStatus.PAID_OFF, START.plusDays(21), 780);
    }

    private static PayoffProjection projection(Map<String, DebtBalance> opening, List<ProjectionWindow> windows,
            ProjectionStatus status, LocalDate payoffDate, int maxWindows) {
        return new PayoffProjection(START, opening, windows, status, payoffDate,
                new ProjectionOptions(maxWindows), NO_ASSUMPTIONS);
    }

    private static Map<String, DebtBalance> loanBalances(long principalCents, long interestCents) {
        return Map.of("loan-1", new DebtBalance(principalCents, interestCents));
    }

    private static ProjectedPayment extraPayment(String debtId, long amountCents, LocalDate date) {
        return new ProjectedPayment(new PaymentAction(debtId, amountCents, date), PaymentKind.EXTRA, 0L);
    }

    private static ProjectedPayment minimumPayment(String debtId, long amountCents, LocalDate date, long adjustment) {
        return new ProjectedPayment(new PaymentAction(debtId, amountCents, date), PaymentKind.MINIMUM, adjustment);
    }

    private static ProjectionWindow loanWindow(LocalDate start, long interest, DebtBalance ending,
            ProjectedPayment... payments) {
        return new ProjectionWindow(start, start.plusDays(14), List.of(payments),
                Map.of("loan-1", interest), Map.of("loan-1", ending), 50_000L, 10_000L, 0L, 0L);
    }

    private static ProjectionWindow unchangedWindow(LocalDate start) {
        return loanWindow(start, 0L, new DebtBalance(100_000L, 0L));
    }

    private static ProjectionWindow payoffWindow() {
        return loanWindow(START, 0L, new DebtBalance(0L, 0L), extraPayment("loan-1", 50_000L, START));
    }

    private static ProjectionWindow largeWindow(LocalDate start, long payment, long interest, DebtBalance ending) {
        return new ProjectionWindow(start, start.plusDays(14),
                List.of(minimumPayment("loan-1", payment, start.plusDays(7), 0L)),
                Map.of("loan-1", interest), Map.of("loan-1", ending), Long.MAX_VALUE, 10_000L, payment, 0L);
    }
}
