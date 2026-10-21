package dev.balancebeam.examples;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.balancebeam.core.engine.AvalancheStrategy;
import dev.balancebeam.core.engine.BiweeklyPayoffProjector;
import dev.balancebeam.core.engine.CashFlowIndexStrategy;
import dev.balancebeam.core.engine.PayoffStrategy;
import dev.balancebeam.core.engine.SnowballStrategy;
import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.model.ProjectionOptions;
import dev.balancebeam.core.plan.PayoffProjection;
import dev.balancebeam.core.plan.ProjectionWindow;

/** Runs synthetic tight and negative cash-flow examples with all three payoff strategies. */
public final class BiweeklyCreditCardBudgetStressExample {
    private static final List<ProjectionDebt> DEBTS = List.of(
            new ProjectionDebt(new Debt("card-1", "Credit card 1", DebtType.CREDIT_CARD,
                    487_520L, 2499, 14_500L, 3, 900_000L)),
            new ProjectionDebt(new Debt("card-2", "Credit card 2", DebtType.CREDIT_CARD,
                    732_045L, 1899, 22_000L, 11, 900_000L)),
            new ProjectionDebt(new Debt("card-3", "Credit card 3", DebtType.CREDIT_CARD,
                    194_560L, 2149, 6_500L, 21, 400_000L)));

    private BiweeklyCreditCardBudgetStressExample() {
    }

    public static void main(String[] args) {
        // Tight case: $2,300 income - $2,050 essentials - $100 buffer = $150 available.
        runBudgetCase("Tight budget", new Budget(230_000L, 205_000L, 10_000L));
        // Negative case: $2,100 income - $2,050 essentials - $100 buffer = -$50 available.
        runBudgetCase("Negative available cash", new Budget(210_000L, 205_000L, 10_000L));
    }

    private static void runBudgetCase(String label, Budget budget) {
        List<PayoffStrategy> strategies = List.of(
                new AvalancheStrategy(), new SnowballStrategy(), new CashFlowIndexStrategy());
        PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 10, 6));
        ProjectionOptions options = new ProjectionOptions();

        System.out.printf("%n%s | Income %s | Essentials %s | Buffer %s | Available %s%n",
                label, money(budget.paycheckNetCents()), money(budget.essentialSpendPerPaycheckCents()),
                money(budget.bufferCents()), money(budget.paycheckNetCents()
                        - budget.essentialSpendPerPaycheckCents() - budget.bufferCents()));
        for (PayoffStrategy strategy : strategies) {
            PayoffProjection projection = new BiweeklyPayoffProjector()
                    .project(DEBTS, budget, schedule, strategy, options);
            ProjectionWindow firstWindow = projection.windows().getFirst();
            System.out.printf("  %-22s | %s | payoff %s | windows %d | paid %s | interest %s | any shortfall %s%n",
                    strategy.getClass().getSimpleName(), projection.status(), projection.payoffDate(),
                    projection.windows().size(), money(projection.totalPaidCents()),
                    money(projection.totalInterestChargedCents()), projection.hasMinimumShortfall());
            System.out.printf("    First window: minimums %s, shortfall %s, payments %s%n",
                    money(firstWindow.totalMinimumsDueCents()), money(firstWindow.minimumShortfallCents()),
                    firstWindow.payments().stream()
                            .map(payment -> payment.action().debtId() + "=" + money(payment.action().paymentAmountCents()))
                            .toList());
            for (Map.Entry<String, Long> interest : firstWindow.interestChargedCents().entrySet()) {
                System.out.printf("    %s interest charged %s%n", interest.getKey(), money(interest.getValue()));
            }
        }
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%s%d.%02d", cents < 0 ? "-$" : "$",
                Math.abs(cents / 100), Math.abs(cents % 100));
    }
}
