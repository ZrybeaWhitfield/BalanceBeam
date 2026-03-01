package dev.balancebeam.core.model;

import java.util.Objects;

public record Debt(
        String id,
        String name,
        DebtType type,
        long balanceCents,
        int aprBasisPoints,
        long minimumPaymentCents,
        int dueDayOfMonth,
        Long creditLimitCents) {
    public Debt {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (balanceCents < 0) {
            throw new IllegalArgumentException("balanceCents cannot be negative");
        }
        if (aprBasisPoints < 0) {
            throw new IllegalArgumentException("aprBasisPoints cannot be negative");
        }
        if (minimumPaymentCents < 0) {
            throw new IllegalArgumentException("minimumPaymentCents cannot be negative");
        }
        if (dueDayOfMonth < 1 || dueDayOfMonth > 28) {
            throw new IllegalArgumentException("dueDayOfMonth must be between 1 and 28");
        }
        if (type == DebtType.CREDIT_CARD) {
            if (creditLimitCents == null) {
                throw new IllegalArgumentException("creditLimitCents must be provided for CREDIT_CARD debts");
            }
            if (creditLimitCents <= 0) {
                throw new IllegalArgumentException("creditLimitCents must be > 0 for CREDIT_CARD debts");
            }
        }
        if (type != DebtType.CREDIT_CARD && creditLimitCents != null) {
            throw new IllegalArgumentException("creditLimitCents must be null for non-CREDIT_CARD debts");
        }
    }

}