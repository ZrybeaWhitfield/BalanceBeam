# BalanceBeam

## Overview

BalanceBeam is a personal debt payoff planner designed to turn real-life cashflow into a practical payment strategy.

Instead of just tracking balances, BalanceBeam generates a biweekly action plan based on debt terms, interest rates, and available income. The goal is to reduce monthly obligations while minimizing long-term interest.

---

## Tech Stack

- **Quarkus (Java)** backend deployed to Google Cloud Run
- **Firestore** for persistence
- **Vite + React** for a lightweight UI
- **GitHub Projects** for planning and issue tracking

---

## Status

The MVP is designed for personal use, with the possibility of expanding to multi-user support later.

---

## Getting Started

### Backend (Quarkus)

To run the backend API locally:

1. Navigate to the backend directory:
   ```sh
   cd backend/balancebeam-api
   ```
2. Start in development mode (live reload):
   ```sh
   ./mvnw quarkus:dev
   ```
   The Dev UI is available at [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/).

3. Check the health endpoint to verify the API is running:
   - Open [http://localhost:8080/q/health](http://localhost:8080/q/health) in your browser or use:
     ```sh
     curl http://localhost:8080/q/health
     ```
   - A healthy response will confirm the backend is up.

For more backend options (packaging, native builds, etc.), see `backend/balancebeam-api/README.md`.

### Frontend

The frontend folder is currently empty. Future instructions will be added as development progresses.

---