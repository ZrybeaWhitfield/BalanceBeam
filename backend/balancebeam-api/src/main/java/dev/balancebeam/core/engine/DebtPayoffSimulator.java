package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

public class DebtPayoffSimulator {
    private static final int MAX_PERIODS = 360;

    private final boolean captureSnapshots;

    public DebtPayoffSimulator(boolean captureSnapshots) {
        this.captureSnapshots = captureSnapshots;
    }

    /**
     * Simulates month-by-month payoff for a list of debts.
     *
     * @param debts             → the debts to simulate
     * @param paymentsPerPeriod debtId → payment amount in cents applied each period
     * @param startDate         → the calendar date the simulation starts from
     * @return the simulation result including summary and optional snapshots
     */

    public SimulationResult simulate(List<Debt> debts, Map<String, Long> paymentsPerPeriod, LocalDate startDate) {
        throw new UnsupportedOperationException("Method not yet implemeted");

    }
}