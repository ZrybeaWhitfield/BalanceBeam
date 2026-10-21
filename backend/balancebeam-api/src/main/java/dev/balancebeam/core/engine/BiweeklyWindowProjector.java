package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.plan.DebtBalance;
import dev.balancebeam.core.plan.PaymentAction;
import dev.balancebeam.core.plan.PaymentKind;
import dev.balancebeam.core.plan.ProjectedPayment;
import dev.balancebeam.core.plan.ProjectionWindow;

/** Processes one paycheck window, updating the supplied interest states in place. */
final class BiweeklyWindowProjector {
    private final BiweeklyAllocator allocator = new BiweeklyAllocator();

    ProjectionWindow project(
            List<Debt> debts,
            Map<String, DebtInterestState> states,
            Budget budget,
            LocalDate windowStart,
            PayoffStrategy strategy) {
        Objects.requireNonNull(debts, "debts cannot be null");
        Objects.requireNonNull(states, "states cannot be null");
        Objects.requireNonNull(budget, "budget cannot be null");
        Objects.requireNonNull(windowStart, "windowStart cannot be null");
        Objects.requireNonNull(strategy, "strategy cannot be null");

        List<Debt> debtTerms = List.copyOf(debts).stream().sorted(Comparator.comparing(Debt::id)).toList();
        Map<String, DebtInterestState> interestStates = new TreeMap<>(Map.copyOf(states));
        validateDebtIds(debtTerms, interestStates);
        LocalDate windowEnd = windowStart.plusDays(14);
        long available = Math.subtractExact(
                Math.subtractExact(budget.paycheckNetCents(), budget.essentialSpendPerPaycheckCents()),
                budget.bufferCents());

        Map<String, Long> openingInterestCharges = new HashMap<>();
        List<Debt> openingDebts = new ArrayList<>();
        for (Debt debt : debtTerms) {
            DebtInterestState state = interestStates.get(debt.id());
            state.postInterest();
            openingInterestCharges.put(debt.id(), state.totalInterestChargedCents());
            openingDebts.add(withBalance(debt, state.balance().totalBalanceCents()));
        }

        AllocationResult allocation = allocator.allocate(
                List.copyOf(openingDebts), budget, new PaySchedule(PayFrequency.BIWEEKLY, windowStart), strategy);
        List<ScheduledPayment> schedule = paymentSchedule(allocation);
        Map<String, LocalDate> plannedPayoffDates = plannedPayoffDates(openingDebts, schedule);
        Map<LocalDate, List<ScheduledPayment>> paymentsByDate = new TreeMap<>();
        long reserved = 0L;
        for (ScheduledPayment payment : schedule) {
            paymentsByDate.computeIfAbsent(payment.action().scheduledDate(), date -> new ArrayList<>()).add(payment);
            reserved = Math.addExact(reserved, payment.action().paymentAmountCents());
        }
        long cashRemaining = Math.max(available, 0L);
        if (reserved > cashRemaining) {
            throw new IllegalArgumentException("scheduled payments cannot exceed available cash");
        }

        List<ProjectedPayment> actualPayments = new ArrayList<>();
        for (LocalDate date = windowStart; date.isBefore(windowEnd); date = date.plusDays(1)) {
            List<ScheduledPayment> scheduledToday = paymentsByDate.getOrDefault(date, List.of());
            for (ScheduledPayment payment : scheduledToday) {
                reserved -= payment.action().paymentAmountCents();
            }
            List<ProjectedPayment> paidToday = applyScheduledPayments(scheduledToday, interestStates);
            for (ProjectedPayment payment : paidToday) {
                cashRemaining -= payment.action().paymentAmountCents();
            }

            List<Debt> eligiblePayoffs = eligiblePayoffs(debtTerms, interestStates, plannedPayoffDates, date);
            cashRemaining -= applyPayoffAdjustments(
                    eligiblePayoffs, interestStates, paidToday, cashRemaining - reserved, strategy);
            actualPayments.addAll(paidToday);

            for (DebtInterestState state : interestStates.values()) {
                state.accrueDay();
            }
        }

        Map<String, Long> interestCharges = new TreeMap<>();
        Map<String, DebtBalance> endingBalances = new TreeMap<>();
        for (Map.Entry<String, DebtInterestState> entry : interestStates.entrySet()) {
            String debtId = entry.getKey();
            DebtInterestState state = entry.getValue();
            state.postInterest();
            interestCharges.put(debtId,
                    Math.subtractExact(state.totalInterestChargedCents(), openingInterestCharges.get(debtId)));
            endingBalances.put(debtId, state.balance());
        }
        return new ProjectionWindow(windowStart, windowEnd, actualPayments, interestCharges, endingBalances,
                available, budget.bufferCents(), allocation.totalMinimumsDueCents(), allocation.shortfallCents());
    }

    private static void validateDebtIds(List<Debt> debts, Map<String, DebtInterestState> states) {
        Set<String> debtIds = new HashSet<>();
        Set<DebtInterestState> distinctStates = new HashSet<>();
        for (Debt debt : debts) {
            if (!debtIds.add(debt.id())) {
                throw new IllegalArgumentException("duplicate debt ID: " + debt.id());
            }
        }
        if (!debtIds.equals(states.keySet())) {
            throw new IllegalArgumentException("debt terms and interest states must contain the same debt IDs");
        }
        for (DebtInterestState state : states.values()) {
            if (!distinctStates.add(state)) {
                throw new IllegalArgumentException("each debt must have its own interest state");
            }
        }
    }

    private static List<ScheduledPayment> paymentSchedule(AllocationResult allocation) {
        List<ScheduledPayment> schedule = new ArrayList<>();
        for (PaymentAction action : allocation.minimumPaymentActions()) {
            schedule.add(new ScheduledPayment(action, PaymentKind.MINIMUM));
        }
        for (PaymentAction action : allocation.extraPaymentActions()) {
            schedule.add(new ScheduledPayment(action, PaymentKind.EXTRA));
        }
        schedule.sort(Comparator.comparing((ScheduledPayment payment) -> payment.action().scheduledDate())
                .thenComparing(payment -> payment.action().debtId())
                .thenComparing(ScheduledPayment::kind));
        return schedule;
    }

    private static Map<String, LocalDate> plannedPayoffDates(List<Debt> openingDebts, List<ScheduledPayment> schedule) {
        Map<String, Long> plannedAmounts = new HashMap<>();
        Map<String, LocalDate> lastPaymentDates = new HashMap<>();
        for (ScheduledPayment payment : schedule) {
            PaymentAction action = payment.action();
            plannedAmounts.merge(action.debtId(), action.paymentAmountCents(), Math::addExact);
            lastPaymentDates.merge(action.debtId(), action.scheduledDate(),
                    (first, second) -> first.isAfter(second) ? first : second);
        }
        Map<String, LocalDate> payoffDates = new HashMap<>();
        for (Debt debt : openingDebts) {
            if (debt.balanceCents() > 0 && plannedAmounts.getOrDefault(debt.id(), 0L) == debt.balanceCents()) {
                payoffDates.put(debt.id(), lastPaymentDates.get(debt.id()));
            }
        }
        return payoffDates;
    }

    private static List<ProjectedPayment> applyScheduledPayments(
            List<ScheduledPayment> schedule, Map<String, DebtInterestState> states) {
        List<ProjectedPayment> payments = new ArrayList<>();
        for (ScheduledPayment scheduled : schedule) {
            PaymentAction action = scheduled.action();
            long actual = states.get(action.debtId()).applyPayment(action.paymentAmountCents());
            if (actual > 0) {
                payments.add(new ProjectedPayment(
                        new PaymentAction(action.debtId(), actual, action.scheduledDate()), scheduled.kind(), 0L));
            }
        }
        return payments;
    }

    private static List<Debt> eligiblePayoffs(List<Debt> debtTerms, Map<String, DebtInterestState> states,
            Map<String, LocalDate> payoffDates, LocalDate date) {
        List<Debt> eligible = new ArrayList<>();
        for (Debt debt : debtTerms) {
            if (date.equals(payoffDates.get(debt.id()))) {
                long remaining = states.get(debt.id()).balance().totalBalanceCents();
                if (remaining > 0) {
                    eligible.add(withBalance(debt, remaining));
                }
            }
        }
        return List.copyOf(eligible);
    }

    private static long applyPayoffAdjustments(List<Debt> eligible, Map<String, DebtInterestState> states,
            List<ProjectedPayment> payments, long spareCash, PayoffStrategy strategy) {
        if (spareCash == 0 || eligible.isEmpty()) {
            return 0L;
        }
        Map<String, Long> adjustments = new TreeMap<>(Map.copyOf(strategy.allocateExtra(eligible, spareCash)));
        validateAdjustments(adjustments, eligible, spareCash);

        Map<String, Integer> finalPaymentIndexes = new HashMap<>();
        for (int index = 0; index < payments.size(); index++) {
            finalPaymentIndexes.put(payments.get(index).action().debtId(), index);
        }
        long totalAdjusted = 0L;
        for (Map.Entry<String, Long> entry : adjustments.entrySet()) {
            String debtId = entry.getKey();
            long actual = states.get(debtId).applyPayment(entry.getValue());
            int index = finalPaymentIndexes.get(debtId);
            ProjectedPayment original = payments.get(index);
            PaymentAction action = original.action();
            payments.set(index, new ProjectedPayment(
                    new PaymentAction(debtId, Math.addExact(action.paymentAmountCents(), actual), action.scheduledDate()),
                    original.kind(), Math.addExact(original.payoffAdjustmentCents(), actual)));
            totalAdjusted = Math.addExact(totalAdjusted, actual);
        }
        return totalAdjusted;
    }

    private static void validateAdjustments(Map<String, Long> adjustments, List<Debt> eligible, long spareCash) {
        Map<String, Long> eligibleBalances = new HashMap<>();
        for (Debt debt : eligible) {
            eligibleBalances.put(debt.id(), debt.balanceCents());
        }
        long remainingCash = spareCash;
        for (Map.Entry<String, Long> entry : adjustments.entrySet()) {
            Long balance = eligibleBalances.get(entry.getKey());
            if (balance == null) {
                throw new IllegalArgumentException("payoff adjustment references an ineligible debt: " + entry.getKey());
            }
            long amount = entry.getValue();
            if (amount <= 0 || amount > balance || amount > remainingCash) {
                throw new IllegalArgumentException("payoff adjustments must be positive and within the debt balance and spare cash");
            }
            remainingCash -= amount;
        }
    }

    private static Debt withBalance(Debt debt, long balance) {
        return new Debt(debt.id(), debt.name(), debt.type(), balance, debt.aprBasisPoints(),
                debt.minimumPaymentCents(), debt.dueDayOfMonth(), debt.creditLimitCents());
    }

    private record ScheduledPayment(PaymentAction action, PaymentKind kind) {
    }
}
