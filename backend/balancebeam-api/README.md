# BalanceBeam backend

Java 21 and Quarkus host BalanceBeam's debt payoff engine. The core projection is implemented; payoff HTTP endpoints and persistence are upcoming. The application currently exposes Quarkus health checks, while forecasts run directly through Java or the console examples.

## Setup

Install **JDK 21** and run the commands below from `backend/balancebeam-api`. Use the included Maven wrapper; a separate Maven installation is not required. The core tests and examples do not require a database or cloud credentials.

On Windows, use `mvnw.cmd` instead of `./mvnw`, and replace the Java classpath below with `"target/classes;target/test-classes"`.

## Validation

Run the full local build, including tests and SpotBugs:

```sh
./mvnw verify
```

Expected result: `BUILD SUCCESS`. For a focused test run:

```sh
./mvnw -Dtest=BiweeklyPayoffProjectorTest test
```

Core tests use plain JUnit 5. Test reports are written to `target/surefire-reports/`, and the JaCoCo coverage report is at `target/site/jacoco/index.html` after running tests.

SpotBugs remains part of the build. `spotbugs-exclude.xml` documents narrow exclusions for immutable result collections; tests verify their defensive copies and rejection of mutations.

## Run and edit examples

```sh
./mvnw test-compile
java -cp target/classes:target/test-classes dev.balancebeam.examples.BiweeklyProjectionExample
```

The default example projects a student-loan payoff on **October 13, 2026**: **$370.95 paid**, **$0.70 newly charged interest**, and **$29.05 unspent**, with the **$100 buffer protected**.

Select the three-card scenario with an argument:

```sh
java -cp target/classes:target/test-classes dev.balancebeam.examples.BiweeklyProjectionExample credit-cards
```

The `student-loan` argument selects the default scenario explicitly. Edit each scenario's debts, budget, and strategy in `runStudentLoan()` or `runCreditCards()`; the shared `runProjection()` method contains the pay schedule, forecast limit, and output code.

All runners are in `src/test/java/dev/balancebeam/examples/`; recompile after editing their inputs. Run the other classes below with the same classpath and no scenario argument:

| Class | Scenario |
| --- | --- |
| `BiweeklyProjectionExample` | Detailed student-loan forecast by default; pass `credit-cards` for the three-card scenario. |
| `BiweeklyCreditCardBudgetStressExample` | All three strategies under tight and negative available-cash budgets. |
| `BiweeklyProjectionTimingScenariosExample` | Due-date payments, later reserved minimums, and the next-payday boundary. |

The timing examples use custom strategies that intentionally withhold extra payments to isolate those behaviors. Console examples are manual inspection tools; JUnit tests provide automated assertions. Examples live under `src/test/java` and are not part of the packaged application.

## Core structure and entry point

All packages below are under `dev.balancebeam.core` and have no Quarkus or database dependencies.

| Package | Responsibility |
| --- | --- |
| `model` | Validated inputs: debts, budget, pay schedule, opening-interest details, and projection options. |
| `engine` | Strategies, paycheck allocation, interest accounting, and repeated-window projection. |
| `plan` | Immutable results: dated payments, balances, forecast windows, totals, status, and assumptions. |

The entry point for a complete biweekly forecast is `BiweeklyPayoffProjector`, in `src/main/java/dev/balancebeam/core/engine/BiweeklyPayoffProjector.java`:

```java
PayoffProjection projection = new BiweeklyPayoffProjector()
        .project(debts, budget, schedule, strategy, options);
```

The arguments are `List<ProjectionDebt>`, `Budget`, `PaySchedule`, `PayoffStrategy`, and `ProjectionOptions`. The older `DebtPayoffSimulator` models fixed monthly payments; the biweekly projector supplies the complete schedule for future API work.

Available strategies are `AvalancheStrategy` (highest APR), `SnowballStrategy` (smallest balance), and `CashFlowIndexStrategy` (lowest balance divided by stated monthly minimum). Minimums are reserved before extra payments are allocated.

## Input conventions

- **Money:** `long` cents; `40_000L` means $400.00.
- **APR:** integer basis points; `1000` means 10%, and `2499` means 24.99%.
- **Due day:** 1–28 inclusive, so the date exists in every month.
- **Debt identity:** IDs and names must be nonblank; IDs must be unique within a projection.
- **Credit limit:** credit cards require a positive `creditLimitCents`; student loans require `null`.
- **Opening student-loan interest:** the optional amount is already included in `Debt.balanceCents()`. Principal is the total minus that amount. Omission means assumed zero and is recorded in the result; an explicitly supplied zero creates no assumption. Credit cards require this field to be absent.
- **Schedule and limit:** projections require `BIWEEKLY`. `ProjectionOptions` accepts a positive window count and defaults to 780 windows, approximately 30 years.

## Forecast rules and assumptions

The current calculation model is identified as `biweekly-v1` in the result's `ProjectionAssumptions`.

| Topic | Rule |
| --- | --- |
| Student-loan interest | Simple daily interest on remaining principal at APR / 365.25. Payments cover unpaid interest before principal. |
| Credit-card interest | Daily compounding at APR / 365 on the modeled outstanding balance, including carried fractions. The full supplied opening balance is the initial interest-bearing amount. |
| Rounding | Carry fractional cents using integer arithmetic with 10^18 internal units per cent. Round cumulative new interest to cents using half-even rounding at payments and window boundaries. Clear the remaining fraction at final payoff. |
| Timing | Each window includes payday and excludes the next payday, 14 days later. Extras occur on payday; minimums occur on their due dates. Payments precede that day's interest accrual. |
| Cash protection | Available cash is paycheck income minus essentials and buffer. Nonpositive available cash produces no payments. |
| Final payoff | A planned final payment may use unreserved cash to cover newly accrued interest. Same-date adjustments follow the selected strategy using balances after scheduled payments, while protecting other reservations. |
| Shortfalls | Record unfunded minimums and continue projecting. A shortfall can coexist with eventual payoff. |

APRs, stated monthly minimums, paycheck income, essentials, and buffer remain fixed throughout a forecast. Minimum payments are capped at the amount owed. There are no new purchases, lender-specific minimum formulas, late fees, penalty APR changes, or student-loan capitalization events. These are estimate policies and may differ from account statements.

### Reading the result

- Each `ProjectionWindow` contains actual dated payments, newly charged interest, ending principal and interest balances, minimum shortfall, and unspent cash.
- A payment's `payoffAdjustmentCents` is already included in its payment amount; do not add it again.
- `totalInterestChargedCents()` includes new interest still unpaid and excludes opening unpaid interest supplied with the input.
- `PAID_OFF` uses the final actual payment date. Empty or already-paid-off portfolios return zero windows and the start date as the payoff date.
- `HORIZON_REACHED` returns remaining balances and a null payoff date. It means the limit was reached, not that payoff is impossible.
- Results preserve the IDs of student loans with assumed zero opening interest. Collections are immutable and ordered for repeatable inspection.

## Run the Quarkus application

```sh
./mvnw quarkus:dev
```

- Health endpoint: [http://localhost:8080/q/health](http://localhost:8080/q/health)
- Dev UI, available in development mode: [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/)

There is no payoff HTTP endpoint yet; use the examples or Java entry point to calculate forecasts.

## Package and run

```sh
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Keep the entire `target/quarkus-app/` directory when distributing this build; the runner depends on its accompanying libraries. Use `./mvnw verify` for the full validation gate, including SpotBugs.

### Optional native build

With a compatible GraalVM installation:

```sh
./mvnw package -Dnative
```

Or with a container runtime:

```sh
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

The JVM build is the normal development path. Native builds are not exercised by the current backend CI workflow.
