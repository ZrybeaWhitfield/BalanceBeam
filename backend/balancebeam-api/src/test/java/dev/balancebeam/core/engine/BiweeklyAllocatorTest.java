package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.plan.PaymentAction;

@DisplayName("BiweeklyAllocator")
class BiweeklyAllocatorTest {

    private final BiweeklyAllocator allocator = new BiweeklyAllocator();
    private final Budget budget = new Budget(200_000L, 100_000L, 10_000L);
    private final PayoffStrategy strategy = new AvalancheStrategy();

    @Nested
    @DisplayName("Pay window")
    class PayWindow {
        @Test
        @DisplayName("Includes a minimum due on payday")
        void debtDueOnPayday_minimumScheduled() {
            Debt debt = debtDueOn(5);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(
                    List.of(new PaymentAction(debt.id(), 10_000L, LocalDate.of(2026, 4, 5))),
                    result.minimumPaymentActions());
        }

        @Test
        @DisplayName("Excludes a minimum due on the next payday")
        void debtDueOnNextPayday_minimumExcluded() {
            Debt debt = debtDueOn(19);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(List.of(), result.minimumPaymentActions());
        }

        @Test
        @DisplayName("Includes a minimum due after the window crosses into a new month")
        void debtDueAfterMonthRollover_minimumScheduled() {
            Debt debt = debtDueOn(1);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 25));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(
                    List.of(new PaymentAction(debt.id(), 10_000L, LocalDate.of(2026, 5, 1))),
                    result.minimumPaymentActions());
        }

        @Test
        @DisplayName("Schedules multiple minimums in due-date order")
        void multipleDebtsDueInWindow_minimumsOrderedByDueDate() {
            Debt later = new Debt("loan-2", "Other student loan", DebtType.STUDENT_LOAN,
                    100_000L, 800, 15_000L, 16, null);
            Debt earlier = debtDueOn(10);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(later, earlier), budget, schedule, strategy);

            assertEquals(List.of(
                    new PaymentAction(earlier.id(), 10_000L, LocalDate.of(2026, 4, 10)),
                    new PaymentAction(later.id(), 15_000L, LocalDate.of(2026, 4, 16))),
                    result.minimumPaymentActions());
        }

        @Test
        @DisplayName("Caps a due minimum at the debt's remaining balance")
        void minimumExceedsBalance_paymentEqualsBalance() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    8_000L, 600, 10_000L, 10, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(List.of(new PaymentAction(debt.id(), 8_000L, LocalDate.of(2026, 4, 10))),
                    result.minimumPaymentActions());
            assertEquals(8_000L, result.totalMinimumsDueCents());
            assertEquals(List.of(), result.extraPaymentActions());
        }

        @Test
        @DisplayName("Excludes a paid-off debt from minimum and extra payments")
        void zeroBalanceDebt_noPaymentActions() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    0L, 600, 10_000L, 10, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(List.of(), result.minimumPaymentActions());
            assertEquals(0L, result.totalMinimumsDueCents());
            assertEquals(List.of(), result.extraPaymentActions());
        }
    }

    @Nested
    @DisplayName("Extra payments")
    class ExtraPayments {
        @Test
        @DisplayName("Returns extra actions in debt ID order regardless of strategy map order")
        void strategyReturnsReverseOrder_extraActionsSortedByDebtId() {
            Debt first = new Debt("loan-a", "Student loan A", DebtType.STUDENT_LOAN,
                    100_000L, 600, 10_000L, 25, null);
            Debt second = new Debt("loan-z", "Student loan Z", DebtType.STUDENT_LOAN,
                    100_000L, 800, 10_000L, 25, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));
            PayoffStrategy reverseOrder = (debts, extraCents) -> {
                Map<String, Long> allocations = new LinkedHashMap<>();
                allocations.put(second.id(), 20_000L);
                allocations.put(first.id(), 30_000L);
                return allocations;
            };

            AllocationResult result = allocator.allocate(List.of(first, second), budget, schedule, reverseOrder);

            assertEquals(List.of(
                    new PaymentAction(first.id(), 30_000L, LocalDate.of(2026, 4, 5)),
                    new PaymentAction(second.id(), 20_000L, LocalDate.of(2026, 4, 5))),
                    result.extraPaymentActions());
        }

        @Test
        @DisplayName("Limits extra payment to the balance left after the minimum")
        void minimumNearlyPaysOffDebt_extraLimitedToRemainingBalance() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    11_000L, 600, 10_000L, 5, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(
                    List.of(new PaymentAction(debt.id(), 10_000L, LocalDate.of(2026, 4, 5))),
                    result.minimumPaymentActions());
            assertEquals(
                    List.of(new PaymentAction(debt.id(), 1_000L, LocalDate.of(2026, 4, 5))),
                    result.extraPaymentActions());
        }

        @Test
        @DisplayName("Schedules due minimum before sending remaining cash to the strategy")
        void minimumDueAndExtraAvailable_minimumPaidBeforeExtra() {
            Debt dueNow = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    150_000L, 600, 30_000L, 10, null);
            Debt dueLater = new Debt("loan-2", "Other student loan", DebtType.STUDENT_LOAN,
                    200_000L, 2400, 15_000L, 25, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(dueNow, dueLater), budget, schedule, strategy);

            assertEquals(List.of(new PaymentAction(dueNow.id(), 30_000L, LocalDate.of(2026, 4, 10))),
                    result.minimumPaymentActions());
            assertEquals(List.of(new PaymentAction(dueLater.id(), 60_000L, LocalDate.of(2026, 4, 5))),
                    result.extraPaymentActions());
        }

        @Test
        @DisplayName("Keeps the buffer when a strategy uses all cash remaining after minimums")
        void extrasUseAllRemainingCash_bufferRemainsReserved() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    200_000L, 600, 20_000L, 10, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debt), budget, schedule, strategy);

            assertEquals(10_000L, result.reservedBufferCents());
            assertEquals(20_000L, result.minimumPaymentActions().getFirst().paymentAmountCents());
            assertEquals(70_000L, result.extraPaymentActions().getFirst().paymentAmountCents());
        }

        @Test
        @DisplayName("Rejects strategy payments that exceed cash left after minimums")
        void strategyAllocationsExceedRemainingCash_throwsIllegalArgumentException() {
            Debt dueNow = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    100_000L, 600, 20_000L, 5, null);
            Debt dueLater = new Debt("loan-2", "Other student loan", DebtType.STUDENT_LOAN,
                    100_000L, 800, 10_000L, 25, null);
            Budget paycheck = new Budget(200_000L, 100_000L, 10_000L);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));
            PayoffStrategy overAllocating = (debts, extraCents) -> Map.of(
                    dueNow.id(), 40_000L, dueLater.id(), 35_000L);

            assertThrows(IllegalArgumentException.class,
                    () -> allocator.allocate(List.of(dueNow, dueLater), paycheck, schedule, overAllocating));
        }

        @Test
        @DisplayName("Rejects an extra payment for a debt outside the input list")
        void strategyReturnsUnknownDebtId_throwsIllegalArgumentException() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    100_000L, 600, 20_000L, 10, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));
            PayoffStrategy unknownDebt = (debts, extraCents) -> Map.of("unknown-loan", 5_000L);

            assertThrows(IllegalArgumentException.class,
                    () -> allocator.allocate(List.of(debt), budget, schedule, unknownDebt));
        }

        @Test
        @DisplayName("Rejects an extra payment above the balance left after the minimum")
        void strategyOverpaysDebtAfterMinimum_throwsIllegalArgumentException() {
            Debt debt = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    20_000L, 600, 15_000L, 10, null);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));
            PayoffStrategy overpaying = (debts, extraCents) -> Map.of(debt.id(), 6_000L);

            assertThrows(IllegalArgumentException.class,
                    () -> allocator.allocate(List.of(debt), budget, schedule, overpaying));
        }
    }

    @Nested
    @DisplayName("Insufficient cash")
    class InsufficientCash {
        @Test
        @DisplayName("Reports shortfall after paying minimums in due-date order")
        void availableBelowRequiredMinimums_reportsShortfall() {
            Debt first = new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                    100_000L, 600, 15_000L, 10, null);
            Debt second = new Debt("loan-2", "Other student loan", DebtType.STUDENT_LOAN,
                    100_000L, 800, 10_000L, 12, null);
            Budget tightBudget = new Budget(120_000L, 90_000L, 10_000L);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(first, second), tightBudget, schedule, strategy);

            assertEquals(List.of(
                    new PaymentAction(first.id(), 15_000L, LocalDate.of(2026, 4, 10)),
                    new PaymentAction(second.id(), 5_000L, LocalDate.of(2026, 4, 12))),
                    result.minimumPaymentActions());
            assertEquals(25_000L, result.totalMinimumsDueCents());
            assertEquals(5_000L, result.shortfallCents());
            assertEquals(List.of(), result.extraPaymentActions());
        }

        @Test
        @DisplayName("Generates no extra actions when available cash is zero")
        void availableCashIsZero_noExtraActions() {
            Budget zeroAvailable = new Budget(100_000L, 90_000L, 10_000L);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debtDueOn(10)), zeroAvailable, schedule, strategy);

            assertEquals(0L, result.availableCents());
            assertEquals(List.of(), result.extraPaymentActions());
        }

        @Test
        @DisplayName("Generates no extra actions when available cash is negative")
        void availableCashIsNegative_noExtraActions() {
            Budget negativeAvailable = new Budget(100_000L, 95_000L, 10_000L);
            PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 4, 5));

            AllocationResult result = allocator.allocate(List.of(debtDueOn(10)), negativeAvailable, schedule, strategy);

            assertEquals(-5_000L, result.availableCents());
            assertEquals(List.of(), result.extraPaymentActions());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        @DisplayName("Rejects a non-biweekly pay schedule")
        void monthlySchedule_throwsIllegalArgumentException() {
            PaySchedule schedule = new PaySchedule(PayFrequency.MONTHLY, LocalDate.of(2026, 4, 5));

            assertThrows(IllegalArgumentException.class,
                    () -> allocator.allocate(List.of(debtDueOn(10)), budget, schedule, strategy));
        }
    }

    private static Debt debtDueOn(int dueDayOfMonth) {
        return new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                50_000L, 600, 10_000L, dueDayOfMonth, null);
    }
}
