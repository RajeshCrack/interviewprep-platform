# GateReady

Interview-prep platform for college students: learn a topic, clear a 20-question MCQ gate (14 of 20), clear a coding round, then the next topic unlocks.

- `frontend/` React + TypeScript + Vite. Talks to the real API by default (`/api`, proxied to `localhost:8080` in dev). `npm run dev:mock` runs against an in-browser stand-in for the screens that do not need a test (the MCQ test needs the real server).
- `backend/` Spring Boot 3 (Java 21) + PostgreSQL + Flyway. Phase 1: register, login, refresh and logout, goals, dashboard, streak and XP, and the pure gate rules. Phase 2: course catalog, Java roadmap with locked topics, topic lessons (topic 1 has full content so far). Phase 3: the MCQ gate. A bank of 40 questions for topic 1; each test draws 20 (5 from each of theory/code x easy/hard, options shuffled), is graded on the server so the answer key never reaches the browser, resumes after a refresh, expires after 30 minutes, draws new questions on a retry, and awards 100 XP once on the first pass. 

Phase 4: the coding round. Opens after the MCQ pass. Five original problems for topic 1 (2 easy, 1 medium, 2 hard), each judged against sample and hidden tests (programs read stdin, write stdout). Run checks the samples, Submit checks every test; hidden test data never reaches the browser. The round is passed with at least 3 problems solved including both easy ones (this rule is an assumption, see `GateRules.CODING_MIN_SOLVED`), which unlocks the next topic and gives 200 XP once.

## Run it

```bash
# database (once)
createuser -P gateready      # password: gateready
createdb -O gateready gateready
createdb -O gateready gateready_test

# backend  (JWT_SECRET must be 32+ characters)
cd backend
JWT_SECRET=change-me-change-me-change-me-1234 mvn spring-boot:run
# Without SMTP, verification and reset links are printed in this terminal's log.

# frontend
cd frontend && npm install
npm run dev                         # http://localhost:5173
```

## Test

```bash
cd backend && mvn verify      # needs PostgreSQL with the gateready_test database
cd frontend && npm test
```

Phase 5: reminders and badges. Every minute the server checks who is due: a student who has not met their daily goal gets one reminder a day at the time they chose (up to 3 hours late if the server was down), as an in-app notification and, if they chose email, an email. Nine badges (streaks, first MCQ pass, first solve, topics cleared) are awarded automatically with a notification. Not built yet: browser push notifications, which need a service worker and VAPID keys; the "push" setting is shown as "In the app" until then.

Email: set `SPRING_MAIL_HOST` (plus `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM`) to send real email. Without it, emails are written to the log. Set `app.reminders.enabled=false` to switch the scheduler off.

Code judging is chosen with `JUDGE_MODE`: `disabled` (default, submissions answer 503), `local` (development and tests; runs code with the JDK on the same machine, **not sandboxed**, never use in production) or `judge0` (production; set `JUDGE_URL` and optionally `JUDGE_TOKEN`). For local development: `JUDGE_MODE=local mvn spring-boot:run`.

Backend settings come from environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `APP_ZONE` (default `Asia/Kolkata`, decides what "today" means for goals and streaks).

## Deploy

See [DEPLOY.md](DEPLOY.md): Netlify for the site, a Hetzner server for the API and PostgreSQL, a second Hetzner server for Judge0, with backups and a launch checklist.
