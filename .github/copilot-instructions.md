# BalanceBeam — Copilot Instructions

## Project Overview

BalanceBeam is a personal debt payoff planner. It generates a biweekly payment action plan based on debt terms, interest rates, and available income. It is a solo project built for personal use.

**Stack:** Quarkus 3 (Java 21) · Firestore · Vite + React · Deployed to Google Cloud Run

---

## Architecture

```
dev.balancebeam.core.model   — immutable domain input types (framework-agnostic)
dev.balancebeam.core.plan    — immutable output/result types
dev.balancebeam.core.engine  — planning logic, simulation, strategies (pure Java)
```

**Hard rule:** Nothing under `dev.balancebeam.core` may import Quarkus, JAX-RS, or Firestore classes. The core engine must remain framework-agnostic.

---

## Domain Model Conventions

- **Money** is always stored as `long` cents. Never use `double` or `BigDecimal` for financial values.
- **APR** is stored as `int` basis points (bps). 1% = 100 bps. 20% APR = 2000 bps.
- **`dueDayOfMonth`** is capped at **28**. This ensures the day is valid across all calendar months including February. Document this rule wherever it appears.
- Use **Java records** for all immutable data types (domain models, result types).
- Use **primitive types** (`long`, `int`, `boolean`) by default. Use boxed types (`Long`, `Integer`) **only when `null` is a meaningful, intentional value** — not as a default.
- `creditLimitCents` is `Long` (nullable) because it only applies to `CREDIT_CARD` debts. A `STUDENT_LOAN` must have `null` here; a `CREDIT_CARD` must have a value. This invariant is enforced in the `Debt` compact constructor.

---

## Validation Rules (enforced in compact constructors)

- All cent values (`balanceCents`, `minimumPaymentCents`, `paycheckNetCents`, etc.) must be `>= 0`
- `aprBasisPoints >= 0`
- `dueDayOfMonth` in `[1, 28]` inclusive
- `id`, `name`, `type` on `Debt` are non-null (`Objects.requireNonNull`)
- `CREDIT_CARD` debt requires non-null `creditLimitCents`; all other types require `null`

---

## Testing Conventions

- Domain and engine tests use **plain JUnit 5**. Do not use `@QuarkusTest` for anything under `dev.balancebeam.core`.
- Use `@DisplayName` on classes and methods for readable test output. Class-level `@DisplayName` should be the short class name only (e.g., `"Debt"`, `"Budget"`).
- Test method naming pattern: `subjectAndCondition_expectedOutcome()`
- Test classes are **package-private** (`class`, not `public class`).
- **No wildcard imports** (`import static org.junit.jupiter.api.Assertions.*` or `import java.util.*`). Import each type or static member explicitly so it's clear where everything comes from.
- **Do not test compiler-generated behavior.** `toString()` on records is auto-generated and not worth testing.
- Keep tests simple and focused — one behavior per test, no unrelated assertions grouped together.
- Minimize inline comments. Let `@DisplayName` or and or javadoc carry the scenario description. Only add a comment when the setup isn't self-evident.
- Always test:
  - Happy path construction + accessor correctness
  - All constraint violations (one test per constraint)
  - Boundary values explicitly (e.g., `dueDayOfMonth = 1` and `= 28`, not just `0` and `29`)
  - `equals()` and `hashCode()` for records
- Use realistic domain values in tests (e.g., real-world APR bps, plausible balance amounts)

---

## Current State (as of Epic 1 — Core Debt Planning Engine)

| Issue | Title | Status |
|---|---|---|
| #5 | feat(core): add debt + cashflow domain models | ✅ Done |
| #6 | feat(core): implement interest accrual + payoff simulation | ✅ |
| #7 | feat(core): implement payoff strategies (avalanche + min reduction) | Open |
| #8 | feat(core): implement biweekly cashflow allocator | Open |
| #9 | feat(core): generate plan summary + next actions | Open |
| #10 | test(core): realistic scenario tests for 5–7 debt portfolio | Open |
| #11 | docs(core): document engine rules + assumptions → `docs/engine.md` | Open |

Issues are designed to be worked **in order** — each builds on the types from the previous.
