package dev.balancebeam.core.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Budget")
class BudgetTest {

    @Test
    @DisplayName("valid budget constructs with all fields accessible")
    void validBudget_constructsAndAccessorsReturnExpectedValues() {
        Budget budget = new Budget(500000L, 200000L, 50000L);

        Assertions.assertEquals(500000L, budget.paycheckNetCents());
        Assertions.assertEquals(200000L, budget.essentialSpendPerPaycheckCents());
        Assertions.assertEquals(50000L, budget.bufferCents());
    }

    @Test
    @DisplayName("all-zero values are valid")
    void zeroBudget_constructsSuccessfully() {
        Assertions.assertDoesNotThrow(() -> new Budget(0L, 0L, 0L));
    }

    @Test
    @DisplayName("negative paycheckNetCents throws IllegalArgumentException")
    void negativePaycheckNetCents_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Budget(-1L, 200000L, 50000L));
    }

    @Test
    @DisplayName("negative essentialSpendPerPaycheckCents throws IllegalArgumentException")
    void negativeEssentialSpend_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Budget(500000L, -1L, 50000L));
    }

    @Test
    @DisplayName("negative bufferCents throws IllegalArgumentException")
    void negativeBufferCents_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Budget(500000L, 200000L, -1L));
    }

    @Test
    @DisplayName("equal Budget records with the same values are equal")
    void equalBudgets_areEqual() {
        Budget a = new Budget(500000L, 200000L, 50000L);
        Budget b = new Budget(500000L, 200000L, 50000L);

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Budget records with different paycheckNetCents are not equal")
    void differentPaycheckNetCents_areNotEqual() {
        Budget a = new Budget(500000L, 200000L, 50000L);
        Budget b = new Budget(400000L, 200000L, 50000L);

        Assertions.assertNotEquals(a, b);
    }
}
