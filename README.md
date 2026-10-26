# GateReady

Interview-prep platform for college students: learn a topic, clear a 20-question MCQ gate (14 of 20), clear a coding round, then the next topic unlocks.

- `frontend/` React + TypeScript + Vite. Talks to the real API by default (`/api`, proxied to `localhost:8080` in dev). `npm run dev:mock` runs against an in-browser stand-in for the screens that do not need a test (the MCQ test needs the real server).
- `backend/` Spring Boot 3 (Java 21) + PostgreSQL + Flyway. Phase 1: register, login, refresh and logout, goals, dashboard, streak and XP, and the pure gate rules. Phase 2: course catalog, Java roadmap with locked topics, topic lessons (topic 1 has full content so far). Phase 3: the MCQ gate. A bank of 40 questions for topic 1; each test draws 20 (5 from each of theory/code x easy/hard, options shuffled), is graded on the server so the answer key never reaches the browser, resumes after a refresh, expires after 30 minutes, draws new questions on a retry, and awards 100 XP once on the first pass. The coding round is phase 4, so no topic can be fully passed yet.

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
npm run dev                         # http://localhost:5173
```

## Test

```bash
cd backend && mvn verify      # needs PostgreSQL with the gateready_test database
cd frontend && npm test
```

Backend settings come from environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `APP_ZONE` (default `Asia/Kolkata`, decides what "today" means for goals and streaks).
