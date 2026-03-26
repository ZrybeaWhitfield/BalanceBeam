package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.balancebeam.core.model.Debt;

public class DebtPayoffSimulator {
    private static final int MAX_MONTHS = 360;

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
        Map<String, Long> runningBalances = new HashMap<>();
        for (Debt debt : debts) {
            runningBalances.put(debt.id(), debt.balanceCents());
        }
        for (Map.Entry<String, Long> entry : paymentsPerPeriod.entrySet()) {
            if (!runningBalances.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Unknown debtId in paymentsPerPeriod: " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Payment amount must be >= 0 for debtId: " + entry.getKey());
            }
        }

        if (runningBalances.values().stream().allMatch(b -> b == 0)) {
            return new SimulationResult(0L, 0, startDate, false, Map.copyOf(runningBalances), List.of());
        }

        long totalInterestPaidCents = 0;
        int periodsSimulated = 0;

        List<PeriodSnapshot> snapshots = new ArrayList<>();

        for (int period = 0; period < MAX_MONTHS; period++) {
            Map<String, Long> interestCharged = captureSnapshots ? new HashMap<>() : null;
            Map<String, Long> principalApplied = captureSnapshots ? new HashMap<>() : null;
            for (Debt debt : debts) {
                long balance = runningBalances.get(debt.id());
                if (balance == 0) {
                    if (captureSnapshots) {
                        interestCharged.put(debt.id(), 0L);
                        principalApplied.put(debt.id(), 0L);
                    }
                    continue;
                }

                long interest = Math.ceilDiv(balance * debt.aprBasisPoints(), 120_000L);
                long payment = paymentsPerPeriod.getOrDefault(debt.id(), 0L);

                long interestPaid = Math.min(payment, interest);
                long newBalance = Math.max(0L, balance + interest - payment);
                long principalPaid = balance - newBalance;

                runningBalances.put(debt.id(), newBalance);
                totalInterestPaidCents += interestPaid;
                if (captureSnapshots) {
                    interestCharged.put(debt.id(), interest);
                    principalApplied.put(debt.id(), principalPaid);
                }
            }

            periodsSimulated++;

            if (captureSnapshots) {
                snapshots.add(new PeriodSnapshot(
                        period,
                        Map.copyOf(runningBalances),
                        Map.copyOf(interestCharged),
                        Map.copyOf(principalApplied)));
            }

            if (runningBalances.values().stream().allMatch(b -> b == 0)) {
                break;
            }
        }

        boolean neverPaysOff = runningBalances.values().stream().anyMatch(b -> b > 0);

        LocalDate estimatedPayoffDate = neverPaysOff ? null : startDate.plusMonths(periodsSimulated);
        return new SimulationResult(
                totalInterestPaidCents, periodsSimulated, estimatedPayoffDate, neverPaysOff,
                Map.copyOf(runningBalances), List.copyOf(snapshots));
    }
}