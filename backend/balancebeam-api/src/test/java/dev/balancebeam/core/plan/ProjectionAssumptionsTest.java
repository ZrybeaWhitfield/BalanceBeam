package dev.balancebeam.core.plan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectionAssumptions")
class ProjectionAssumptionsTest {

    @Test
    @DisplayName("Accepts a projection with no assumed opening-interest amounts")
    void emptyAssumedDebtIds_constructsSuccessfully() {
        assertDoesNotThrow(() -> new ProjectionAssumptions("biweekly-v1", Set.of()));
    }

    @Test
    @DisplayName("Rejects a missing model version")
    void nullModelVersion_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ProjectionAssumptions(null, Set.of()));
    }

    @Test
    @DisplayName("Rejects an empty model version")
    void emptyModelVersion_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionAssumptions("", Set.of()));
    }

    @Test
    @DisplayName("Rejects a whitespace-only model version")
    void whitespaceModelVersion_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionAssumptions("   ", Set.of()));
    }

    @Test
    @DisplayName("Rejects a missing set of assumed debt IDs")
    void nullAssumedDebtIds_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ProjectionAssumptions("biweekly-v1", null));
    }

    @Test
    @DisplayName("Rejects a null debt ID in the assumption set")
    void nullDebtId_throwsNullPointerException() {
        Set<String> debtIds = new HashSet<>();
        debtIds.add(null);

        assertThrows(NullPointerException.class, () -> new ProjectionAssumptions("biweekly-v1", debtIds));
    }

    @Test
    @DisplayName("Rejects an empty debt ID in the assumption set")
    void emptyDebtId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectionAssumptions("biweekly-v1", Set.of("")));
    }

    @Test
    @DisplayName("Rejects a whitespace-only debt ID in the assumption set")
    void whitespaceDebtId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectionAssumptions("biweekly-v1", Set.of("   ")));
    }

    @Test
    @DisplayName("Iterates assumed debt IDs in lexicographic order regardless of insertion order")
    void reverseInsertedDebtIds_iterateInSortedOrder() {
        Set<String> debtIds = new LinkedHashSet<>(List.of("loan-2", "loan-10", "loan-1"));
        ProjectionAssumptions assumptions = new ProjectionAssumptions("biweekly-v1", debtIds);

        assertEquals(List.of("loan-1", "loan-10", "loan-2"),
                new ArrayList<>(assumptions.assumedZeroInterestDebtIds()));
    }

    @Test
    @DisplayName("Retains the assumption snapshot after the caller changes the original set")
    void sourceDebtIdsChanged_assumptionsRetainSnapshot() {
        Set<String> debtIds = new HashSet<>(Set.of("loan-1"));
        ProjectionAssumptions assumptions = new ProjectionAssumptions("biweekly-v1", debtIds);

        debtIds.clear();
        debtIds.add("loan-2");

        assertEquals(Set.of("loan-1"), assumptions.assumedZeroInterestDebtIds());
    }

    @Test
    @DisplayName("Prevents changes through the returned assumption set")
    void returnedDebtIdsModified_throwsUnsupportedOperationException() {
        ProjectionAssumptions assumptions = new ProjectionAssumptions("biweekly-v1", Set.of("loan-1"));

        assertThrows(UnsupportedOperationException.class,
                () -> assumptions.assumedZeroInterestDebtIds().add("loan-2"));
    }
}
