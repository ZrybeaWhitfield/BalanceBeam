package dev.balancebeam.core.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@DisplayName("PaySchedule")
class PayScheduleTest {

    @Test
    @DisplayName("WEEKLY pay schedule constructs with all fields accessible")
    void weeklyPaySchedule_constructsAndAccessorsReturnExpectedValues() {
        LocalDate date = LocalDate.of(2026, 3, 6);
        PaySchedule schedule = new PaySchedule(PayFrequency.WEEKLY, date);

        Assertions.assertEquals(PayFrequency.WEEKLY, schedule.frequency());
        Assertions.assertEquals(date, schedule.nextPayDate());
    }

    @Test
    @DisplayName("BIWEEKLY pay schedule constructs successfully")
    void biweeklyPaySchedule_constructsSuccessfully() {
        PaySchedule schedule = new PaySchedule(PayFrequency.BIWEEKLY, LocalDate.of(2026, 3, 13));

        Assertions.assertEquals(PayFrequency.BIWEEKLY, schedule.frequency());
    }

    @Test
    @DisplayName("MONTHLY pay schedule constructs successfully")
    void monthlyPaySchedule_constructsSuccessfully() {
        PaySchedule schedule = new PaySchedule(PayFrequency.MONTHLY, LocalDate.of(2026, 3, 31));

        Assertions.assertEquals(PayFrequency.MONTHLY, schedule.frequency());
    }

    @Test
    @DisplayName("null frequency throws NullPointerException")
    void nullFrequency_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new PaySchedule(null, LocalDate.of(2026, 3, 6)));
    }

    @Test
    @DisplayName("null nextPayDate throws NullPointerException")
    void nullNextPayDate_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new PaySchedule(PayFrequency.WEEKLY, null));
    }

    @Test
    @DisplayName("equal PaySchedule records with the same values are equal")
    void equalPaySchedules_areEqual() {
        LocalDate date = LocalDate.of(2026, 3, 6);
        PaySchedule a = new PaySchedule(PayFrequency.WEEKLY, date);
        PaySchedule b = new PaySchedule(PayFrequency.WEEKLY, date);

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("PaySchedule records with different frequencies are not equal")
    void differentFrequencies_areNotEqual() {
        LocalDate date = LocalDate.of(2026, 3, 6);
        PaySchedule a = new PaySchedule(PayFrequency.WEEKLY, date);
        PaySchedule b = new PaySchedule(PayFrequency.BIWEEKLY, date);

        Assertions.assertNotEquals(a, b);
    }
}
