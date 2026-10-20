package dev.balancebeam.core.plan;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import dev.balancebeam.core.model.ProjectionOptions;

/**
 * A complete forecast. A null payoff date means the forecast limit was reached;
 * an initially debt-free portfolio uses the start date and has no windows.
 */
public record PayoffProjection(
        LocalDate startDate,
        Map<String, DebtBalance> openingBalances,
        List<ProjectionWindow> windows,
        ProjectionStatus status,
        LocalDate payoffDate,
        ProjectionOptions options,
        ProjectionAssumptions assumptions) {
    public PayoffProjection {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(openingBalances, "openingBalances cannot be null");
        Objects.requireNonNull(windows, "windows cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        Objects.requireNonNull(assumptions, "assumptions cannot be null");

        openingBalances = copyOpeningBalances(openingBalances);
        windows = List.copyOf(windows);
        if (windows.size() > options.maxWindows()) {
            throw new IllegalArgumentException("windows cannot exceed the configured limit");
        }
        if (!openingBalances.keySet().containsAll(assumptions.assumedZeroInterestDebtIds())) {
            throw new IllegalArgumentException("assumed debt IDs must exist in the opening balances");
        }

        Map<String, DebtBalance> currentBalances = openingBalances;
        LocalDate nextWindowStart = startDate;
        for (ProjectionWindow window : windows) {
            if (allPaidOff(currentBalances)) {
                throw new IllegalArgumentException("windows cannot continue after all debts are paid off");
            }
            if (!window.startDate().equals(nextWindowStart)) {
                throw new IllegalArgumentException("windows must be consecutive and begin on the projection start date");
            }
            if (!window.endingBalances().keySet().equals(openingBalances.keySet())) {
                throw new IllegalArgumentException("every window must contain the same debt IDs as the opening balances");
            }
            validateBalanceChanges(currentBalances, window);
            currentBalances = window.endingBalances();
            nextWindowStart = window.endDate();
        }

        switch (status) {
            case PAID_OFF -> {
                if (!allPaidOff(currentBalances)) {
                    throw new IllegalArgumentException("PAID_OFF requires zero remaining balances");
                }
                Objects.requireNonNull(payoffDate, "payoffDate is required for PAID_OFF");
                LocalDate expectedPayoffDate = windows.isEmpty()
                        ? startDate
                        : windows.getLast().payments().getLast().action().scheduledDate();
                if (!payoffDate.equals(expectedPayoffDate)) {
                    throw new IllegalArgumentException("payoffDate must match the final payment date or debt-free start date");
                }
            }
            case HORIZON_REACHED -> {
                if (allPaidOff(currentBalances)) {
                    throw new IllegalArgumentException("HORIZON_REACHED requires debt remaining");
                }
                if (windows.size() != options.maxWindows()) {
                    throw new IllegalArgumentException("HORIZON_REACHED requires the configured number of windows");
                }
                if (payoffDate != null) {
                    throw new IllegalArgumentException("payoffDate must be null for HORIZON_REACHED");
                }
            }
        }
    }

    public long totalPaidCents() {
        return windows.stream().mapToLong(ProjectionWindow::totalPaidCents).reduce(0L, Math::addExact);
    }

    public long totalInterestChargedCents() {
        return windows.stream().mapToLong(ProjectionWindow::totalInterestChargedCents).reduce(0L, Math::addExact);
    }

    public Map<String, DebtBalance> endingBalances() {
        return windows.isEmpty() ? openingBalances : windows.getLast().endingBalances();
    }

    public boolean hasMinimumShortfall() {
        return windows.stream().anyMatch(ProjectionWindow::hasMinimumShortfall);
    }

    private static boolean allPaidOff(Map<String, DebtBalance> balances) {
        return balances.values().stream().allMatch(balance -> balance.totalBalanceCents() == 0);
    }

    private static void validateBalanceChanges(Map<String, DebtBalance> openingBalances, ProjectionWindow window) {
        Map<String, Long> paymentsByDebt = new HashMap<>();
        for (ProjectedPayment payment : window.payments()) {
            PaymentAction action = payment.action();
            paymentsByDebt.merge(action.debtId(), action.paymentAmountCents(), Math::addExact);
        }

        for (Map.Entry<String, DebtBalance> entry : openingBalances.entrySet()) {
            String debtId = entry.getKey();
            DebtBalance openingBalance = entry.getValue();
            DebtBalance endingBalance = window.endingBalances().get(debtId);
            long opening = openingBalance.totalBalanceCents();
            long ending = endingBalance.totalBalanceCents();
            long interest = window.interestChargedCents().get(debtId);
            long paid = paymentsByDebt.getOrDefault(debtId, 0L);

            if (opening == 0 && (interest != 0 || paid != 0)) {
                throw new IllegalArgumentException("paid-off debts cannot accrue interest or receive payments: " + debtId);
            }
            if (endingBalance.principalCents() > openingBalance.principalCents()) {
                throw new IllegalArgumentException("principal cannot increase during a projection: " + debtId);
            }
            long principalReduction = openingBalance.principalCents() - endingBalance.principalCents();
            long availableForPrincipal = Math.max(paid - openingBalance.unpaidInterestCents(), 0L);
            if (principalReduction > availableForPrincipal) {
                throw new IllegalArgumentException("principal reduction cannot exceed payments remaining after opening unpaid interest: " + debtId);
            }
            // Comparing net changes avoids overflow in opening balance + interest.
            if (opening - ending != paid - interest) {
                throw new IllegalArgumentException("ending balance must equal opening balance plus interest minus payments: " + debtId);
            }
        }
    }

    private static Map<String, DebtBalance> copyOpeningBalances(Map<String, DebtBalance> balances) {
        Map<String, DebtBalance> copy = new TreeMap<>();
        for (Map.Entry<String, DebtBalance> entry : balances.entrySet()) {
            String debtId = Objects.requireNonNull(entry.getKey(), "opening balance debt IDs cannot be null");
            if (debtId.isBlank()) {
                throw new IllegalArgumentException("opening balance debt IDs cannot be blank");
            }
            DebtBalance balance = Objects.requireNonNull(entry.getValue(), "opening balances cannot contain null values");
            copy.put(debtId, balance);
        }
        return Collections.unmodifiableMap(copy);
    }
}
