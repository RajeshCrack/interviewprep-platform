# GateReady

Interview-prep platform for college students: learn a topic, clear a 20-question MCQ gate (14 of 20), clear a coding round, then the next topic unlocks.

- `frontend/` React + TypeScript + Vite. Runs against an in-browser mock API by default. `VITE_USE_MOCK=false` switches to the real API (`/api`, proxied to `localhost:8080` in dev).
- `backend/` Spring Boot 3 (Java 21) + PostgreSQL + Flyway. Phase 1: register, login, refresh and logout, goals, dashboard, streak and XP, and the pure gate rules.

## Run it

```bash
# database (once)
createuser -P gateready      # password: gateready
createdb -O gateready gateready
createdb -O gateready gateready_test

# backend  (JWT_SECRET must be 32+ characters)
cd backend
JWT_SECRET=change-me-change-me-change-me-1234 mvn spring-boot:run

# frontend
cd frontend && npm install
VITE_USE_MOCK=false npm run dev     # http://localhost:5173
```

## Test

```bash
cd backend && mvn verify      # needs PostgreSQL with the gateready_test database
cd frontend && npm test
```

Backend settings come from environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `APP_ZONE` (default `Asia/Kolkata`, decides what "today" means for goals and streaks).
