# BalanceBeam project conventions

BalanceBeam is a debt payoff planner. The backend uses Java 21 and Quarkus; the planned UI uses Vite and React. See `README.md` for setup and project context.

## Architecture

- `dev.balancebeam.core.model` contains domain inputs, `dev.balancebeam.core.plan` contains plan outputs, and `dev.balancebeam.core.engine` contains planning logic.
- Keep `dev.balancebeam.core` framework-agnostic. Nothing in it should import Quarkus, JAX-RS, or Firestore classes.

## Java style

- Use explicit Java imports; do not use wildcard imports, including static imports.

## Domain rules

- Represent money as `long` cents and APR as `int` basis points (100 bps = 1%). Do not use `double` or `BigDecimal` for monetary values.
- Keep `dueDayOfMonth` in the inclusive range 1–28 so it is valid in every month. Explain the limit where the field is exposed to users.
- Use records for immutable domain inputs and plan results. Prefer primitives unless `null` has a defined meaning.
- Validate domain invariants in record compact constructors: cent amounts and APR cannot be negative; required fields cannot be null; debt IDs and names cannot be blank.
- `creditLimitCents` is a nullable `Long`: a `CREDIT_CARD` requires a positive limit, and other debt types require `null`.

## Tests

- Test core models and engine behavior with plain JUnit 5; do not use `@QuarkusTest` for `dev.balancebeam.core`.
- Use package-private test classes and `@DisplayName` for readable scenarios. Class-level display names should be the short class name. Name test methods `subjectAndCondition_expectedOutcome()`.
- Cover relevant valid cases, constraint violations, and boundary values. Keep each test focused on one behavior and use realistic debt and cashflow values.
- Avoid tests of unchanged compiler-generated record behavior and comments that repeat the test name.

## Agent coordination

- Give parallel contributors distinct, bounded tasks with an expected result. Coordinate before editing the same files, and review combined changes against these project rules.
