# Mini Expense Manager

A small full-stack app to track daily expenses (INR).

- **Add** an expense by hand, or **upload** many at once from a CSV.
- **Auto-categorize** every expense by vendor name using editable keyword rules (e.g. `swiggy` → Food).
- **Flag unusually high expenses** (anomalies) in red across the app.
- **Dashboard**: monthly totals per category (chart + table), top 5 vendors, high-expense list.
- **One click "Load sample data"** on an empty dashboard to try it out.
- **Uncategorized vendors panel** (Rules page): vendors stuck in Other, each with **Create rule**; picking Other lets you add a **nickname** (shown as "Other · Gym"). Saving a rule moves matching expenses that are still in Other.
- **Month-over-month card**: this month vs last month, per category (Other split by nickname).
- **Compare page** (`/compare`): any 2 months, any 2 weeks (Mon–Sun) or any 2 custom date ranges, per category, with a side-by-side bar chart and a table. It says what kind of comparison you picked and warns when it is not like-for-like (different lengths, a period still running, overlap), with a per-day average view.
- **Search + server-side pagination** on the expense list (50 per page).
- **Export to CSV** of exactly what the filters and search show; the file can be uploaded again.
- **Muneem, your accountant** (`/muneem`): chat or talk (English / Hindi) with an AI assistant that answers from your own data, finds saving ideas, asks follow-up questions and remembers your answers. Free: Gemini / Groq free tiers + the browser's own voice. See [Muneem](#muneem-ai-assistant).

The full design is in [`docs/SPEC.md`](docs/SPEC.md); Muneem's in [`docs/ASSISTANT-SPEC.md`](docs/ASSISTANT-SPEC.md).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Validation), Flyway, Apache Commons CSV, Maven wrapper |
| Database | PostgreSQL 18 |
| Frontend | React 18 + TypeScript (strict), Vite 7, React Router 6, Recharts 2, Tailwind CSS 4 |
| Tests | JUnit 5 + Mockito (logic), Spring Boot integration tests against a real Postgres test DB |
| Packaging | Docker multi-stage builds, nginx for the frontend, `docker-compose.yml` for the whole stack |

## Run with Docker (easiest)

Needs Docker Desktop (or Docker Engine + Compose v2). From the repo root:

```bash
docker compose up --build
```

Open **http://localhost:5173**. On the empty dashboard click **Load sample data**.

- `db`: `postgres:18`, data in the `db-data` volume, health-checked.
- `backend`: Spring Boot. Starts once the DB is healthy and runs the Flyway migrations + seed. Not published to the host; the browser reaches it through the frontend.
- `frontend`: nginx serving the built app on port 5173, with SPA fallback and `/api` proxied to `backend:8080`.

Stop with `docker compose down`, or `docker compose down -v` to also delete the database.

> If `npm run dev` is also running on your machine, it holds `localhost:5173` on IPv6 and your browser may reach the dev server instead of Docker. Stop it first, or open http://127.0.0.1:5173.

## Run locally without Docker

Needs JDK 21, Node 22 (or 20.19+) and PostgreSQL running on `localhost:5432`.

1. Create the databases (the app creates its tables itself via Flyway):
   ```bash
   psql -U postgres -c "CREATE DATABASE expense_manager"
   psql -U postgres -c "CREATE DATABASE expense_manager_test"   # only for the tests
   ```
2. Backend (http://localhost:8080). Defaults: user `postgres`, password `postgres123`; override with the env vars below.
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
3. Frontend (http://localhost:5173). The Vite dev server proxies `/api` to `localhost:8080`.
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## Run tests

```bash
cd backend
./mvnw test          # 112 tests: unit tests + *IT integration tests (uses the expense_manager_test DB; never calls the real AI)

cd frontend
npm run build        # TypeScript strict type-check + production build
```

## Muneem (AI assistant)

Muneem answers questions like *"Where did I spend most this month?"*, *"Is mahine kharcha kahan bada?"* or *"How can I save?"*.

**Setup (free, ~5 minutes):**
1. Get one or both free API keys:
   - Gemini: https://aistudio.google.com/apikey (Google account)
   - Groq: https://console.groq.com/keys
2. Copy `.env.example` to `.env` in the repo root (skip if `.env` already exists) and paste each key right after its `=` (no quotes, no spaces). `.env` is git-ignored: never commit it or paste keys anywhere else.
3. Restart the backend (`./mvnw spring-boot:run` reads `.env` itself; `docker compose up --build` passes it in). On first start Flyway adds the `assistant_notes` table.
4. Start the frontend (`npm run dev`; add `-- --port 5180` if 5173 is taken) and open **Muneem** in the menu. Voice (mic + reading replies aloud) works in **Chrome or Edge**; allow the microphone when asked.

**Check it works**
- `GET /api/assistant/status` → `{"enabled":true,"providers":["GEMINI","GROQ"]}`; the page shows no amber "Muneem is off" box.
- Type *"Where did I spend most this month?"* → a reply with your real ₹ amounts and a small "via Gemini" / "via Groq" tag.
- Pick **हिन्दी**, tap the mic, ask in Hindi → a Hindi reply, read aloud.
- Answer one of Muneem's follow-up questions → it appears under **What Muneem remembers**.
- "Muneem is busy" = both free limits used up; try later. An error about the model name = set `GEMINI_MODEL` / `GROQ_MODEL` in `.env` to a current free model with tool calling.

**How it works**
- The backend computes every number with SQL (same queries as the dashboard / Compare page) and gives the AI only small, ready-made results ("₹4,200"). The AI picks which data to look at and puts it into words; it never does the maths and can't change data.
- Gemini is tried first; on a rate limit, error or timeout the same question goes to Groq. Both down → "Muneem is busy, try again in a minute". No key at all → the page says Muneem is off; the rest of the app is unaffected.
- Chats live only in the browser tab (gone when it closes). Only **notes** (≤ 30 short facts you told Muneem) are stored on the server; you can see and delete them on the page.
- Light on memory for free-tier hosting: no AI SDK, no server-side chat state, capped requests and results, 30 messages per minute.
- Speech-to-text and text-to-speech are the browser's built-in Web Speech API (free). Chrome sends the microphone audio to Google to transcribe.

**Privacy:** on free tiers, Google and Groq may use prompts to improve their models. Muneem sends only summaries and at most 20 expense rows per answer (vendor, amount, date, description), never the whole table. Don't use it with data you can't share that way.

**If a model is retired:** free models change. Set `GEMINI_MODEL` / `GROQ_MODEL` in `.env` to a current free model that supports tool calling, and restart.

## Environment variables

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DB_URL` | backend | `jdbc:postgresql://localhost:5432/expense_manager` | JDBC URL of the database |
| `DB_USER` | backend | `postgres` | DB user |
| `DB_PASSWORD` | backend | `postgres123` | DB password |
| `CORS_ORIGINS` | backend | `http://localhost:5173,http://localhost:5180` | Comma-separated browser origins allowed to call `/api` (needed when the frontend is on another host) |
| `SERVER_PORT` | backend | `8080` | HTTP port (standard Spring Boot setting) |
| `TEST_DB_URL` | backend tests | `jdbc:postgresql://localhost:5432/expense_manager_test` | DB for the integration tests |
| `VITE_API_BASE_URL` | frontend, **build time** | `/api` | API base URL. Keep `/api` when a proxy forwards it (Vite dev, nginx); set e.g. `https://api.example.com/api` when the backend is on another host. Docker: `--build-arg VITE_API_BASE_URL=...` |
| `BACKEND_URL` | frontend nginx container | `http://backend:8080` | Where nginx proxies `/api` |
| `GEMINI_API_KEY` | backend | _(empty)_ | Gemini free-tier key for Muneem (tried first). Also read from `.env` |
| `GROQ_API_KEY` | backend | _(empty)_ | Groq free-tier key for Muneem (fallback). Also read from `.env` |
| `GEMINI_MODEL` | backend | `gemini-flash-lite-latest` | Gemini model (must support tool calling) |
| `GROQ_MODEL` | backend | `openai/gpt-oss-120b` | Groq model (must support tool calling) |

## CSV format

```csv
date,amount,vendor,description
2026-11-18,450,Swiggy,Dinner
18-11-2026,"₹1,250.50",Amazon,Headphones
```

- Header row required (any column order, case-insensitive); `description` is optional.
- Dates `YYYY-MM-DD` or `DD-MM-YYYY` (also with `/`). `₹` and `,` in amounts are fine.
- Max 2 MB and 5,000 rows. Bad rows and duplicates are skipped and listed; good rows are saved.
- Sample file: [`sample-data/expenses.csv`](sample-data/expenses.csv) (45 rows: 42 good, 3 bad, 3 high expenses). There is only one copy: the backend bundles it into its jar for **Load sample data**, and the frontend build publishes it for the **Download sample CSV** link.

## Assumptions

1. **Single user, no login.**
2. **Currency is INR**, stored as `NUMERIC(12,2)` rupees (never floating point). Amount must be > 0.
3. **Categories** are a fixed seeded list: Food, Groceries, Transport, Shopping, Entertainment, Bills & Utilities, Travel, Health, Other.
4. **Rules** are seeded (~20) and editable on the Rules page (add / edit / delete).
5. **Matching** is case-insensitive: *vendor name contains rule keyword*. If several keywords match, the **longest keyword wins** (`"Amazon Prime"` → Entertainment beats `"Amazon"` → Shopping). No match → **Other**.
6. The category is decided **once, when the expense is saved**. Editing a rule later does not change old expenses.
7. **Anomaly**: amount > 3 × the average of **all** expenses in the same category, **including itself**. Checked live on every read.
8. **CSV**: header row required; bad rows are skipped and reported; good rows are saved.
9. Expenses can be added and deleted; editing an expense is out of scope.
10. **Validation (same for manual add and CSV)**: date required and not in the future; amount > 0 and ≤ ₹1,00,00,000 (1 crore); vendor required, 1–150 characters after trimming; description ≤ 500 characters.
11. **Duplicates**: same date + same amount + same vendor (ignoring case and spaces). In a CSV upload a duplicate is skipped and reported as `duplicate` (whether it matches an existing row or an earlier row of the same file). A manual add is allowed but shows a yellow "looks like a duplicate" warning.

## Design note

**Anomaly rule, including itself.** An expense is "high" when `amount > 3 × average(all expenses in its category)`, and the average includes the expense itself. Because a large expense raises its own average, a category needs **at least 4 expenses** before anything can be flagged; with 3 or fewer it is mathematically impossible. Example (Food): 200, 300, 250, 3000 → average 937.50 → threshold 2812.50 → only **3000** is flagged (3.2× avg). This was a deliberate choice and is covered by a test.

**Anomalies are computed, not stored.** The flag depends on every other row in the category, so storing it would go stale on every insert or delete. It is computed in SQL on read: the category average lives in a CTE over *all* expenses, and filters (month, category, "only high") apply only to the outer query. So filtering a list never changes the average or the flags, and a "high" expense looks the same on every page.

**Categories are decided once, at save time.** Simple and predictable: an expense's category never changes behind the user's back. The trade-off: after editing a rule, old expenses keep their old category (a "re-apply rules" action is a possible next step).

**Duplicates.** A CSV upload is meant to be safe to repeat: uploading the same file twice saves 0 rows the second time. Manual adds are never blocked (two identical coffees on one day are real), only warned about.

**Other trade-offs (out of scope for v1):** login / multi-user, editing expenses, multiple currencies, regex rules, pagination (lists are capped at 500 rows instead).

## API summary

Base path `/api`, JSON.

| Method | Path | Purpose |
|---|---|---|
| GET | `/categories` | List categories |
| GET | `/expenses?month=2026-11&categoryId=&label=&anomaliesOnly=false&q=&page=0&size=50` | One page of expenses, newest first, each with `category`, `label`, `categoryAvg`, `timesAvg`, `anomaly` → `{ items, page, size, totalItems, totalPages, totalAmount }`. `q` searches vendor and description; `size` 1–100 |
| GET | `/expenses/export` (same filters, no paging) | CSV download of exactly what the filters show; the file can be uploaded again |
| POST | `/expenses` | Add one: `{ "date":"2026-11-18", "amount":3000, "vendor":"Zomato", "description":"Party order" }`. Returns the saved row with its anomaly info and `possibleDuplicate` |
| DELETE | `/expenses/{id}` | Delete one |
| POST | `/expenses/upload` (multipart `file`) | CSV import → `{ total, saved, skipped, duplicates, errors:[{row, reason}], anomaliesFound }` |
| GET | `/rules` | List rules |
| POST | `/rules` | Add `{ keyword, categoryId, label? }` (`label` = nickname, only with Other; `409` if the keyword exists). Response has `recategorized`: how many Other expenses moved |
| PUT | `/rules/{id}` | Update a rule (same body and response) |
| DELETE | `/rules/{id}` | Delete a rule |
| GET | `/vendors/uncategorized` | Vendors in Other with no nickname: `[{ vendor, keyword, count, total, lastDate }]` |
| GET | `/labels` | Nicknames in use, for filters |
| GET | `/dashboard?month=2026-11` | `{ months, monthlyTotals, topVendors, anomalies:{count, items}, totals:{spend, count}, monthOverMonth, uncategorizedVendors }` (month optional) |
| GET | `/compare?aFrom=2026-10-01&aTo=2026-10-31&bFrom=2026-11-01&bTo=2026-11-30` | Spend per category in period A vs B (dates inclusive, each ≤ 366 days): `{ a:{from,to,days,count}, b:{…}, total:{a,b,changePct}, rows:[{categoryId, category, label, a, b, changePct}] }` |
| POST | `/sample-data` | Import the bundled sample CSV. Only when there are no expenses (else `409`) |
| GET | `/assistant/status` | `{ enabled, providers }`: is Muneem on, which AI providers have a key |
| POST | `/assistant/chat` | `{ message, history:[{role,content}] (≤ 20), voice, lang:"en-IN"\|"hi-IN" }` → `{ reply, provider, toolsUsed, suggestions }`. `429` over 30/min, `503` when the AI is off or busy |
| GET | `/assistant/notes` | What Muneem remembers (newest first) |
| DELETE | `/assistant/notes/{id}`, `/assistant/notes` | Forget one note / all notes |

Errors: `400 { "message": "...", "fieldErrors": { "amount": "must be > 0" } }`, `404` for an unknown id, `409` for conflicts.

## Deployment

**Live URL:** _coming soon_ (placeholder)

Planned AWS setup:

| Piece | AWS service | Notes |
|---|---|---|
| Database | **RDS for PostgreSQL** | Private subnet, automated backups. Backend gets `DB_URL` / `DB_USER` / `DB_PASSWORD` (from Secrets Manager or environment properties). Flyway migrates on startup. |
| Backend | **Elastic Beanstalk** (Docker or Java platform) **or ECS Fargate** behind an ALB | Uses `backend/Dockerfile` (or the jar). Set `CORS_ORIGINS` to the CloudFront domain, and `SERVER_PORT` if the platform expects another port. |
| Frontend | **S3 + CloudFront** | Build with `VITE_API_BASE_URL=https://<backend-domain>/api npm run build` and upload `frontend/dist/` to S3. CloudFront serves it with a custom error response 403/404 → `/index.html` (200) for client-side routes. |

Option: route `/api/*` through the same CloudFront distribution to the ALB. Then the frontend keeps the default `/api` and no CORS is needed.

## Repo layout

```
backend/        Spring Boot app (controller / service / repository / dto / entity / config), Flyway migrations, Dockerfile
frontend/       React + Vite app (pages / components / api / types), nginx config, Dockerfile
sample-data/    expenses.csv (single copy, used by backend and frontend)
docs/SPEC.md    design spec
docker-compose.yml
```
