package dev.balancebeam.examples;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.balancebeam.core.engine.AvalancheStrategy;
import dev.balancebeam.core.engine.BiweeklyPayoffProjector;
import dev.balancebeam.core.engine.PayoffStrategy;
import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.model.ProjectionOptions;
import dev.balancebeam.core.plan.DebtBalance;
import dev.balancebeam.core.plan.PayoffProjection;
import dev.balancebeam.core.plan.PaymentAction;
import dev.balancebeam.core.plan.ProjectedPayment;
import dev.balancebeam.core.plan.ProjectionWindow;

/** Editable console example with synthetic values for inspecting a forecast without starting the API. */
public final class BiweeklyProjectionExample {
    public static void main(String[] args) {
        // Money is in cents and APR is in basis points. Due days are 1–28 so they exist in every month.
        List<ProjectionDebt> debts = List.of(new ProjectionDebt(
                new Debt("loan-1", "Student loan", DebtType.STUDENT_LOAN,
                        37_025L, 1000, 40_000L, 13, null),
                500L));
        Budget budget = new Budget(200_000L, 150_000L, 10_000L);
        PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 10, 6));
        PayoffStrategy strategy = new AvalancheStrategy();
        ProjectionOptions options = new ProjectionOptions();

        PayoffProjection projection = new BiweeklyPayoffProjector().project(debts, budget, schedule, strategy, options);

        System.out.println("Biweekly payoff forecast — " + strategy.getClass().getSimpleName());
        System.out.printf("Paycheck %s | Essentials %s | Protected buffer %s%n",
                money(budget.paycheckNetCents()), money(budget.essentialSpendPerPaycheckCents()), money(budget.bufferCents()));
        System.out.printf("Start %s | Window limit %d | Model %s%n",
                projection.startDate(), options.maxWindows(), projection.assumptions().modelVersion());
        System.out.println("Assumed zero opening interest: " + projection.assumptions().assumedZeroInterestDebtIds());
        printBalances("Opening portfolio", projection.openingBalances());

        Map<String, DebtBalance> openingBalances = projection.openingBalances();
        for (ProjectionWindow window : projection.windows()) {
            System.out.printf("%nWindow %s to %s (end exclusive)%n", window.startDate(), window.endDate());
            System.out.printf("  Available %s | Minimums due %s | Minimum shortfall %s%n",
                    money(window.availableCents()), money(window.totalMinimumsDueCents()), money(window.minimumShortfallCents()));
            printBalances("  Opening balances", openingBalances);

            long cashRemaining = Math.max(window.availableCents(), 0L);
            if (window.payments().isEmpty()) {
                System.out.println("  No payments");
            }
            for (ProjectedPayment payment : window.payments()) {
                PaymentAction action = payment.action();
                cashRemaining = Math.subtractExact(cashRemaining, action.paymentAmountCents());
                System.out.printf("  %s | %s | %s | Paid %s (includes adjustment %s) | Cash remaining %s%n",
                        action.scheduledDate(), action.debtId(), payment.kind(), money(action.paymentAmountCents()),
                        money(payment.payoffAdjustmentCents()), money(cashRemaining));
            }
            for (Map.Entry<String, Long> entry : window.interestChargedCents().entrySet()) {
                System.out.printf("  %s | New interest charged %s%n", entry.getKey(), money(entry.getValue()));
            }
            printBalances("  Ending balances", window.endingBalances());
            System.out.printf("  Paid %s | New interest %s | Unspent %s | Protected buffer %s%n",
                    money(window.totalPaidCents()), money(window.totalInterestChargedCents()),
                    money(window.unspentCents()), money(window.bufferCents()));
            openingBalances = window.endingBalances();
        }

        System.out.printf("%nStatus %s | Payoff date %s | Windows %d%n", projection.status(),
                projection.payoffDate() == null ? "Not reached within limit" : projection.payoffDate(),
                projection.windows().size());
        System.out.printf("Total paid %s | Total new interest %s | Any minimum shortfall %s%n",
                money(projection.totalPaidCents()), money(projection.totalInterestChargedCents()),
                projection.hasMinimumShortfall());
        printBalances("Ending portfolio", projection.endingBalances());
    }

    private static void printBalances(String label, Map<String, DebtBalance> balances) {
        System.out.println(label + ":");
        if (balances.isEmpty()) {
            System.out.println("    No debts");
        }
        for (Map.Entry<String, DebtBalance> entry : balances.entrySet()) {
            DebtBalance balance = entry.getValue();
            System.out.printf("    %s | Principal %s | Unpaid interest %s | Total %s%n", entry.getKey(),
                    money(balance.principalCents()), money(balance.unpaidInterestCents()), money(balance.totalBalanceCents()));
        }
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%s%d.%02d", cents < 0 ? "-$" : "$",
                Math.abs(cents / 100), Math.abs(cents % 100));
    }
}
