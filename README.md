# GateReady

Interview-prep platform for college students: learn a topic, clear a 20-question MCQ gate (14 of 20), clear a coding round, then the next topic unlocks.

- `frontend/` React + TypeScript + Vite. Runs against an in-browser mock API by default (`VITE_USE_MOCK=false` switches to the real API at `/api`).
- `backend/` Spring Boot 3 + PostgreSQL (phase 1, in progress).
- Design document and cost options: see the shared design document, section 12.

```bash
cd frontend && npm install && npm run dev      # app at http://localhost:5173
npm test                                       # unit and component tests
```
