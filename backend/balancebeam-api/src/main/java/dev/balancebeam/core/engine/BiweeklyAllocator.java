package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.plan.PaymentAction;

public class BiweeklyAllocator {
    /**
     * Allocates one paycheck. Minimums are due in the 14-day window starting on
     * {@code nextPayDate}, inclusive, and ending on the following pay date,
     * exclusive. A debt's due day is between 1 and 28, so it exists in every month.
     */
    public AllocationResult allocate(
            List<Debt> debts,
            Budget budget,
            PaySchedule paySchedule,
            PayoffStrategy strategy) {

        Objects.requireNonNull(debts, "debts must not be null");
        Objects.requireNonNull(budget, "budget must not be null");
        Objects.requireNonNull(paySchedule, "paySchedule must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        if (paySchedule.frequency() != PayFrequency.BIWEEKLY) {
            throw new IllegalArgumentException("BiweeklyAllocator requires BIWEEKLY pay frequency");
        }

        long availableCents = budget.paycheckNetCents() - budget.essentialSpendPerPaycheckCents() - budget.bufferCents();

        LocalDate windowStart = paySchedule.nextPayDate();
        LocalDate windowEnd = windowStart.plusDays(14);

        List<Debt> debtsInWindow = new ArrayList<>();
        Map<String, LocalDate> dueDates = new HashMap<>();

        for (Debt debt : debts) {
            if (debt.balanceCents() == 0) {
                continue;
            }

            LocalDate dueDate = dueDateWithinWindow(debt.dueDayOfMonth(), windowStart, windowEnd);

            if (dueDate != null) {
                debtsInWindow.add(debt);
                dueDates.put(debt.id(), dueDate);
            }
        }

        debtsInWindow.sort(Comparator.comparing(d -> dueDates.get(d.id())));

        long totalMinimumsDueCents = 0;
        long remaining = Math.max(availableCents, 0);
        long minimumsPaidCents = 0;
        List<PaymentAction> minimumPaymentActions = new ArrayList<>();
        Map<String, Long> minimumsPaidByDebt = new HashMap<>();

        for (Debt debt : debtsInWindow) {
            long effectiveMinimum = Math.min(debt.balanceCents(), debt.minimumPaymentCents());
            totalMinimumsDueCents += effectiveMinimum;
            long amountCanPay = Math.min(effectiveMinimum, remaining);

            if (amountCanPay > 0) {
                minimumPaymentActions.add(new PaymentAction(debt.id(), amountCanPay, dueDates.get(debt.id())));
                minimumsPaidByDebt.put(debt.id(), amountCanPay);
                remaining -= amountCanPay;
                minimumsPaidCents += amountCanPay;
            }
        }

        long shortfallCents = totalMinimumsDueCents - minimumsPaidCents;

        List<PaymentAction> extraPaymentActions = new ArrayList<>();

        if (remaining > 0) {
            List<Debt> debtsAfterMinimums = new ArrayList<>(debts.size());
            Map<String, Long> balancesAfterMinimums = new HashMap<>();
            for (Debt debt : debts) {
                long minimumPaid = minimumsPaidByDebt.getOrDefault(debt.id(), 0L);
                Debt debtAfterMinimum;
                if (minimumPaid == 0) {
                    debtAfterMinimum = debt;
                } else {
                    debtAfterMinimum = new Debt(
                            debt.id(), debt.name(), debt.type(), debt.balanceCents() - minimumPaid,
                            debt.aprBasisPoints(), debt.minimumPaymentCents(), debt.dueDayOfMonth(),
                            debt.creditLimitCents());
                }
                debtsAfterMinimums.add(debtAfterMinimum);
                balancesAfterMinimums.put(debt.id(), debtAfterMinimum.balanceCents());
            }

            Map<String, Long> extraAllocations = strategy.allocateExtra(debtsAfterMinimums, remaining);
            long cashLeftForExtras = remaining;

            for (Map.Entry<String, Long> entry : extraAllocations.entrySet()) {
                String debtId = entry.getKey();
                long amount = entry.getValue();
                Long balanceAfterMinimum = balancesAfterMinimums.get(debtId);

                if (amount <= 0 || amount > cashLeftForExtras) {
                    throw new IllegalArgumentException("strategy allocations must be positive and within available cash");
                }
                if (balanceAfterMinimum == null) {
                    throw new IllegalArgumentException("strategy allocation has unknown debt ID: " + debtId);
                }
                if (amount > balanceAfterMinimum) {
                    throw new IllegalArgumentException("strategy allocation exceeds remaining balance for debt: " + debtId);
                }

                cashLeftForExtras -= amount;
                extraPaymentActions.add(new PaymentAction(debtId, amount, windowStart));
            }

            extraPaymentActions.sort(Comparator.comparing(PaymentAction::debtId));
        }

        return new AllocationResult(minimumPaymentActions, extraPaymentActions, availableCents, budget.bufferCents(), totalMinimumsDueCents, shortfallCents);
    }

    private LocalDate dueDateWithinWindow(int dueDayOfMonth, LocalDate windowStart, LocalDate windowEnd) {
        LocalDate candidate = windowStart.withDayOfMonth(dueDayOfMonth);
        if(!candidate.isBefore(windowStart) && candidate.isBefore(windowEnd)) {
            return candidate;
        }

        candidate = windowStart.plusMonths(1).withDayOfMonth(dueDayOfMonth);

        if (!candidate.isBefore(windowStart) && candidate.isBefore(windowEnd)) {
            return candidate;
        }

        return null;
    }
}
