# Mini Expense Manager — Spec

Status: **v1 built. v2 additions A–F built, described at the end of this file:** A uncategorized vendors panel + nicknames ("Other · Gym"), B month-over-month card, C search + server-side pagination, D export to CSV, E tests, F Compare page (`/compare`).

---

## 1. Goal
A small full-stack app to track daily expenses:
add one by hand or upload many from a CSV, auto-categorize by vendor, flag unusually high expenses, and show a summary dashboard.

## 2. Tech stack
| Layer | Choice |
|---|---|
| Backend | Java 21 (Temurin LTS), Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, Flyway, Apache Commons CSV, Maven wrapper (`mvnw`) |
| Database | PostgreSQL 18 (local install and Docker image `postgres:18`, same version everywhere) |
| Frontend | React 18 + TypeScript (Vite), React Router, Recharts (charts), Tailwind CSS |
| Tests | JUnit 5 + Mockito (logic), Spring Boot integration test on a local test DB |
| Extras | `docker-compose.yml` (db + backend + frontend) for reviewers; sample CSV in repo |

## 3. Decisions (also go into README "Assumptions")
1. **Single user, no login.**
2. **Currency is INR**, stored as `NUMERIC(12,2)` rupees (never float). Amount must be > 0.
3. **Categories** are a fixed seeded list: Food, Groceries, Transport, Shopping, Entertainment, Bills & Utilities, Travel, Health, Other.
4. **Rules** are seeded (~20) and editable from a Rules page (add / edit / delete).
5. **Matching:** case-insensitive, *vendor name contains rule keyword*. If several keywords match, the **longest keyword wins** (`"Amazon Prime"` → Entertainment beats `"Amazon"` → Shopping). No match → **Other**.
6. Category is decided **once, when the expense is saved**. Editing a rule later does **not** change old expenses (re-apply is out of scope).
7. **Anomaly:** amount > 3 × average of **all** expenses in the same category, **including itself** (owner's choice). Checked **live** on every read, so flags stay correct as data grows.
8. **CSV:** header row required; bad rows are skipped and reported; good rows are saved.
9. Expenses can be added and deleted; editing an expense is out of scope.
10. **Validation (same for manual and CSV):** date required and **not in the future**; amount > 0 and **≤ ₹1,00,00,000** (1 crore); vendor required, 1–150 chars after trim; description ≤ 500 chars.
11. **Duplicates:** same date + same amount + same vendor (case/space-insensitive) counts as a duplicate. In a CSV upload it is skipped and reported as `"duplicate"` (whether it matches a row already in the DB or an earlier row in the same file). Manual add is allowed, but the UI shows a yellow warning: "Looks like a duplicate of an expense on 18 Nov".

## 4. Data model (Flyway `V1__schema.sql`)
```sql
CREATE TABLE categories (
  id    SMALLSERIAL PRIMARY KEY,
  name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE vendor_rules (
  id           BIGSERIAL PRIMARY KEY,
  keyword      VARCHAR(100) NOT NULL,          -- stored lower-case, trimmed
  category_id  SMALLINT NOT NULL REFERENCES categories(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_vendor_rules_keyword UNIQUE (keyword)
);

CREATE TABLE expenses (
  id           BIGSERIAL PRIMARY KEY,
  expense_date DATE NOT NULL,
  amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  vendor       VARCHAR(150) NOT NULL,
  description  VARCHAR(500),
  category_id  SMALLINT NOT NULL REFERENCES categories(id),
  source       VARCHAR(10) NOT NULL DEFAULT 'MANUAL',   -- MANUAL | CSV
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_expenses_category ON expenses(category_id);
CREATE INDEX idx_expenses_date     ON expenses(expense_date);
-- fast duplicate lookup (date + amount + normalized vendor)
CREATE INDEX idx_expenses_dup ON expenses(expense_date, amount, lower(trim(vendor)));
```
`V2__seed.sql`: the 9 categories plus about 20 rules (swiggy, zomato → Food; blinkit, zepto, bigbasket → Groceries; uber, ola, rapido → Transport; amazon, flipkart, myntra → Shopping; netflix, amazon prime, bookmyshow → Entertainment; airtel, jio, electricity → Bills & Utilities; irctc, indigo, makemytrip → Travel; apollo, pharmeasy → Health).

**Why this shape**
- Categories get their own table: dropdowns come from it, and a foreign key stops typos like "food" vs "Food".
- `is_anomaly` is **not stored**. It depends on the other rows, so it is computed in SQL when read (see §6).
- `source` keeps track of how a row arrived (manual or CSV), which helps when debugging uploads.

## 5. Categorization logic
`CategorizationService.categorize(vendor)`:
1. `v = vendor.trim().toLowerCase()`
2. Load all rules (one small table, cached in memory; the cache is cleared on any rule change).
3. Keep the rules where `v.contains(rule.keyword)` and pick the one with the longest keyword.
4. If none match, return the **Other** category.

Used by both manual add and CSV upload, so the behaviour is identical.

**Rule keyword validation:** trimmed, lower-cased, **2–100 characters**, unique (a duplicate gives `409`). One-letter keywords like `"a"` are rejected because they would match almost every vendor.

## 6. Anomaly logic
**Rule:** `amount > 3 × AVG(amount of all expenses in that category, including this one)`.

Computed in SQL, so it is always fresh. The average is **always taken over all expenses in the category (all time), before any filter**. Filtering the list (by month, category or "only high") must never change the average or the flags. So the average lives in a CTE and filters apply only to the outer query:
```sql
WITH cat_avg AS (
  SELECT category_id, AVG(amount) AS category_avg
  FROM expenses GROUP BY category_id
)
SELECT e.*, c.name AS category, a.category_avg,
       e.amount / a.category_avg        AS times_avg,     -- e.g. 3.2
       e.amount > 3 * a.category_avg    AS is_anomaly
FROM expenses e
JOIN categories c ON c.id = e.category_id
JOIN cat_avg   a ON a.category_id = e.category_id
WHERE (:month IS NULL OR date_trunc('month', e.expense_date) = :month)   -- filters only here
  AND (:categoryId IS NULL OR e.category_id = :categoryId);
```
The dashboard anomaly list uses the same CTE, so a "high" expense looks the same on every page.
**Known effect of "including itself"** (goes into the Design Note): since the expense raises its own average, a category needs **at least 4 expenses** before anything can be flagged. With 3 or fewer it is mathematically impossible. This is accepted.

Example (Food): 200, 300, 250, 3000 → avg 937.50 → 3× = 2812.50 → **3000 flagged** (3.2× avg).

## 7. REST API (base `/api`, JSON)
| Method | Path | Purpose |
|---|---|---|
| GET | `/categories` | List categories |
| GET | `/expenses?month=2026-11&categoryId=&label=&anomaliesOnly=false&q=&page=0&size=50` | One page of expenses, newest first, each with `category`, `label`, `categoryAvg`, `timesAvg`, `anomaly` → `{ items, page, size, totalItems, totalPages, totalAmount }` (v2 C) |
| GET | `/expenses/export` (same filters, no paging) | CSV download of what the filters show (v2 D) |
| POST | `/expenses` | Add one expense; returns the saved row **with its anomaly info** and `possibleDuplicate: true/false` |
| DELETE | `/expenses/{id}` | Delete one |
| POST | `/expenses/upload` (multipart `file`) | CSV import → `{ total, saved, skipped, errors:[{row, reason}], anomaliesFound }` |
| GET | `/rules` | List rules |
| POST | `/rules` | `{ keyword, categoryId, label? }` (label = nickname, only with Other); response has `recategorized` (v2 A) |
| PUT | `/rules/{id}` | Update (same body and response) |
| DELETE | `/rules/{id}` | Delete |
| GET | `/vendors/uncategorized` | Vendors in Other with no nickname, for the Rules panel (v2 A) |
| GET | `/labels` | Nicknames in use, for filters (v2 A) |
| GET | `/dashboard?month=2026-11` (optional month) | `{ months, monthlyTotals, topVendors, anomalies:{count, items}, totals:{spend, count}, monthOverMonth, uncategorizedVendors }` (v2 A, B) |
| GET | `/compare?aFrom=&aTo=&bFrom=&bTo=` | Spend per category in period A vs B (v2 F) |
| POST | `/sample-data` | Loads `sample-data/expenses.csv` (bundled in the jar) through the same CSV import. Allowed **only when there are no expenses** (else `409`). Returns the same result as upload. |

**POST `/expenses` body:** `{ "date":"2026-11-18", "amount":3000, "vendor":"Zomato", "description":"Party order" }`
**Errors:** `400 { "message": "...", "fieldErrors": { "amount": "must be > 0" } }`, `404` for an unknown id, `409` for a duplicate rule keyword.
CORS allows `http://localhost:5173`.

## 8. CSV upload
- Header (case-insensitive, any column order): `date,amount,vendor,description` (`description` optional).
- Date: `YYYY-MM-DD` or `DD-MM-YYYY` (also `/`). Amount: number > 0; `₹` and `,` are stripped (`"₹1,250.50"` works).
- Max file size 2 MB, **max 5,000 data rows** (more → `400`, nothing saved), `.csv` only. Blank lines are ignored.
- Each row is validated alone with the rules in §3 (no future date, amount ≤ ₹1 crore, …). Good rows are saved in one transaction; a bad row is skipped with `{row: 7, reason: "amount must be > 0"}`.
- **Duplicates** (§3.11) are skipped with `reason: "duplicate"` and counted separately: the result also has `duplicates: n`. Uploading the same file twice therefore saves 0 rows the second time.
- A wrong or missing header → `400`, nothing is saved.
- `sample-data/expenses.csv` (~40 rows over 3 months, including a few anomalies and a few bad rows) ships in the repo.

## 9. Dashboard (SQL)
1. **Monthly totals per category:** `GROUP BY date_trunc('month', expense_date), category`, for the last 6 months. Shown as a stacked bar chart and a table (month rows × category columns, with a total column).
2. **Top 5 vendors by total spend:** `GROUP BY lower(trim(vendor)) ORDER BY SUM(amount) DESC LIMIT 5`, showing the display name, total and count. Respects the month filter (default: all time). **Display name** = the spelling used most often in that group (`mode() WITHIN GROUP (ORDER BY trim(vendor))`; on a tie, the alphabetically first). So "swiggy" ×2 and "Swiggy" ×5 show as **Swiggy**.
3. **Anomalies:** a count card plus a list (date, vendor, category, amount, "3.2× avg"). Respects the month filter.

## 10. Frontend pages
| Page | Content |
|---|---|
| **Dashboard** `/` | Month filter (All time / month); cards: total spend, number of expenses, **anomaly count (red)**; "vendors in Other without a rule" line (A); month-over-month card (B); monthly chart + table; top 5 vendors; anomaly list |
| **Compare** `/compare` | Month vs month, week vs week or custom dates; guidance badge and notes; Total / per-day toggle; A, B, change tiles; grouped bar chart + table per category (F) |
| **Expenses** `/expenses` | Add form (date, amount, vendor, description) with a live hint "→ will be categorized as **Food**" (or "Other · Gym"); table with filters (month, category / nickname, "only high expenses"), search, 50 per page (C), **Export CSV** (D) and delete |
| **Upload** `/upload` | File picker / drag-drop, link to download the sample CSV, result summary (saved / skipped / anomalies) and a table of skipped rows with reasons |
| **Rules** `/rules` | **Uncategorized vendors** panel with **Create rule** (A); table of keyword → category (→ "Other · nickname"); add / edit / delete inline, nickname field when Other is picked; duplicate-keyword error shown |

**How a high expense looks (anomaly UI):**
- Table row has a **light-red background** and a red left border.
- A red badge **"⚠ High · 3.2× avg"** next to the amount; the amount is shown in bold red.
- Hovering the badge shows a tooltip: "₹3,000 is 3.2× the Food average (₹937.50)".
- **After adding** an expense that is flagged, a red toast appears: "⚠ High expense: ₹3,000 at Zomato is 3.2× your Food average."
- After a CSV upload, the summary says "3 high expenses found", linking to the filtered list.
- Dashboard anomaly card is red and links to `/expenses?anomaliesOnly=true`.

Amounts are formatted with `Intl.NumberFormat('en-IN', {style:'currency', currency:'INR'})`.

**Empty / first-run state:** with no expenses, the dashboard shows a friendly empty card: "No expenses yet", with buttons **Add expense**, **Upload CSV** and **Load sample data** (calls `POST /api/sample-data`, then refreshes). The other pages have their own empty messages ("No rules yet", "No expenses match these filters").

**Loading and error states (every page):**
- Loading: skeleton rows for tables and cards, and a spinner on buttons. Buttons are disabled while saving so nothing is sent twice.
- Errors: field errors under the matching input (from `fieldErrors`); other errors as a red toast; if the backend is down, a banner "Can't reach server, retry".
- Success: a green toast ("Expense added", "Rule saved", "38 rows imported").

**Responsive (works on every device, as the JD asks):**
- Desktop ≥ 1024px: top nav, dashboard in a 2-column grid.
- Tablet 640–1023px: 1–2 columns; tables scroll inside their own box.
- Phone < 640px: hamburger menu; expense table turns into **cards** (vendor, amount, badge, date); forms full width; charts resize.
- Checked at 360px, 768px and 1280px with no horizontal page scroll.

## 11. Repo layout
```
iConcile Assignement/
├── backend/            Spring Boot (controller / service / repository / dto / entity / config)
│   └── src/main/resources/db/migration/  V1__schema.sql, V2__seed.sql
├── frontend/           Vite React TS (pages/, components/, api/, types/, lib/ = format, periods, colors, categorize helpers)
├── sample-data/expenses.csv
├── docs/SPEC.md        this file
├── docker-compose.yml
└── README.md           setup, tech, assumptions, Design Note
```
Config through env vars with local defaults: `DB_URL=jdbc:postgresql://localhost:5432/expense_manager`, `DB_USER`, `DB_PASSWORD`.

## 12. Tests
- `CategorizationServiceTest`: exact match, case/space, partial ("SWIGGY INSTAMART"), longest keyword wins, no match → Other.
- `CsvParserTest`: both date formats, ₹/commas, missing header, bad amount, empty vendor, blank lines, future date, amount over 1 crore, more than 5,000 rows.
- `RuleValidationTest`: 1-char keyword rejected, duplicate keyword → 409.
- `AnomalyIT` (local test DB `expense_manager_test`): the 200/300/250/3000 example flags only 3000; 3 rows can never flag; **filtering by month does not change the average or the flags**.
- `DuplicateIT`: uploading the same CSV twice → second time `saved: 0`, all `duplicate`; a duplicate inside one file is caught.
- `TopVendorsIT`: "swiggy" ×2 + "Swiggy" ×5 → one row named **Swiggy**.
- `SampleDataIT`: works on an empty DB; `409` when expenses already exist.
- Frontend: TypeScript strict build passes (`npm run build`).
- v2 tests: see E (`UncategorizedIT`, `MonthOverMonthIT`, `PagingSearchIT`, `ExportIT`, …) and F (`CompareIT`). 65 tests in total, all passing.

## 13. Out of scope (trade-offs, for the Design Note)
Login / multi-user, editing expenses, re-applying rules to old expenses, multiple currencies, regex rules, pagination (a simple limit of 500 rows instead), AWS deploy (a short "how I'd deploy on AWS" note in the README instead: RDS + Elastic Beanstalk/ECS + S3/CloudFront).

## 14. Build plan
1. Setup: install JDK 21, `git init` in this folder, create the `expense_manager` DB.
2. Backend: schema + seed → categorization → expenses API → anomaly query → CSV upload → rules API → dashboard API → tests.
3. Frontend: layout + API client → Expenses page → Upload → Rules → Dashboard → anomaly styling and toasts.
4. Sample CSV, docker-compose, README + Design Note, final run-through (including a responsive check at 360 / 768 / 1280 px).

## 15. Later (not in this version)
Still parked: Swagger, CI, edit expense, budgets, frontend tests, live deploy. **Muneem** (chat + voice AI assistant) is specified and built separately: see [`ASSISTANT-SPEC.md`](ASSISTANT-SPEC.md). (Uncategorized panel with nicknames, month-over-month, search + pagination, CSV export and the Compare page are built: see v2 below.)

---

# v2 additions (approved 2026-11-18)

Picked from §15: **A** Uncategorized vendors panel (with nicknames), **B** Month-over-month card, **C** Search + server-side pagination, **D** Export to CSV. Added later the same day: **F** Compare page. CI is skipped for now.

Changes to v1 decisions:
- §3.6 changes: **creating or editing a rule re-applies the rules to expenses that are in Other** (never to expenses in a real category). See A.4.
- §13 "pagination" is no longer out of scope; the 500-row limit on the expense list is gone (C).

## A. Uncategorized vendors panel + nickname (label)
**A.1 Idea.** A panel lists the vendors whose expenses landed in **Other** with no nickname. Each row has **Create rule**. When the user picks **Other** in that form, they can type a **nickname** (e.g. "Gym"), so the expense stays in Other but is shown and filtered as **Other · Gym**.

**A.2 Data (Flyway `V3__labels.sql`)**
```sql
ALTER TABLE vendor_rules ADD COLUMN label VARCHAR(50);   -- only with category Other
ALTER TABLE expenses     ADD COLUMN label VARCHAR(50);   -- copied from the matching rule
CREATE INDEX idx_expenses_other_label ON expenses(category_id, label);
```
- Label rules: trimmed; blank → `null`; 1–50 chars; allowed **only when the category is Other** (else `400 fieldErrors.label = "is only for Other"`). Changing a rule's category away from Other clears its label.
- The 9 categories stay a fixed list; a label is not a category (no own anomaly average: anomaly math stays per category).

**A.3 Categorization.** `CategorizationService.match(vendor)` returns `{categoryId, label, matched}` (longest keyword wins, as before). Manual add and CSV import store both `category_id` and `label`. The "will be categorized as" hint shows `Other · Gym` when the rule has a label.

**A.4 Re-apply on rule create / update.** After the rule is saved, every expense **in Other** (with or without a label) is run through `match()` again; when a rule matches and the result differs, the expense gets the new category + label in one SQL batch. Expenses in a real category are never touched; deleting a rule changes no expense. The rule response gets `recategorized: n`, and the UI toasts "Rule saved · 4 expenses moved".

**A.5 API**
| Method | Path | Purpose |
|---|---|---|
| GET | `/vendors/uncategorized` | `[{ vendor, keyword, count, total, lastDate }]`: expenses with category Other and no label, grouped by `lower(trim(vendor))`, display name = most used spelling (same as top vendors), ordered by total desc, max 50. `keyword` = suggested rule keyword (lower-cased trimmed vendor, cut to 100 chars). |
| GET | `/labels` | Distinct labels in use (expenses + rules), sorted, for filters. |
| POST/PUT | `/rules` | Body gains optional `label`. Response gains `label` and `recategorized`. |

**A.6 UI**
- **Rules page**: the panel "Uncategorized vendors" sits above the rules list. Row: vendor, "3 expenses · ₹1,250 · last 12 Nov", **Create rule**. The button opens an inline form: keyword (pre-filled, editable), category, and when **Other** is chosen a **Nickname** field (required in the panel, since a rule to Other without a nickname would leave the vendor in the panel). Saving reloads panel + rules. Empty state: "All vendors are sorted 🎉".
- The normal Add/Edit rule forms also show the optional Nickname field when Other is chosen. The rules list shows `→ Other · Gym`.
- **Dashboard**: when the panel has rows, a slim line under the stat cards: "5 vendors are in Other without a rule · Sort them →" (links to `/rules`).
- **Expenses**: the category column shows `Other · Gym`; the category filter lists each label under Other ("Other · Gym") and filters by `categoryId` + `label`.

## B. Month-over-month card (dashboard)
- Compares a month with the month before it. Month = the dashboard's month filter, or the **current month** when "All time" is chosen.
- One SQL query with `FILTER` aggregates and `GROUPING SETS` (per category + label, plus a grand total row):
```sql
SELECT c.id, c.name, e.label, GROUPING(c.id) AS is_total,
       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date >= :cur), 0) AS current,
       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date <  :cur), 0) AS previous
FROM expenses e JOIN categories c ON c.id = e.category_id
WHERE e.expense_date >= :prev AND e.expense_date < :next
GROUP BY GROUPING SETS ((c.id, c.name, e.label), ())
```
- `changePct = (current − previous) / previous × 100`, 1 decimal; `null` when previous is 0 (UI shows "new").
- Dashboard response gains `monthOverMonth: { month, previousMonth, total:{current, previous, changePct}, rows:[{categoryId, category, label, current, previous, changePct}] }`, rows ordered by current desc.
- UI card: "Nov 2026: ₹12,400 · ▲ 18% vs Oct", then one line per category (Other split by label). Up = red (spending more), down = green, "new" = gray, a category that dropped to ₹0 shows ▼ 100%.

## C. Search + server-side pagination (expenses list)
- `GET /expenses?month=&categoryId=&label=&anomaliesOnly=&q=&page=0&size=50` now returns a page:
  `{ items:[...], page, size, totalItems, totalPages, totalAmount }`.
- `q`: trimmed, max 100 chars, case-insensitive "contains" on vendor **or** description (`ILIKE` with `%`, `_` and `\` escaped). Blank → no search.
- `page` ≥ 0 (default 0); `size` 1–100 (default 50); out of range → `400`. A page past the end returns empty `items` with the real totals.
- Two queries share the same WHERE: one `COUNT(*)` + `SUM(amount)` for the totals, one `ORDER BY expense_date DESC, id DESC LIMIT :size OFFSET :offset` for the rows. The anomaly CTE is unchanged, so flags stay the same on every page.
- UI: search box (300 ms debounce) next to the filters; `q` and `page` live in the URL; changing any filter goes back to page 1. Under the list: "1–50 of 312 · ₹1,24,500" and Prev / "Page 1 of 7" / Next. Works the same on phone cards.
- Dashboard anomaly list keeps its own limit of 500.

## D. Export to CSV
- `GET /expenses/export` with the same filters as C (no paging) → `text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="expenses-<month or all>.csv"`.
- Columns: `date,amount,vendor,description,category,label,high_expense`. `date` is `YYYY-MM-DD`, amount plain `1250.50`, `high_expense` is `yes`/`no`.
- Rows are streamed from the DB (no full list in memory), newest first, max 100,000 rows.
- **CSV injection guard:** a text cell starting with `=`, `+`, `-`, `@`, tab or CR gets a leading `'` so Excel won't run it as a formula.
- **Round trip:** the importer (§8) now accepts and ignores the extra columns `category`, `label`, `high_expense`, so an exported file can be uploaded again (rows already present are skipped as duplicates).
- UI: an **Export CSV** button above the expense list downloads exactly what the current filters + search show (all pages).

## E. Tests for v2
- `CategorizationServiceTest`: label comes back with the match; no match → Other, no label.
- `RuleValidationTest`: label with a non-Other category → 400; label over 50 chars → 400.
- `UncategorizedIT`: Other vendors are listed and grouped; creating a rule moves their Other expenses (count in `recategorized`) and they leave the panel; a rule to Other + nickname labels them; expenses in a real category are never moved.
- `MonthOverMonthIT`: per-category current / previous / changePct, `null` when previous is 0, total row, month filter picks the compared month.
- `PagingSearchIT`: page sizes and totals, search on vendor and description, `%` is literal, bad `size` → 400, flags identical across pages.
- `ExportIT`: header + rows follow filters, formula cell is escaped, the exported file re-imports with all rows reported as duplicates.
- Frontend: `npm run build` (strict TS) passes.

## F. Compare page (`/compare`, added 2026-11-18)
- **Modes:** *Month vs month* (calendar months, last 24 to pick from), *Week vs week* (Mon–Sun weeks, last 26), *Custom dates* (two date ranges, each ≤ 366 days, start not in the future). Each mode card says what it suits best: months for bills / rent / budget, weeks for everyday spend. Defaults: this month vs last month, this week vs last week, last 30 days vs the 30 before. Selection lives in the URL (`?mode=month&a=2026-10&b=2026-11`, `?mode=week&a=<Monday>&b=<Monday>`, `?mode=custom&aFrom&aTo&bFrom&bTo`). Switching mode carries the dates over; **Swap** exchanges A and B.
- **A is the baseline**, change = B − A; % = (B − A) / A, "new" when A is 0. Spending more is red, less is green.
- **Guidance line:** a badge names the kind (Monthly / Weekly / Equal-length / Different lengths) and notes explain it: unequal days, a period still running ("18 of 30 days so far"), overlapping periods (shared days count in both), same period twice.
- **Total / Per-day average** toggle. Per-day divides by the days that have already happened, so a running period is compared by its pace. Default is per-day when the lengths differ, else total.
- **Output:** A, B and change tiles; biggest rise / drop; grouped bar chart (A blue, B orange, one pair per category, Other split by nickname); table with A, B, change, %.
- **API:** `GET /api/compare?aFrom&aTo&bFrom&bTo` → `{ a:{from,to,days,count}, b:{…}, total:{a,b,changePct}, rows:[{categoryId, category, label, a, b, changePct}] }`, rows by B desc then A desc. One query with `FILTER` aggregates + `GROUPING SETS` (same as B). Bad / missing date, end before start, > 366 days → 400.
- **Tests:** `CompareIT`: two months per category, overlapping custom ranges, empty periods, 400 cases.
