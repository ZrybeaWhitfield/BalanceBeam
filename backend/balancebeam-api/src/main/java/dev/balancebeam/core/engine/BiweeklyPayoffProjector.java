package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.model.ProjectionOptions;
import dev.balancebeam.core.plan.DebtBalance;
import dev.balancebeam.core.plan.PayoffProjection;
import dev.balancebeam.core.plan.ProjectionAssumptions;
import dev.balancebeam.core.plan.ProjectionStatus;
import dev.balancebeam.core.plan.ProjectionWindow;

/** Projects biweekly payments and interest until payoff or the configured window limit. */
public final class BiweeklyPayoffProjector {
    private static final String MODEL_VERSION = "biweekly-v1";
    private final BiweeklyWindowProjector windowProjector = new BiweeklyWindowProjector();

    public PayoffProjection project(
            List<ProjectionDebt> debts,
            Budget budget,
            PaySchedule schedule,
            PayoffStrategy strategy,
            ProjectionOptions options) {
        Objects.requireNonNull(debts, "debts cannot be null");
        Objects.requireNonNull(budget, "budget cannot be null");
        Objects.requireNonNull(schedule, "schedule cannot be null");
        Objects.requireNonNull(strategy, "strategy cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        List<ProjectionDebt> inputs = List.copyOf(debts);
        if (schedule.frequency() != PayFrequency.BIWEEKLY) {
            throw new IllegalArgumentException("BiweeklyPayoffProjector requires BIWEEKLY pay frequency");
        }
        LocalDate startDate = schedule.nextPayDate();
        // Validate the full requested date range before calculating any windows.
        startDate.plusWeeks(2L * options.maxWindows());

        Map<String, DebtInterestState> states = new TreeMap<>();
        Map<String, DebtBalance> openingBalances = new TreeMap<>();
        Set<String> assumedZeroInterestDebtIds = new TreeSet<>();
        for (ProjectionDebt input : inputs) {
            Debt debt = input.debt();
            DebtInterestState state = new DebtInterestState(input);
            if (states.putIfAbsent(debt.id(), state) != null) {
                throw new IllegalArgumentException("duplicate debt ID: " + debt.id());
            }
            openingBalances.put(debt.id(), state.balance());
            if (debt.type() == DebtType.STUDENT_LOAN && input.openingUnpaidInterestCents() == null) {
                assumedZeroInterestDebtIds.add(debt.id());
            }
        }
        ProjectionAssumptions assumptions = new ProjectionAssumptions(MODEL_VERSION, assumedZeroInterestDebtIds);
        List<Debt> debtTerms = inputs.stream().map(ProjectionDebt::debt).toList();
        List<ProjectionWindow> windows = new ArrayList<>();
        Map<String, DebtBalance> currentBalances = openingBalances;
        LocalDate windowStart = startDate;

        while (windows.size() < options.maxWindows() && !allPaidOff(currentBalances)) {
            ProjectionWindow window = windowProjector.project(debtTerms, states, budget, windowStart, strategy);
            windows.add(window);
            currentBalances = window.endingBalances();
            windowStart = window.endDate();
        }

        boolean paidOff = allPaidOff(currentBalances);
        LocalDate payoffDate = null;
        if (paidOff) {
            payoffDate = windows.isEmpty()
                    ? startDate
                    : windows.getLast().payments().getLast().action().scheduledDate();
        }
        return new PayoffProjection(startDate, openingBalances, windows,
                paidOff ? ProjectionStatus.PAID_OFF : ProjectionStatus.HORIZON_REACHED,
                payoffDate, options, assumptions);
    }

    private static boolean allPaidOff(Map<String, DebtBalance> balances) {
        return balances.values().stream().allMatch(balance -> balance.totalBalanceCents() == 0);
    }
}
