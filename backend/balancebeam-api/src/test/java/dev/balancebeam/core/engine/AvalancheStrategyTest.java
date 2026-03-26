package dev.balancebeam.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.balancebeam.core.model.Debt;
import dev.balancebeam.core.model.DebtType;

@DisplayName("AvalancheStrategy")
class AvalancheStrategyTest {

    private static Debt studentLoan(String id, long balanceCents, int aprBps, long minPayCents) {
        return new Debt(id, "Loan " + id, DebtType.STUDENT_LOAN, balanceCents, aprBps, minPayCents, 15, null);
    }

    private static Debt creditCard(String id, long balanceCents, int aprBps, long minPayCents) {
        return new Debt(id, "Card " + id, DebtType.CREDIT_CARD, balanceCents, aprBps, minPayCents, 15, 500_000L);
    }

    // TODO:
    // Extra covers the first debt entirely and leaves some → two entries: first
    // gets its full balance, second gets the remainder
    // Extra exceeds the sum of all balances → each debt gets exactly its
    // balanceCents (not more)

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Allocates all extra to single debt when extra is less than balance")
        void singleDebt_extraCentsLessThanBalance_AllocateExtraToThatDebt() {
            Debt debt = creditCard("d1", 20_000L, 0, 0);
            long extraCents = 4_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();
            Map<String, Long> result = strategy.allocateExtra(List.of(debt), extraCents);

            assertEquals(Map.of("d1", 4_000L), result);

        }

        @Test
        @DisplayName("Allocates extra to higher APR debt first,")
        void twoDebtsDifferentApr_extraCentsIsLessThanBalance_AllocateExtraToHigherApr() {
            Debt debt1 = creditCard("d1", 20_000L, 1200, 0);
            Debt debt2 = creditCard("d2", 80_000L, 1800, 0);
            long extraCents = 4_000L;

            AvalancheStrategy strategy = new AvalancheStrategy();

            Map<String, Long> result = strategy.allocateExtra(List.of(debt1,debt2), extraCents);

            assertEquals(Map.of("d2", 4_000L), result);

        }

    }

    @Nested
    @DisplayName("Tie-breaking")
    class TieBreaking {

    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

    }
}
