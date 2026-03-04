package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;

@DisplayName("DebtPayoffSimulator")
class DebtPayoffSimulatorTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 1);

    private static Debt studentLoan(String id, long balanceCents, int aprBasisPoints) {
        return new Debt(id, "Loan " + id, DebtType.STUDENT_LOAN, balanceCents, aprBasisPoints, 0L, 15, null);
    }

    private static Debt creditCard(String id, long balanceCents, int aprBasisPoints) {
        return new Debt(id, "Card " + id, DebtType.CREDIT_CARD, balanceCents, aprBasisPoints, 0L, 15, 500_000L);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("single debt pays off in one period — interest, balance, and payoff date are correct")
        void singleDebt_paysOffInOnePeriod_resultIsCorrect() {
            // balance=100_000, APR=1200bps → monthly interest = ceilDiv(100_000*1200, 120_000) = 1_000
            Debt debt = studentLoan("d1", 100_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 101_000L), START);

            assertEquals(1_000L, result.totalInterestPaidCents());
            assertEquals(1, result.periodsSimulated());
            assertEquals(0L, result.endingBalances().get("d1"));
            assertFalse(result.neverPaysOff());
            assertEquals(START.plusMonths(1), result.estimatedPayoffDate());
        }

        @Test
        @DisplayName("single debt pays off over multiple periods — periods and interest accumulate correctly")
        void singleDebt_multiPeriodPayoff_periodsAndInterestAreCorrect() {
            // balance=120_000, APR=1200bps, payment=70_000
            // period 1: interest=1_200 → new balance=51_200
            // period 2: interest=ceilDiv(51_200*1200,120_000)=512 → new balance=0
            // totalInterest = 1_200 + 512 = 1_712
            Debt debt = studentLoan("d1", 120_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 70_000L), START);

            assertEquals(2, result.periodsSimulated());
            assertEquals(1_712L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("d1"));
            assertEquals(START.plusMonths(2), result.estimatedPayoffDate());
        }

        @Test
        @DisplayName("multiple debts — interest from each is summed and balances are tracked independently")
        void multipleDebts_independentBalancesAndInterestSummed() {
            // Debt A: balance=60_000, APR=1800bps → interest=900, payment=61_000 → pays off period 1
            // Debt B: balance=40_000, APR=600bps  → interest=200, payment=40_300 → pays off period 1
            // totalInterest = 900 + 200 = 1_100
            Debt debtA = studentLoan("a", 60_000L, 1800);
            Debt debtB = studentLoan("b", 40_000L, 600);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debtA, debtB), Map.of("a", 61_000L, "b", 40_300L), START);

            assertEquals(1_100L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("a"));
            assertEquals(0L, result.endingBalances().get("b"));
            assertFalse(result.neverPaysOff());
        }
    }

    @Nested
    @DisplayName("boundary and math")
    class BoundaryAndMath {

        @Test
        @DisplayName("zero APR debt — no interest accrues, all payment is principal")
        void zeroApr_noInterestAccrues() {
            Debt debt = studentLoan("d1", 50_000L, 0);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 50_000L), START);

            assertEquals(0L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("d1"));
        }

        @Test
        @DisplayName("overpayment clamps ending balance to zero, not negative")
        void overpayment_endingBalanceIsZeroNotNegative() {
            // balance=10_000, APR=1200bps → interest=100, payment=50_000 (massive overpayment)
            Debt debt = studentLoan("d1", 10_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 50_000L), START);

            assertEquals(0L, result.endingBalances().get("d1"));
        }
    }

    @Nested
    @DisplayName("never pays off")
    class NeverPaysOff {

        @Test
        @DisplayName("payment exactly covers interest every period — never pays off, payoff date is null")
        void paymentCoversInterestOnly_neverPaysOff() {
            // balance=120_000, APR=1200bps → monthly interest=1_200, payment=1_200 → principal never reduces
            Debt debt = studentLoan("d1", 120_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 1_200L), START);

            assertTrue(result.neverPaysOff());
            assertNull(result.estimatedPayoffDate());
            assertEquals(360, result.periodsSimulated());
        }
    }

    @Nested
    @DisplayName("snapshot behavior")
    class SnapshotBehavior {

        @Test
        @DisplayName("captureSnapshots=true — snapshot count equals periodsSimulated, period 0 values are correct")
        void captureSnapshotsTrue_snapshotsPresentAndCorrect() {
            // balance=10_000, APR=1200bps → interest=100, payment=10_200 → pays off period 1
            // principalApplied = balance - newBalance = 10_000 - 0 = 10_000
            Debt debt = studentLoan("d1", 10_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(true)
                    .simulate(List.of(debt), Map.of("d1", 10_200L), START);

            assertEquals(result.periodsSimulated(), result.snapshots().size());

            PeriodSnapshot snap0 = result.snapshots().get(0);
            assertEquals(0, snap0.periodIndex());
            assertEquals(0L, snap0.balancesAfter().get("d1"));
            assertEquals(100L, snap0.interestCharged().get("d1"));
            assertEquals(10_000L, snap0.principalApplied().get("d1"));
        }

        @Test
        @DisplayName("captureSnapshots=false — snapshot list is empty")
        void captureSnapshotsFalse_snapshotsIsEmpty() {
            Debt debt = studentLoan("d1", 10_000L, 0);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of("d1", 10_000L), START);

            assertTrue(result.snapshots().isEmpty());
        }
    }

    @Nested
    @DisplayName("guard and edge cases")
    class GuardAndEdgeCases {

        @Test
        @DisplayName("unknown debtId in paymentsPerPeriod throws IllegalArgumentException")
        void unknownDebtId_throwsIllegalArgumentException() {
            Debt debt = studentLoan("d1", 10_000L, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> new DebtPayoffSimulator(false)
                            .simulate(List.of(debt), Map.of("unknown", 5_000L), START));
        }

        @Test
        @DisplayName("negative payment amount throws IllegalArgumentException")
        void negativePayment_throwsIllegalArgumentException() {
            Debt debt = studentLoan("d1", 10_000L, 1200);
            assertThrows(IllegalArgumentException.class,
                    () -> new DebtPayoffSimulator(false)
                            .simulate(List.of(debt), Map.of("d1", -1L), START));
        }

        @Test
        @DisplayName("debt with zero starting balance — skipped each period, no interest, pays off immediately")
        void zeroStartingBalance_noInterestAndImmediatelyComplete() {
            Debt debt = studentLoan("d1", 0L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debt), Map.of(), START);

            assertEquals(0L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("d1"));
            assertFalse(result.neverPaysOff());
            assertEquals(0, result.periodsSimulated());
            assertEquals(START, result.estimatedPayoffDate());
        }
    }

    @Nested
    @DisplayName("mixed scenarios")
    class MixedScenarios {

        @Test
        @DisplayName("credit card and student loan together — different types simulate independently")
        void mixedDebtTypes_eachTypeSimulatesCorrectly() {
            // Credit card: balance=50_000, APR=1800bps → interest=ceilDiv(50_000*1800,120_000)=750, payment=51_000 → pays off period 1
            // Student loan: balance=30_000, APR=600bps  → interest=ceilDiv(30_000*600,120_000)=150,  payment=30_200 → pays off period 1
            // totalInterest = 750 + 150 = 900
            Debt card = creditCard("cc", 50_000L, 1800);
            Debt loan = studentLoan("sl", 30_000L, 600);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(card, loan), Map.of("cc", 51_000L, "sl", 30_200L), START);

            assertEquals(900L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("cc"));
            assertEquals(0L, result.endingBalances().get("sl"));
            assertFalse(result.neverPaysOff());
        }

        @Test
        @DisplayName("some debts start at zero, others have balances — zero debts skipped, interest only accrues on non-zero debts")
        void mixedStartingBalances_zeroDebtsSkippedNonZeroSimulatedNormally() {
            // Debt A: balance=0 — skipped, no interest
            // Debt B: balance=20_000, APR=1200bps → interest=200, payment=20_200 → pays off period 1
            Debt debtA = studentLoan("a", 0L, 1200);
            Debt debtB = studentLoan("b", 20_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debtA, debtB), Map.of("b", 20_200L), START);

            assertEquals(200L, result.totalInterestPaidCents());
            assertEquals(0L, result.endingBalances().get("a"));
            assertEquals(0L, result.endingBalances().get("b"));
            assertFalse(result.neverPaysOff());
        }

        @Test
        @DisplayName("one debt pays off, another never reduces principal — neverPaysOff=true, payoff date is null")
        void mixedPayoff_oneSettlesOneDoesNot_neverPaysOffIsTrue() {
            // Debt A: balance=10_000, APR=1200bps → interest=100, payment=20_000 → pays off period 1
            // Debt B: balance=120_000, APR=1200bps → interest=1_200, payment=1_200 → principal never reduces
            Debt debtA = creditCard("a", 10_000L, 1200);
            Debt debtB = creditCard("b", 120_000L, 1200);
            SimulationResult result = new DebtPayoffSimulator(false)
                    .simulate(List.of(debtA, debtB), Map.of("a", 20_000L, "b", 1_200L), START);

            assertTrue(result.neverPaysOff());
            assertNull(result.estimatedPayoffDate());
            assertEquals(0L, result.endingBalances().get("a"));
            assertTrue(result.endingBalances().get("b") > 0);
        }
    }
}
