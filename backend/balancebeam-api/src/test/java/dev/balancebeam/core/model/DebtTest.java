package dev.balancebeam.core.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Debt")
class DebtTest {

    @Test
    @DisplayName("valid CREDIT_CARD constructs with all fields accessible")
    void validCreditCard_constructsAndAccessorsReturnExpectedValues() {
        Debt debt = new Debt("id-1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L);

        Assertions.assertEquals("id-1", debt.id());
        Assertions.assertEquals("Visa", debt.name());
        Assertions.assertEquals(DebtType.CREDIT_CARD, debt.type());
        Assertions.assertEquals(100000L, debt.balanceCents());
        Assertions.assertEquals(1999, debt.aprBasisPoints());
        Assertions.assertEquals(2500L, debt.minimumPaymentCents());
        Assertions.assertEquals(15, debt.dueDayOfMonth());
        Assertions.assertEquals(500000L, debt.creditLimitCents());
    }

    @Test
    @DisplayName("valid STUDENT_LOAN constructs with null creditLimitCents")
    void validStudentLoan_constructsSuccessfully() {
        Debt debt = new Debt("id-2", "Navient", DebtType.STUDENT_LOAN, 1500000L, 600, 15000L, 10, null);

        Assertions.assertNull(debt.creditLimitCents());
        Assertions.assertEquals(DebtType.STUDENT_LOAN, debt.type());
    }

    @Test
    @DisplayName("dueDayOfMonth boundary - day 1 is valid")
    void dueDayOfMonth_dayOne_isValid() {
        Assertions.assertDoesNotThrow(
                () -> new Debt("1", "Loan", DebtType.STUDENT_LOAN, 0L, 0, 0L, 1, null));
    }

    @Test
    @DisplayName("dueDayOfMonth boundary - day 28 is valid")
    void dueDayOfMonth_dayTwentyEight_isValid() {
        Assertions.assertDoesNotThrow(
                () -> new Debt("1", "Loan", DebtType.STUDENT_LOAN, 0L, 0, 0L, 28, null));
    }

    @Test
    @DisplayName("blank id throws IllegalArgumentException")
    void blankId_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("  ", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("blank name throws IllegalArgumentException")
    void blankName_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "  ", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("null id throws NullPointerException")
    void nullId_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new Debt(null, "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("null name throws NullPointerException")
    void nullName_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new Debt("1", null, DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("null type throws NullPointerException")
    void nullType_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new Debt("1", "Visa", null, 100000L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("negative balanceCents throws IllegalArgumentException")
    void negativeBalanceCents_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, -1L, 1999, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("negative aprBasisPoints throws IllegalArgumentException")
    void negativeAprBasisPoints_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, -1, 2500L, 15, 500000L));
    }

    @Test
    @DisplayName("negative minimumPaymentCents throws IllegalArgumentException")
    void negativeMinimumPaymentCents_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, -1L, 15, 500000L));
    }

    @Test
    @DisplayName("dueDayOfMonth = 0 throws IllegalArgumentException")
    void dueDayZero_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 0, 500000L));
    }

    @Test
    @DisplayName("dueDayOfMonth = 29 throws IllegalArgumentException")
    void dueDayTwentyNine_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 29, 500000L));
    }

    @Test
    @DisplayName("CREDIT_CARD with creditLimitCents = 0 throws IllegalArgumentException")
    void creditCard_withZeroCreditLimit_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 0L));
    }

    @Test
    @DisplayName("CREDIT_CARD with null creditLimitCents throws IllegalArgumentException")
    void creditCard_withNullCreditLimit_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, null));
    }

    @Test
    @DisplayName("STUDENT_LOAN with non-null creditLimitCents throws IllegalArgumentException")
    void studentLoan_withCreditLimit_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Debt("1", "Navient", DebtType.STUDENT_LOAN, 1500000L, 600, 15000L, 10, 500000L));
    }

    @Test
    @DisplayName("equal Debt records with the same values are equal")
    void equalDebts_areEqual() {
        Debt a = new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L);
        Debt b = new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L);

        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Debt records with different ids are not equal")
    void differentIds_areNotEqual() {
        Debt a = new Debt("1", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L);
        Debt b = new Debt("2", "Visa", DebtType.CREDIT_CARD, 100000L, 1999, 2500L, 15, 500000L);

        Assertions.assertNotEquals(a, b);
    }
}
