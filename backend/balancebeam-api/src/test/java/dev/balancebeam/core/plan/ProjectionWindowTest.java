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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectionWindow")
class ProjectionWindowTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 6);
    private static final LocalDate END = LocalDate.of(2026, 10, 20);

    @Nested
    @DisplayName("Cash and interest summaries")
    class Summaries {
        @Test
        @DisplayName("Counts a payoff adjustment once in the actual payment total")
        void paymentWithAdjustment_totalIncludesAdjustmentOnce() {
            ProjectedPayment payment = new ProjectedPayment(
                    new PaymentAction("loan-1", 37_095L, START.plusDays(7)), PaymentKind.MINIMUM, 70L);
            ProjectionWindow window = new ProjectionWindow(START, END, List.of(payment),
                    Map.of("loan-1", 70L), Map.of("loan-1", new DebtBalance(0L, 0L)),
                    40_000L, 10_000L, 37_025L, 0L);

            assertEquals(37_095L, window.totalPaidCents());
        }

        @Test
        @DisplayName("Sums multiple payments across debts")
        void multiplePayments_totalIncludesEveryPayment() {
            ProjectionWindow window = windowWithPayments(List.of(
                    payment("loan-1", 15_000L, START), payment("card-1", 25_000L, START)), 50_000L);

            assertEquals(40_000L, window.totalPaidCents());
        }

        @Test
        @DisplayName("Leaves unused available cash without subtracting the buffer twice")
        void paymentBelowAvailable_unspentExcludesOnlyActualPayment() {
            ProjectionWindow window = windowWithPayments(
                    List.of(payment("loan-1", 37_095L, START)), 40_000L);

            assertEquals(2_905L, window.unspentCents());
        }

        @Test
        @DisplayName("Allows payments to use exactly all available cash")
        void paymentsEqualAvailable_unspentIsZero() {
            ProjectionWindow window = windowWithPayments(List.of(
                    payment("loan-1", 15_000L, START), payment("card-1", 25_000L, START)), 40_000L);

            assertEquals(0L, window.unspentCents());
        }

        @Test
        @DisplayName("Reports no payment or interest totals for an empty portfolio")
        void emptyPortfolio_totalsAreZero() {
            ProjectionWindow window = windowWithMaps(Map.of(), Map.of());

            assertEquals(0L, window.totalPaidCents());
            assertEquals(0L, window.totalInterestChargedCents());
            assertEquals(40_000L, window.unspentCents());
        }

        @Test
        @DisplayName("Includes interest charges that remain unpaid across debts")
        void unpaidInterest_totalIncludesAllNewCharges() {
            ProjectionWindow window = windowWithMaps(
                    Map.of("loan-1", 1_500L, "card-1", 2_300L),
                    Map.of("loan-1", new DebtBalance(980_000L, 21_500L),
                            "card-1", new DebtBalance(150_000L, 2_300L)));

            assertEquals(3_800L, window.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Reports zero unspent cash when available cash is zero")
        void zeroAvailableCash_unspentIsZero() {
            ProjectionWindow window = windowWithPayments(List.of(), 0L);

            assertEquals(0L, window.unspentCents());
        }

        @Test
        @DisplayName("Reports zero unspent cash when the budget has a gap")
        void negativeAvailableCash_unspentIsZero() {
            ProjectionWindow window = windowWithPayments(List.of(), -5_000L);

            assertEquals(0L, window.unspentCents());
        }

        @Test
        @DisplayName("Reports a partially unfunded minimum")
        void partialMinimumShortfall_reportsShortfall() {
            ProjectedPayment payment = new ProjectedPayment(
                    new PaymentAction("loan-1", 20_000L, START.plusDays(7)), PaymentKind.MINIMUM, 0L);
            ProjectionWindow window = new ProjectionWindow(START, END, List.of(payment),
                    Map.of("loan-1", 0L), Map.of("loan-1", new DebtBalance(980_000L, 0L)),
                    20_000L, 10_000L, 25_000L, 5_000L);

            assertTrue(window.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Allows the entire minimum amount to remain unfunded")
        void shortfallEqualsMinimums_reportsShortfall() {
            ProjectionWindow window = new ProjectionWindow(START, END, List.of(),
                    Map.of("loan-1", 0L), Map.of("loan-1", new DebtBalance(1_000_000L, 0L)),
                    0L, 10_000L, 25_000L, 25_000L);

            assertTrue(window.hasMinimumShortfall());
        }

        @Test
        @DisplayName("Reports no shortfall when none is recorded")
        void zeroMinimumShortfall_reportsNoShortfall() {
            assertFalse(windowWithPayments(List.of(), 40_000L).hasMinimumShortfall());
        }
    }

    @Nested
    @DisplayName("Window and payment dates")
    class Dates {
        @Test
        @DisplayName("Accepts a 14-day window crossing a year boundary")
        void windowCrossesYear_constructsSuccessfully() {
            assertDoesNotThrow(() -> windowWithDates(
                    LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 8)));
        }

        @Test
        @DisplayName("Rejects a window shorter than 14 days")
        void thirteenDayWindow_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithDates(START, START.plusDays(13)));
        }

        @Test
        @DisplayName("Rejects a window longer than 14 days")
        void fifteenDayWindow_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithDates(START, START.plusDays(15)));
        }

        @Test
        @DisplayName("Rejects reversed window dates")
        void reversedWindow_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithDates(END, START));
        }

        @Test
        @DisplayName("Accepts payments on the first and last included days")
        void paymentDatesAtIncludedBoundaries_constructsSuccessfully() {
            assertDoesNotThrow(() -> windowWithPayments(List.of(
                    payment("loan-1", 15_000L, START),
                    payment("card-1", 25_000L, END.minusDays(1))), 40_000L));
        }

        @Test
        @DisplayName("Rejects a payment before the window starts")
        void paymentBeforeStart_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(
                    List.of(payment("loan-1", 15_000L, START.minusDays(1))), 40_000L));
        }

        @Test
        @DisplayName("Rejects a payment on the exclusive window end")
        void paymentOnEndDate_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(
                    List.of(payment("loan-1", 15_000L, END)), 40_000L));
        }

        @Test
        @DisplayName("Rejects a missing start date")
        void nullStartDate_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> windowWithDates(null, END));
        }

        @Test
        @DisplayName("Rejects a missing end date")
        void nullEndDate_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> windowWithDates(START, null));
        }
    }

    @Nested
    @DisplayName("Cash and debt constraints")
    class Constraints {
        @Test
        @DisplayName("Rejects combined payments one cent above available cash")
        void paymentSumExceedsAvailable_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(List.of(
                    payment("loan-1", 15_000L, START), payment("card-1", 25_001L, START)), 40_000L));
        }

        @Test
        @DisplayName("Rejects payments when available cash is zero")
        void paymentWithZeroAvailable_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(
                    List.of(payment("loan-1", 1L, START)), 0L));
        }

        @Test
        @DisplayName("Rejects payments when available cash is negative")
        void paymentWithNegativeAvailable_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(
                    List.of(payment("loan-1", 1L, START)), -5_000L));
        }

        @Test
        @DisplayName("Rejects a negative protected buffer")
        void negativeBuffer_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectionWindow(
                    START, END, List.of(), Map.of(), Map.of(), 40_000L, -1L, 0L, 0L));
        }

        @Test
        @DisplayName("Rejects negative total minimums due")
        void negativeMinimumsDue_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectionWindow(
                    START, END, List.of(), Map.of(), Map.of(), 40_000L, 10_000L, -1L, 0L));
        }

        @Test
        @DisplayName("Rejects a negative minimum shortfall")
        void negativeShortfall_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectionWindow(
                    START, END, List.of(), Map.of(), Map.of(), 0L, 10_000L, 25_000L, -1L));
        }

        @Test
        @DisplayName("Rejects a shortfall one cent above minimums due")
        void shortfallExceedsMinimums_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectionWindow(
                    START, END, List.of(), Map.of(), Map.of(), 0L, 10_000L, 25_000L, 25_001L));
        }

        @Test
        @DisplayName("Rejects negative interest charges")
        void negativeInterestCharge_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithMaps(
                    Map.of("loan-1", -1L), Map.of("loan-1", new DebtBalance(1_000_000L, 0L))));
        }

        @Test
        @DisplayName("Rejects different debt IDs even when both maps have the same size")
        void mismatchedDebtIds_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithMaps(
                    Map.of("card-1", 0L), Map.of("loan-1", new DebtBalance(1_000_000L, 0L))));
        }

        @Test
        @DisplayName("Rejects a payment referencing a debt absent from the maps")
        void unknownPaymentDebt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithPayments(
                    List.of(payment("unknown-loan", 15_000L, START)), 40_000L));
        }

        @Test
        @DisplayName("Rejects a missing payment list")
        void nullPayments_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> windowWithPayments(null, 40_000L));
        }

        @Test
        @DisplayName("Rejects a null entry in the payment list")
        void nullPaymentEntry_throwsNullPointerException() {
            List<ProjectedPayment> payments = new ArrayList<>();
            payments.add(null);

            assertThrows(NullPointerException.class, () -> windowWithPayments(payments, 40_000L));
        }

        @Test
        @DisplayName("Rejects a missing interest map")
        void nullInterestMap_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> windowWithMaps(null, Map.of()));
        }

        @Test
        @DisplayName("Rejects a missing balance map")
        void nullBalanceMap_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> windowWithMaps(Map.of(), null));
        }

        @Test
        @DisplayName("Rejects a null debt ID in the interest map")
        void nullInterestDebtId_throwsNullPointerException() {
            Map<String, Long> interest = new HashMap<>();
            interest.put(null, 0L);

            assertThrows(NullPointerException.class, () -> windowWithMaps(interest, Map.of()));
        }

        @Test
        @DisplayName("Rejects a null debt ID in the balance map")
        void nullBalanceDebtId_throwsNullPointerException() {
            Map<String, DebtBalance> balances = new HashMap<>();
            balances.put(null, new DebtBalance(1_000_000L, 0L));

            assertThrows(NullPointerException.class, () -> windowWithMaps(Map.of(), balances));
        }

        @Test
        @DisplayName("Rejects an empty debt ID even when it appears in both maps")
        void emptyMapDebtId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithMaps(
                    Map.of("", 0L), Map.of("", new DebtBalance(1_000_000L, 0L))));
        }

        @Test
        @DisplayName("Rejects a whitespace-only debt ID even when it appears in both maps")
        void whitespaceMapDebtId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> windowWithMaps(
                    Map.of("   ", 0L), Map.of("   ", new DebtBalance(1_000_000L, 0L))));
        }

        @Test
        @DisplayName("Rejects a null interest charge")
        void nullInterestValue_throwsNullPointerException() {
            Map<String, Long> interest = new HashMap<>();
            interest.put("loan-1", null);

            assertThrows(NullPointerException.class, () -> windowWithMaps(
                    interest, Map.of("loan-1", new DebtBalance(1_000_000L, 0L))));
        }

        @Test
        @DisplayName("Rejects a null ending balance")
        void nullBalanceValue_throwsNullPointerException() {
            Map<String, DebtBalance> balances = new HashMap<>();
            balances.put("loan-1", null);

            assertThrows(NullPointerException.class,
                    () -> windowWithMaps(Map.of("loan-1", 0L), balances));
        }
    }

    @Nested
    @DisplayName("Deterministic ordering and immutable collections")
    class Collections {
        @Test
        @DisplayName("Orders payments by date before debt ID")
        void paymentsOnDifferentDates_sortedByDate() {
            ProjectedPayment early = payment("loan-1", 15_000L, START);
            ProjectedPayment late = payment("card-1", 25_000L, START.plusDays(7));
            ProjectionWindow window = windowWithPayments(List.of(late, early), 40_000L);

            assertEquals(List.of(early, late), window.payments());
        }

        @Test
        @DisplayName("Orders same-date payments by debt ID before kind")
        void sameDatePayments_sortedByDebtId() {
            ProjectedPayment card = payment("card-1", 25_000L, START);
            ProjectedPayment loan = new ProjectedPayment(
                    new PaymentAction("loan-1", 15_000L, START), PaymentKind.MINIMUM, 0L);
            ProjectionWindow window = new ProjectionWindow(START, END, List.of(loan, card),
                    Map.of("card-1", 0L, "loan-1", 0L),
                    Map.of("card-1", new DebtBalance(125_000L, 0L),
                            "loan-1", new DebtBalance(985_000L, 0L)),
                    40_000L, 10_000L, 15_000L, 0L);

            assertEquals(List.of(card, loan), window.payments());
        }

        @Test
        @DisplayName("Orders a minimum before an extra for the same debt and date")
        void sameDateAndDebtPayments_sortedByKind() {
            ProjectedPayment extra = payment("loan-1", 25_000L, START);
            ProjectedPayment minimum = new ProjectedPayment(
                    new PaymentAction("loan-1", 15_000L, START), PaymentKind.MINIMUM, 0L);
            ProjectionWindow window = new ProjectionWindow(START, END, List.of(extra, minimum),
                    Map.of("loan-1", 0L), Map.of("loan-1", new DebtBalance(960_000L, 0L)),
                    40_000L, 10_000L, 15_000L, 0L);

            assertEquals(List.of(minimum, extra), window.payments());
        }

        @Test
        @DisplayName("Iterates both debt maps in ID order regardless of insertion order")
        void reverseInsertedDebtMaps_iterateByDebtId() {
            Map<String, Long> interest = new LinkedHashMap<>();
            interest.put("loan-1", 1_500L);
            interest.put("card-1", 2_300L);
            Map<String, DebtBalance> balances = new LinkedHashMap<>();
            balances.put("loan-1", new DebtBalance(980_000L, 21_500L));
            balances.put("card-1", new DebtBalance(150_000L, 2_300L));
            ProjectionWindow window = windowWithMaps(interest, balances);

            assertEquals(List.of("card-1", "loan-1"), new ArrayList<>(window.interestChargedCents().keySet()));
            assertEquals(List.of("card-1", "loan-1"), new ArrayList<>(window.endingBalances().keySet()));
        }

        @Test
        @DisplayName("Retains payments after the caller changes the original list")
        void sourcePaymentsChanged_windowRetainsSnapshot() {
            ProjectedPayment payment = payment("loan-1", 15_000L, START);
            List<ProjectedPayment> payments = new ArrayList<>(List.of(payment));
            ProjectionWindow window = windowWithPayments(payments, 40_000L);

            payments.clear();

            assertEquals(List.of(payment), window.payments());
        }

        @Test
        @DisplayName("Retains interest charges after the caller changes the original map")
        void sourceInterestChanged_windowRetainsSnapshot() {
            Map<String, Long> interest = new HashMap<>(Map.of("loan-1", 1_500L));
            ProjectionWindow window = windowWithMaps(interest,
                    Map.of("loan-1", new DebtBalance(980_000L, 21_500L)));

            interest.put("loan-1", 9_000L);

            assertEquals(Map.of("loan-1", 1_500L), window.interestChargedCents());
        }

        @Test
        @DisplayName("Retains balances after the caller changes the original map")
        void sourceBalancesChanged_windowRetainsSnapshot() {
            DebtBalance balance = new DebtBalance(980_000L, 21_500L);
            Map<String, DebtBalance> balances = new HashMap<>(Map.of("loan-1", balance));
            ProjectionWindow window = windowWithMaps(Map.of("loan-1", 1_500L), balances);

            balances.put("loan-1", new DebtBalance(0L, 0L));

            assertEquals(Map.of("loan-1", balance), window.endingBalances());
        }

        @Test
        @DisplayName("Prevents changes through the returned payment list")
        void returnedPaymentsModified_throwsUnsupportedOperationException() {
            ProjectionWindow window = windowWithPayments(
                    List.of(payment("loan-1", 15_000L, START)), 40_000L);

            assertThrows(UnsupportedOperationException.class, () -> window.payments().clear());
        }

        @Test
        @DisplayName("Prevents changes through the returned interest map")
        void returnedInterestModified_throwsUnsupportedOperationException() {
            ProjectionWindow window = windowWithPayments(List.of(), 40_000L);

            assertThrows(UnsupportedOperationException.class,
                    () -> window.interestChargedCents().put("loan-1", 9_000L));
        }

        @Test
        @DisplayName("Prevents changes through the returned balance map")
        void returnedBalancesModified_throwsUnsupportedOperationException() {
            ProjectionWindow window = windowWithPayments(List.of(), 40_000L);

            assertThrows(UnsupportedOperationException.class,
                    () -> window.endingBalances().put("loan-1", new DebtBalance(0L, 0L)));
        }
    }

    @Nested
    @DisplayName("Overflow boundaries")
    class Overflow {
        @Test
        @DisplayName("Accepts a payment sum exactly at the largest long value")
        void paymentSumEqualsLongMaximum_returnsExactTotal() {
            ProjectionWindow window = windowWithPayments(List.of(
                    payment("loan-1", Long.MAX_VALUE - 15_000L, START),
                    payment("card-1", 15_000L, START)), Long.MAX_VALUE);

            assertEquals(Long.MAX_VALUE, window.totalPaidCents());
            assertEquals(0L, window.unspentCents());
        }

        @Test
        @DisplayName("Rejects a payment sum one cent beyond the largest long value")
        void paymentSumExceedsLongMaximum_throwsArithmeticException() {
            assertThrows(ArithmeticException.class, () -> windowWithPayments(List.of(
                    payment("loan-1", Long.MAX_VALUE, START),
                    payment("card-1", 1L, START)), Long.MAX_VALUE));
        }

        @Test
        @DisplayName("Returns an interest sum exactly at the largest long value")
        void interestSumEqualsLongMaximum_returnsExactTotal() {
            ProjectionWindow window = windowWithMaps(
                    Map.of("loan-1", Long.MAX_VALUE - 1_500L, "card-1", 1_500L),
                    Map.of("loan-1", new DebtBalance(0L, Long.MAX_VALUE - 1_500L),
                            "card-1", new DebtBalance(0L, 1_500L)));

            assertEquals(Long.MAX_VALUE, window.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Rejects an interest summary one cent beyond the largest long value")
        void interestSumExceedsLongMaximum_throwsArithmeticException() {
            ProjectionWindow window = windowWithMaps(
                    Map.of("loan-1", Long.MAX_VALUE, "card-1", 1L),
                    Map.of("loan-1", new DebtBalance(0L, Long.MAX_VALUE),
                            "card-1", new DebtBalance(0L, 1L)));

            assertThrows(ArithmeticException.class, window::totalInterestChargedCents);
        }
    }

    private static ProjectedPayment payment(String debtId, long amountCents, LocalDate date) {
        return new ProjectedPayment(new PaymentAction(debtId, amountCents, date), PaymentKind.EXTRA, 0L);
    }

    private static ProjectionWindow windowWithPayments(List<ProjectedPayment> payments, long availableCents) {
        return new ProjectionWindow(START, END, payments,
                Map.of("loan-1", 0L, "card-1", 0L),
                Map.of("loan-1", new DebtBalance(980_000L, 0L),
                        "card-1", new DebtBalance(150_000L, 0L)),
                availableCents, 10_000L, 0L, 0L);
    }

    private static ProjectionWindow windowWithMaps(
            Map<String, Long> interest, Map<String, DebtBalance> balances) {
        return new ProjectionWindow(START, END, List.of(), interest, balances,
                40_000L, 10_000L, 0L, 0L);
    }

    private static ProjectionWindow windowWithDates(LocalDate start, LocalDate end) {
        return new ProjectionWindow(start, end, List.of(), Map.of(), Map.of(),
                40_000L, 0L, 0L, 0L);
    }
}
