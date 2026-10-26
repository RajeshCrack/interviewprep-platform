# Deploying GateReady

Target: the "Option J" layout from the design document. Three pieces, about $38 a month at pilot size.

```
Students ── Netlify (React site)
               │  HTTPS
               ▼
        API server (Hetzner)                    Judge0 server (Hetzner)
        Caddy ─► Spring Boot API ─► PostgreSQL     Judge0 + workers + Redis + PostgreSQL
                       │  private network, token  ▲
                       └──────────────────────────┘
```

Student code runs only on the Judge0 server, which has nothing else on it. Never run `JUDGE_MODE=local` in production: it runs code on the API machine with no sandbox.

You need: a domain (or a free Netlify address for the site plus a subdomain for the API), a Netlify account, a Hetzner account, and an SMTP account (for verification, password-reset and reminder emails).

## 1. Servers and network

1. In Hetzner Cloud create a private network, for example `10.0.0.0/24`.
2. Create two Ubuntu 22.04 servers in it: `api` (for example `10.0.0.2`) and `judge0` (`10.0.0.3`). Ubuntu 22.04 matters for Judge0.
3. Firewalls. `api`: allow 22 from your own IP, 80 and 443 from anywhere. `judge0`: allow 22 from your own IP and 2358 only from `10.0.0.2`.
4. Install Docker on both: `curl -fsSL https://get.docker.com | sh`.

## 2. Judge0 server

Judge0 CE 1.13.1 (the first release with the security fixes for CVE-2024-28185, CVE-2024-28189 and CVE-2024-29021) needs the older cgroup v1 on the host. Its release notes describe this ([github.com/judge0/judge0/releases](https://github.com/judge0/judge0/releases)), and a still-open issue tracks cgroup v2 ([issue 543](https://github.com/judge0/judge0/issues/543)).

1. `sudo nano /etc/default/grub`, add `systemd.unified_cgroup_hierarchy=0` inside `GRUB_CMDLINE_LINUX="..."`, then `sudo update-grub && sudo reboot`.
2. After the reboot, `mount | grep cgroup` must show `cgroup` mounts of type v1 (not a single `cgroup2`).
3. `git clone https://github.com/RajeshCrack/interviewprep-platform && cd interviewprep-platform/deploy/judge0`
4. `cp .env.example .env`, fill it in (`openssl rand -base64 36` for each secret, `JUDGE0_BIND_IP=10.0.0.3`), then `docker compose up -d`.
5. Check from the **API** server: `curl -H "X-Auth-Token: YOUR_TOKEN" http://10.0.0.3:2358/languages | head`. Then run a real program:
   ```bash
   curl -s -X POST "http://10.0.0.3:2358/submissions?wait=true" -H "X-Auth-Token: YOUR_TOKEN" -H "Content-Type: application/json" \
     -d '{"language_id":62,"source_code":"public class Main { public static void main(String[] a){ System.out.println(42); } }"}'
   ```
   The answer must contain `"stdout":"42\n"` and status id 3. If you see `/box/Main.java` errors, the host is still on cgroup v2.

Judge0 runs **Java 13**. Lessons, problems and reference solutions must not use records, text blocks, `instanceof` patterns or anything newer. The local test runner compiles with `--release 13` so tests catch this early.

## 3. API server

1. DNS: an A record for `api.yourdomain` pointing at the API server's public address.
2. `git clone https://github.com/RajeshCrack/interviewprep-platform && cd interviewprep-platform/deploy`
3. `cp .env.example .env` and fill it in: `API_DOMAIN`, `DB_PASSWORD`, `JWT_SECRET` (32+ characters), `APP_BASE_URL` (the Netlify address, used in the links inside emails), `JUDGE_URL=http://10.0.0.3:2358`, `JUDGE_TOKEN` (same as on the Judge0 server). Set `CORS_ORIGINS` after step 4.
4. `docker compose up -d --build`. The first start runs every database migration and seeds the Java course.
5. `curl https://api.yourdomain/actuator/health` must answer `{"status":"UP"}`. Caddy fetches the HTTPS certificate on its own once DNS is right.

## 4. Website on Netlify

1. Netlify, Add new site, Import from GitHub, pick the repository. `netlify.toml` at the repo root already sets the build folder, command and single-page routing.
2. Site settings, Environment variables: `VITE_API_URL` = `https://api.yourdomain` (no trailing slash and no `/api`). Deploy.
3. Put the site's address into `CORS_ORIGINS` in `deploy/.env` on the API server and run `docker compose up -d` to apply it. Without this the browser blocks every request.

## 5. Backups

1. `sudo mkdir -p /var/backups/gateready` and run `./backup.sh` once by hand from `deploy/`.
2. Add the cron line shown at the top of `backup.sh`. Also copy the backup folder off the server (Hetzner Storage Box, S3, anything).
3. Practise a restore once on a spare machine before you need it: `gunzip -c backup.sql.gz | docker compose exec -T db psql -U gateready gateready`.

## 6. Watching it

- Free uptime monitor (UptimeRobot or similar) on `https://api.yourdomain/actuator/health`, alerting to your phone.
- Logs: `docker compose logs -f api`.
- Update: `git pull && docker compose up -d --build`. Database migrations run on start. Back up first; a migration is not reversible by redeploying the old version.

## 7. Smoke test before inviting anyone

Register, set a goal, open Java topic 1, pass the MCQ test, open the coding round, solve an easy problem (this proves Judge0 end to end), and confirm the dashboard shows XP and a badge. Then ask a friend to do it from a phone on mobile data.

## Not ready yet: decide before real students arrive

- **Password reset and email verification need real SMTP.** Both work end to end, but the links are sent by email. Without `SPRING_MAIL_HOST` they appear only in the API log, so set SMTP (and test with your own address) before inviting students. Accounts created before this feature count as verified. Reminder emails go only to verified addresses.
- **Only topic 1 has content.** Topics 2 to 10 show as locked and have no lesson, no MCQ bank and no coding problems, so no student can get past topic 1 today. Each topic needs a lesson, 40 MCQs (10 in each theory/code and easy/hard group) and 5 coding problems.
- **Browser push notifications** are not built. Reminders are in the app, plus email when SMTP is set.
- **Sessions live in the browser's local storage**, which a script injected into the page could read. Fine for a pilot; add a Content-Security-Policy header before a larger launch.
- **One API instance.** The login rate limiter is in memory. Reminders are safe with several instances (each reminder is claimed in the database), the limiter is not.
- **Privacy.** You will hold students' names and emails. Add a privacy notice and terms page, and check what India's data protection law (DPDP Act 2023) asks of you.
- The compose files and the Judge0 settings were checked for syntax in CI but **have not been run against real servers**, because this workspace has no Docker and no servers. Expect to adjust them on the first real deploy, and compare `deploy/judge0/docker-compose.yml` with the files of the Judge0 release you install.
