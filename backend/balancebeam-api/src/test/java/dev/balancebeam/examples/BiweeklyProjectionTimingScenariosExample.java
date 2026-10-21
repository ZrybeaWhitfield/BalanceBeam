package dev.balancebeam.examples;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.balancebeam.core.engine.BiweeklyPayoffProjector;
import dev.balancebeam.core.engine.PayoffStrategy;
import dev.balancebeam.core.model.Budget;
import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;
import dev.balancebeam.core.model.PayFrequency;
import dev.balancebeam.core.model.PaySchedule;
import dev.balancebeam.core.model.ProjectionDebt;
import dev.balancebeam.core.model.ProjectionOptions;
import dev.balancebeam.core.plan.PayoffProjection;
import dev.balancebeam.core.plan.ProjectionWindow;

/** Synthetic console examples for payoff timing and the next-payday window boundary. */
public final class BiweeklyProjectionTimingScenariosExample {
    private static final LocalDate START = LocalDate.of(2026, 10, 6);
    private static final PaySchedule SCHEDULE = new PaySchedule(PayFrequency.BIWEEKLY, START);
    private static final BiweeklyPayoffProjector PROJECTOR = new BiweeklyPayoffProjector();

    private BiweeklyProjectionTimingScenariosExample() {
    }

    public static void main(String[] args) {
        finalPaymentOnDueDate_preservesLaterMinimum();
        minimumDueOnNextPayday_belongsToFollowingWindow();
    }

    private static void finalPaymentOnDueDate_preservesLaterMinimum() {
        ProjectionDebt nearPayoff = card("near-payoff", 10_000L, 2999, 10_000L, 13);
        ProjectionDebt laterMinimum = card("later-minimum", 50_000L, 0, 10_000L, 18);
        PayoffStrategy reserveForDueDate = (debts, extraCents) -> debts.stream()
                .filter(debt -> debt.id().equals("near-payoff")
                        && debt.balanceCents() > 0 && debt.balanceCents() < 10_000L)
                .findFirst()
                .map(debt -> Map.of(debt.id(), Math.min(debt.balanceCents(), extraCents)))
                .orElseGet(Map::of);
        Budget budget = new Budget(100_000L, 30_000L, 5_000L); // $650 available after essentials and buffer.

        PayoffProjection projection = PROJECTOR.project(List.of(nearPayoff, laterMinimum), budget, SCHEDULE,
                reserveForDueDate, new ProjectionOptions(1));

        printProjection("Final payment on a due date; later minimum stays reserved", projection);
    }

    private static void minimumDueOnNextPayday_belongsToFollowingWindow() {
        ProjectionDebt card = card("boundary-card", 10_000L, 2999, 10_000L, 20);
        PayoffStrategy noExtras = (debts, extraCents) -> Map.of();
        Budget budget = new Budget(20_000L, 9_000L, 0L); // $110 available; only the $100 minimum is scheduled.

        PayoffProjection projection = PROJECTOR.project(List.of(card), budget, SCHEDULE,
                noExtras, new ProjectionOptions(2));

        printProjection("Minimum due on the next payday", projection);
    }

    private static void printProjection(String label, PayoffProjection projection) {
        System.out.printf("%n%s%n", label);
        for (int index = 0; index < projection.windows().size(); index++) {
            ProjectionWindow window = projection.windows().get(index);
            System.out.printf("  Window %d: %s to %s | minimums %s | shortfall %s%n",
                    index + 1, window.startDate(), window.endDate(),
                    money(window.totalMinimumsDueCents()), money(window.minimumShortfallCents()));
            System.out.printf("    Payments: %s%n", window.payments().stream()
                    .map(payment -> payment.action().scheduledDate() + " " + payment.action().debtId() + " "
                            + money(payment.action().paymentAmountCents()) + " (adjustment "
                            + money(payment.payoffAdjustmentCents()) + ")")
                    .toList());
            System.out.printf("    Interest charged: %s | Ending balances: %s%n",
                    window.interestChargedCents(), window.endingBalances());
        }
        System.out.printf("  Status %s | Payoff date %s | Total paid %s | Total interest %s%n",
                projection.status(), projection.payoffDate(), money(projection.totalPaidCents()),
                money(projection.totalInterestChargedCents()));
    }

    private static ProjectionDebt card(String id, long balanceCents, int aprBasisPoints,
            long minimumPaymentCents, int dueDayOfMonth) {
        long creditLimitCents = Math.max(100_000L, balanceCents);
        return new ProjectionDebt(new Debt(id, id, DebtType.CREDIT_CARD, balanceCents,
                aprBasisPoints, minimumPaymentCents, dueDayOfMonth, creditLimitCents));
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%s%d.%02d", cents < 0 ? "-$" : "$",
                Math.abs(cents / 100), Math.abs(cents % 100));
    }
}
