package dev.balancebeam.core.plan;

/**
 * The purpose of a scheduled payment in a payoff projection.
 * A payoff adjustment retains the kind of the payment it adjusts.
 */
public enum PaymentKind {
    /** Money allocated toward a required minimum payment. */
    MINIMUM,

    /** Additional money allocated by the selected payoff strategy. */
    EXTRA,
}
