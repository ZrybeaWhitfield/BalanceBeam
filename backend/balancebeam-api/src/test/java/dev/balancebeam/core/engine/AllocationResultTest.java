package dev.balancebeam.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.plan.PaymentAction;

@DisplayName("AllocationResult")
class AllocationResultTest {

    @Test
    @DisplayName("constructs with all fields accessible")
    void allocationResult_constructsAndAccessorsReturnExpectedValues() {
        PaymentAction minimumAction = new PaymentAction("debt1", 15000L, LocalDate.of(2026, 4, 10));
        PaymentAction extraAction = new PaymentAction("debt2", 5000L, LocalDate.of(2026, 4, 5));

        AllocationResult result = new AllocationResult(
            List.of(minimumAction),
            List.of(extraAction),
            75000L,
            10000L,
            15000L,
            0L
        );

        Assertions.assertEquals(List.of(minimumAction), result.minimumPaymentActions());
        Assertions.assertEquals(List.of(extraAction), result.extraPaymentActions());
        Assertions.assertEquals(75000L, result.availableCents());
        Assertions.assertEquals(10000L, result.reservedBufferCents());
        Assertions.assertEquals(15000L, result.totalMinimumsDueCents());
        Assertions.assertEquals(0L, result.shortfallCents());
    }

    @Test
    @DisplayName("defensively copies the minimum payment actions list")
    void allocationResult_defensivelyCopiesMinimumPaymentActions() {
        List<PaymentAction> minimumActions = new ArrayList<>();
        PaymentAction original = new PaymentAction("debt1", 15000L, LocalDate.of(2026, 4, 10));
        minimumActions.add(original);

        AllocationResult result = new AllocationResult(
            minimumActions,
            List.of(),
            75000L,
            10000L,
            15000L,
            0L
        );

        minimumActions.add(new PaymentAction("debt2", 5000L, LocalDate.of(2026, 4, 11)));

        Assertions.assertEquals(List.of(original), result.minimumPaymentActions());
    }

    @Test
    @DisplayName("defensively copies the extra payment actions list")
    void allocationResult_defensivelyCopiesExtraPaymentActions() {
        List<PaymentAction> extraActions = new ArrayList<>();
        PaymentAction original = new PaymentAction("debt2", 5000L, LocalDate.of(2026, 4, 5));
        extraActions.add(original);

        AllocationResult result = new AllocationResult(
            List.of(),
            extraActions,
            75000L,
            10000L,
            15000L,
            0L
        );

        extraActions.add(new PaymentAction("debt3", 2500L, LocalDate.of(2026, 4, 12)));

        Assertions.assertEquals(List.of(original), result.extraPaymentActions());
    }

    @Test
    @DisplayName("rejects null action lists")
    void allocationResult_rejectsNullActionLists() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new AllocationResult(null, List.of(), 75000L, 10000L, 15000L, 0L)
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new AllocationResult(List.of(), null, 75000L, 10000L, 15000L, 0L)
        );
    }

    @Test
    @DisplayName("equal values produce equal records and different values do not")
    void allocationResult_equalsAndHashCodeBehaveAsExpected() {
        List<PaymentAction> minimumActions = List.of(
            new PaymentAction("debt1", 15000L, LocalDate.of(2026, 4, 10))
        );
        List<PaymentAction> extraActions = List.of(
            new PaymentAction("debt2", 5000L, LocalDate.of(2026, 4, 5))
        );

        AllocationResult first = new AllocationResult(minimumActions, extraActions, 75000L, 10000L, 15000L, 0L);
        AllocationResult second = new AllocationResult(minimumActions, extraActions, 75000L, 10000L, 15000L, 0L);
        AllocationResult different = new AllocationResult(minimumActions, extraActions, 70000L, 10000L, 15000L, 0L);

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(first.hashCode(), second.hashCode());
        Assertions.assertNotEquals(first, different);
    }
}
