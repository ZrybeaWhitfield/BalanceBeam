package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.plan.DebtBalance;

@DisplayName("DebtInterestState")
class DebtInterestStateTest {

    @Nested
    @DisplayName("Opening balances")
    class OpeningBalances {
        @Test
        @DisplayName("Separates supplied opening interest without counting it as a new charge")
        void suppliedOpeningInterest_initializesBalanceSplit() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 20_000L);

            assertEquals(new DebtBalance(980_000L, 20_000L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
            assertEquals(0L, state.postInterest());
        }

        @Test
        @DisplayName("Treats omitted student-loan opening interest as zero")
        void omittedOpeningInterest_initializesEntireBalanceAsPrincipal() {
            DebtInterestState state = studentLoan(1_000_000L, 600, null);

            assertEquals(new DebtBalance(1_000_000L, 0L), state.balance());
        }

        @Test
        @DisplayName("Treats the entire opening card balance as modeled principal")
        void creditCardOpeningBalance_initializesPrincipal() {
            DebtInterestState state = creditCard(150_000L, 2400);

            assertEquals(new DebtBalance(150_000L, 0L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }
    }

    @Nested
    @DisplayName("Daily interest models")
    class DailyInterest {
        @Test
        @DisplayName("Charges seventy cents over seven days on the worked student-loan example")
        void studentLoanSevenDays_chargesSimpleDailyInterest() {
            DebtInterestState state = studentLoan(37_025L, 1000, 500L);

            accrueDays(state, 7);

            assertEquals(70L, state.postInterest());
            assertEquals(new DebtBalance(36_525L, 570L), state.balance());
        }

        @Test
        @DisplayName("Student-loan interest does not compound on opening or newly posted interest")
        void studentLoanWithUnpaidInterest_accruesOnlyOnPrincipal() {
            DebtInterestState state = studentLoan(385_250L, 1000, 20_000L);

            for (int day = 0; day < 30; day++) {
                state.accrueDay();
                state.postInterest();
            }

            assertEquals(3_000L, state.totalInterestChargedCents());
            assertEquals(new DebtBalance(365_250L, 23_000L), state.balance());
        }

        @Test
        @DisplayName("Compounds card interest daily including interest not yet posted")
        void creditCardThirtyDays_chargesCompoundedInterest() {
            DebtInterestState state = creditCard(100_000L, 3650);

            accrueDays(state, 30);

            assertEquals(3_044L, state.postInterest());
            assertEquals(new DebtBalance(100_000L, 3_044L), state.balance());
        }

        @Test
        @DisplayName("A student-loan payment reduces the principal used for that day's accrual")
        void studentLoanPaymentBeforeAccrual_usesReducedPrincipal() {
            DebtInterestState state = studentLoan(365_250L, 1000, 0L);

            state.applyPayment(182_625L);
            state.accrueDay();

            assertEquals(50L, state.postInterest());
            assertEquals(new DebtBalance(182_625L, 50L), state.balance());
        }

        @Test
        @DisplayName("A card payment reduces the balance used for that day's accrual")
        void cardPaymentBeforeAccrual_usesReducedBalance() {
            DebtInterestState state = creditCard(100_000L, 3650);

            state.applyPayment(50_000L);
            state.accrueDay();

            assertEquals(50L, state.postInterest());
        }

        @Test
        @DisplayName("A zero-rate student loan retains opening interest without adding charges")
        void zeroRateStudentLoan_addsNoInterest() {
            DebtInterestState state = studentLoan(1_000_000L, 0, 20_000L);

            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(980_000L, 20_000L), state.balance());
        }

        @Test
        @DisplayName("A zero-rate credit card adds no interest")
        void zeroRateCreditCard_addsNoInterest() {
            DebtInterestState state = creditCard(150_000L, 0);

            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(150_000L, 0L), state.balance());
        }

        @Test
        @DisplayName("A student loan consisting only of unpaid interest accrues no new interest")
        void interestOnlyStudentLoan_addsNoInterest() {
            DebtInterestState state = studentLoan(20_000L, 600, 20_000L);

            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(0L, 20_000L), state.balance());
        }

        @Test
        @DisplayName("An initially paid-off card stays at zero")
        void zeroOpeningCardBalance_addsNoInterest() {
            DebtInterestState state = creditCard(0L, 2400);

            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(0L, 0L), state.balance());
        }
    }

    @Nested
    @DisplayName("Posting and fractional carry")
    class Posting {
        @Test
        @DisplayName("Accrual remains internal until interest is posted")
        void accruedInterestNotPosted_snapshotContainsOnlyPostedCents() {
            DebtInterestState state = studentLoan(37_025L, 1000, 500L);

            accrueDays(state, 7);

            assertEquals(new DebtBalance(36_525L, 500L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Posting twice without more accrual adds no second charge")
        void repeatedPosting_chargesInterestOnce() {
            DebtInterestState state = studentLoan(36_525L, 1000, 0L);
            accrueDays(state, 7);
            state.postInterest();

            assertEquals(0L, state.postInterest());
            assertEquals(70L, state.totalInterestChargedCents());
            assertEquals(new DebtBalance(36_525L, 70L), state.balance());
        }

        @Test
        @DisplayName("Posts ten cents for an exact 10.5-cent accrual")
        void halfwayWithEvenCentBelow_roundsDown() {
            DebtInterestState state = studentLoan(36_525L, 1050, 0L);

            state.accrueDay();

            assertEquals(10L, state.postInterest());
        }

        @Test
        @DisplayName("Posts twelve cents for an exact 11.5-cent accrual")
        void halfwayWithOddCentBelow_roundsUp() {
            DebtInterestState state = studentLoan(36_525L, 1150, 0L);

            state.accrueDay();

            assertEquals(12L, state.postInterest());
        }

        @Test
        @DisplayName("Rounds cumulative interest consistently across intermediate postings")
        void studentLoanPostedDaily_matchesSinglePosting() {
            DebtInterestState daily = studentLoan(36_525L, 1050, 0L);
            DebtInterestState atEnd = studentLoan(36_525L, 1050, 0L);

            for (int day = 0; day < 3; day++) {
                daily.accrueDay();
                daily.postInterest();
                atEnd.accrueDay();
            }
            atEnd.postInterest();

            assertEquals(32L, daily.totalInterestChargedCents());
            assertEquals(new DebtBalance(36_525L, 32L), daily.balance());
            assertEquals(atEnd.balance(), daily.balance());
        }

        @Test
        @DisplayName("Carries quarter-cent accruals through postings until they round to a cent")
        void repeatedSubCentAccrual_accumulatesIntoPostedCent() {
            DebtInterestState state = studentLoan(36_525L, 25, 0L);

            state.accrueDay();
            assertEquals(0L, state.postInterest());
            state.accrueDay();
            assertEquals(0L, state.postInterest());
            state.accrueDay();
            assertEquals(1L, state.postInterest());
            assertEquals(new DebtBalance(36_525L, 1L), state.balance());
        }

        @Test
        @DisplayName("Includes positive fractional carry in the next card accrual")
        void cardPostingRoundsDown_preservesCompoundingBasis() {
            DebtInterestState state = creditCard(10_245L, 3650);

            state.accrueDay();
            assertEquals(10L, state.postInterest());
            state.accrueDay();

            assertEquals(11L, state.postInterest());
            assertEquals(21L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Includes negative fractional carry in the next card accrual")
        void cardPostingRoundsUp_preservesCompoundingBasis() {
            DebtInterestState state = creditCard(11_744L, 3650);

            state.accrueDay();
            assertEquals(12L, state.postInterest());
            state.accrueDay();

            assertEquals(11L, state.postInterest());
            assertEquals(23L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Posting card interest daily or at a window boundary leaves the same final result")
        void cardWithIntermediatePostings_matchesSinglePosting() {
            DebtInterestState daily = creditCard(100_000L, 3650);
            DebtInterestState biweekly = creditCard(100_000L, 3650);
            DebtInterestState atEnd = creditCard(100_000L, 3650);

            for (int day = 1; day <= 28; day++) {
                daily.accrueDay();
                daily.postInterest();
                biweekly.accrueDay();
                if (day % 14 == 0) {
                    biweekly.postInterest();
                }
                atEnd.accrueDay();
            }
            atEnd.postInterest();

            assertEquals(2_838L, atEnd.totalInterestChargedCents());
            assertEquals(atEnd.balance(), daily.balance());
            assertEquals(atEnd.balance(), biweekly.balance());
        }
    }

    @Nested
    @DisplayName("Payments and final settlement")
    class Payments {
        @Test
        @DisplayName("A partial interest payment leaves principal unchanged")
        void paymentBelowUnpaidInterest_reducesOnlyInterest() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 20_000L);

            assertEquals(15_000L, state.applyPayment(15_000L));
            assertEquals(new DebtBalance(980_000L, 5_000L), state.balance());
        }

        @Test
        @DisplayName("A payment equal to unpaid interest leaves principal unchanged")
        void paymentEqualsUnpaidInterest_clearsOnlyInterest() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 20_000L);

            state.applyPayment(20_000L);

            assertEquals(new DebtBalance(980_000L, 0L), state.balance());
        }

        @Test
        @DisplayName("Payment remaining after interest reduces principal")
        void paymentExceedsUnpaidInterest_reducesPrincipalAfterInterest() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 20_000L);

            state.applyPayment(25_000L);

            assertEquals(new DebtBalance(975_000L, 0L), state.balance());
        }

        @Test
        @DisplayName("A payment posts accrued interest before allocating money to principal")
        void paymentWithUnpostedInterest_paysNewInterestFirst() {
            DebtInterestState state = studentLoan(36_525L, 1000, 0L);
            state.accrueDay();

            state.applyPayment(1_010L);

            assertEquals(new DebtBalance(35_525L, 0L), state.balance());
            assertEquals(10L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("A partial card payment preserves fractional interest for the next day's accrual")
        void partialCardPayment_preservesFractionalCarry() {
            DebtInterestState state = creditCard(10_245L, 3650);
            state.accrueDay();

            state.applyPayment(1_000L);

            assertEquals(new DebtBalance(9_255L, 0L), state.balance());
            assertEquals(10L, state.totalInterestChargedCents());

            state.accrueDay();
            state.postInterest();

            assertEquals(20L, state.totalInterestChargedCents());
            assertEquals(new DebtBalance(9_255L, 10L), state.balance());
        }

        @Test
        @DisplayName("Caps the worked example's final payment at principal plus posted interest")
        void requestedPaymentExceedsDebt_returnsActualPayoffAmount() {
            DebtInterestState state = studentLoan(37_025L, 1000, 500L);
            accrueDays(state, 7);

            assertEquals(37_095L, state.applyPayment(40_000L));
            assertEquals(new DebtBalance(0L, 0L), state.balance());
            assertEquals(70L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Repeated payments without an elapsed day do not add interest")
        void multiplePaymentsWithoutAccrual_addNoInterest() {
            DebtInterestState state = studentLoan(101_000L, 600, 1_000L);

            state.applyPayment(500L);
            state.applyPayment(700L);

            assertEquals(new DebtBalance(99_800L, 0L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Paying posted interest does not reduce the cumulative interest charged")
        void postedInterestPaid_retainsChargeHistory() {
            DebtInterestState state = studentLoan(36_525L, 1000, 0L);
            accrueDays(state, 7);
            state.postInterest();

            state.applyPayment(70L);

            assertEquals(new DebtBalance(36_525L, 0L), state.balance());
            assertEquals(70L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("A payment request against an already-paid-off debt returns zero")
        void alreadyPaidOff_returnsZeroActualPayment() {
            DebtInterestState state = studentLoan(0L, 600, 0L);

            assertEquals(0L, state.applyPayment(15_000L));
            assertEquals(new DebtBalance(0L, 0L), state.balance());
        }

        @Test
        @DisplayName("A payoff with interest rounded down stays paid off on later days")
        void payoffWithPositiveFraction_staysPaidOff() {
            DebtInterestState state = creditCard(10_245L, 3650);
            state.accrueDay();

            assertEquals(10_255L, state.applyPayment(20_000L));
            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(0L, 0L), state.balance());
            assertEquals(10L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("A payoff with interest rounded up preserves charges and stays paid off")
        void payoffWithNegativeFraction_staysPaidOff() {
            DebtInterestState state = creditCard(11_744L, 3650);
            state.accrueDay();

            assertEquals(11_756L, state.applyPayment(20_000L));
            accrueDays(state, 30);

            assertEquals(0L, state.postInterest());
            assertEquals(new DebtBalance(0L, 0L), state.balance());
            assertEquals(12L, state.totalInterestChargedCents());
        }
    }

    @Nested
    @DisplayName("Invalid inputs and numeric boundaries")
    class Boundaries {
        @Test
        @DisplayName("Rejects a missing projection debt")
        void nullInput_throwsNullPointerException() {
            assertThrows(NullPointerException.class, () -> new DebtInterestState(null));
        }

        @Test
        @DisplayName("Rejects a zero payment request")
        void zeroRequestedPayment_throwsIllegalArgumentException() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 0L);

            assertThrows(IllegalArgumentException.class, () -> state.applyPayment(0L));
        }

        @Test
        @DisplayName("Rejects a negative payment request")
        void negativeRequestedPayment_throwsIllegalArgumentException() {
            DebtInterestState state = studentLoan(1_000_000L, 600, 0L);

            assertThrows(IllegalArgumentException.class, () -> state.applyPayment(-1L));
        }

        @Test
        @DisplayName("Allows posted interest to bring the total balance exactly to the largest long value")
        void postedBalanceEqualsLongMaximum_returnsExactBalance() {
            long openingInterest = Long.MAX_VALUE - 10L - 36_525L;
            DebtInterestState state = studentLoan(Long.MAX_VALUE - 10L, 1000, openingInterest);
            state.accrueDay();

            assertEquals(10L, state.postInterest());
            assertEquals(Long.MAX_VALUE, state.balance().totalBalanceCents());
        }

        @Test
        @DisplayName("Rejects total balance overflow without partially posting interest")
        void postedBalanceExceedsLongMaximum_preservesPostedState() {
            DebtInterestState state = studentLoan(Long.MAX_VALUE, 1, 0L);
            state.accrueDay();

            assertThrows(ArithmeticException.class, state::postInterest);
            assertEquals(new DebtBalance(Long.MAX_VALUE, 0L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Rejects unpaid-interest overflow without partially posting interest")
        void unpaidInterestExceedsLongMaximum_preservesPostedState() {
            DebtInterestState state = studentLoan(Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE - 1L);
            state.accrueDay();

            assertThrows(ArithmeticException.class, state::postInterest);
            assertEquals(new DebtBalance(1L, Long.MAX_VALUE - 1L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }

        @Test
        @DisplayName("Keeps large accrual exact but rejects posting charges beyond long range")
        void accruedChargesExceedLongMaximum_throwsOnPosting() {
            DebtInterestState state = studentLoan(Long.MAX_VALUE, Integer.MAX_VALUE, 0L);

            assertDoesNotThrow(state::accrueDay);

            assertThrows(ArithmeticException.class, state::postInterest);
            assertEquals(new DebtBalance(Long.MAX_VALUE, 0L), state.balance());
            assertEquals(0L, state.totalInterestChargedCents());
        }
    }

    private static DebtInterestState studentLoan(long totalCents, int aprBasisPoints, Long openingInterestCents) {
        Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                totalCents, aprBasisPoints, 15_000L, 13, null);
        return new DebtInterestState(new ProjectionDebt(debt, openingInterestCents));
    }

    private static DebtInterestState creditCard(long totalCents, int aprBasisPoints) {
        Debt debt = new Debt("card-1", "Credit card", DebtType.CREDIT_CARD,
                totalCents, aprBasisPoints, 5_000L, 13, 500_000L);
        return new DebtInterestState(new ProjectionDebt(debt));
    }

    private static void accrueDays(DebtInterestState state, int days) {
        for (int day = 0; day < days; day++) {
            state.accrueDay();
        }
    }
}
