package dev.balancebeam.core.model;

/**
 * Limits the number of biweekly windows a payoff projection may calculate.
 * The projection ends sooner if all debts are paid off.
 *
 * @param maxWindows positive maximum number of 14-day windows
 */
public record ProjectionOptions(int maxWindows) {
    /** Default calculation limit: 780 biweekly windows, approximately 30 years. */
    public static final int DEFAULT_MAX_WINDOWS = 780;

    public ProjectionOptions {
        if (maxWindows <= 0) {
            throw new IllegalArgumentException("maxWindows must be positive");
        }
    }

    /** Creates options with the default calculation limit. */
    public ProjectionOptions() {
        this(DEFAULT_MAX_WINDOWS);
    }
}
