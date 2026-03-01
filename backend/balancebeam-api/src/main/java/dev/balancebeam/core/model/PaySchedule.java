package dev.balancebeam.core.model;

import java.time.LocalDate;
import java.util.Objects;

public record PaySchedule(
        PayFrequency frequency,
        LocalDate nextPayDate) {
    public PaySchedule {
        Objects.requireNonNull(nextPayDate, "nextPayDate cannot be null");
        Objects.requireNonNull(frequency, "frequency cannot be null");
    }
}