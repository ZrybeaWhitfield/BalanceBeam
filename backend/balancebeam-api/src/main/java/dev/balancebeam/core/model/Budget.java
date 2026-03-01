package dev.balancebeam.core.model;

public record Budget(
        long paycheckNetCents,
        long essentialSpendPerPaycheckCents,
        long bufferCents) {
    public Budget {
        if (paycheckNetCents < 0) {
            throw new IllegalArgumentException("paycheckNetCents cannot be negative");
        }
        if (essentialSpendPerPaycheckCents < 0) {
            throw new IllegalArgumentException("essentialSpendPerPaycheckCents cannot be negative");
        }
        if (bufferCents < 0) {
            throw new IllegalArgumentException("bufferCents cannot be negative");
        }
    }
}