# Muneem: expense assistant (chat + voice), Spec

Status: **Draft, owner answers in (2026-11-18): name Muneem, history per browser session, notes yes but light.** **Built (steps 1–6) and checked live with real keys on 2026-11-18** (API level: Gemini and Groq both answer with correct amounts, Hindi voice mode, note saving). Left to the owner: the browser check with mic (§15).
Adds to [`SPEC.md`](SPEC.md) (v1 + v2 A–F). Same rules apply: single user, no login, INR.

---

## 1. Goal
**Muneem** (shown as "Muneem · your accountant") is a personal money assistant inside the app. The user can **type or speak** to it, and it can **talk back**. It:
- answers questions about the user's own expenses ("Where did I spend most this month?", "How much on Swiggy in October?");
- compares periods ("This month vs last month?");
- **finds ways to save** (spikes, frequent small orders, subscriptions, high expenses);
- **asks cross questions** when something stands out ("Food is up 40%. Was that a one-off party, or every weekend?") and remembers the answers.

**Cost: ₹0.** It uses only free tiers and built-in browser features.

## 2. Free building blocks
| Part | Choice | Where it runs |
|---|---|---|
| Speech → text | Browser **Web Speech API** (`SpeechRecognition`), `en-IN` / `hi-IN` | Frontend (Chrome, Edge; not Firefox) |
| Text → speech | Browser **`speechSynthesis`** (Indian English / Hindi voices) | Frontend |
| Brain #1 (primary) | **Google Gemini API, free tier**, Flash model | Backend → Google |
| Brain #2 (fallback) | **Groq API, free tier** (Llama / GPT-OSS model) | Backend → Groq |

- Both providers offer an **OpenAI-compatible chat-completions endpoint with tool calling**, so the backend needs **one HTTP client and no SDK**:
  - Gemini: `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`
  - Groq: `https://api.groq.com/openai/v1/chat/completions`
- Model names are **config, not code** (free models change): defaults `gemini-flash-lite-latest` (Google's alias for the current Flash) and `openai/gpt-oss-120b` (Groq retired `llama-3.3-70b-versatile` from the free tier in Aug 2026). Override with `GEMINI_MODEL` / `GROQ_MODEL`.
- Keys: env vars, or a git-ignored `.env` (backend reads `./.env` and `../.env` via `spring.config.import`; docker compose passes them to the backend container).

## 3. Decisions
1. **The AI never does the maths.** Java computes every number with SQL (the same queries as the dashboard / compare page). The model only calls tools and turns their results into words. The system prompt says: *quote only amounts that come from a tool result; never add, subtract or estimate yourself.*
2. Tool results carry amounts **only as ready text** (`"₹4,200"`, percentages as `"41%"`), so the model can't mis-format or recompute them, and results stay small.
3. **Read-only.** In this version the assistant can't add, edit or delete expenses or rules. Its only write is its own notes (§6).
4. **Provider order:** Gemini first. If Gemini fails (429 rate limit, 5xx, timeout > 20 s, or a bad tool-call response), the **same turn is retried on Groq**. If both fail, the user sees: "Assistant is busy right now (free limit reached). Try again in a minute." The reply records which provider answered.
5. **Keys stay on the server.** They come from the env vars `GEMINI_API_KEY` and `GROQ_API_KEY` and are never sent to the browser. With one key the assistant uses only that provider. With no key, `/assistant/status` returns `enabled:false` and the page shows "Assistant is off: add an API key (see README)".
6. **Privacy:** free tiers may use prompts to improve their models. Only what a tool returns is sent (aggregates and at most 20 rows), never the whole table. The README says this plainly.
7. **Language:** it replies in the language of the question (English, Hindi or Hinglish). The UI has a voice-language picker (English-India / Hindi).
8. **Voice mode gives short answers:** at most 2–3 sentences, no tables or markdown, and a "Show details" link to see the full numbers in the chat.
9. **Cross questions:** at most **one** follow-up question per reply, and only when an insight is unclear (spike, possible subscription, high expense). It never repeats a question that a saved note already answers.
10. **Limits:** at most 5 tool rounds per user message, and no new tool round after 45 s (the model must then answer; nginx allows 120 s for `/api`); at most 20 history messages per request; at most 30 user messages per minute (protects the free quota).
11. **Chat history lives only in the browser session** (`sessionStorage`). Closing the tab clears it. The server keeps **no chat state**: each `/chat` request carries its own recent history.
12. **Light on memory (free-tier hosting).** The assistant must add almost nothing to the backend's RAM:
    - stateless server: no chat sessions, no caches of replies or expenses;
    - no new libraries (no AI SDK; Spring's `RestClient` + Jackson already in the app);
    - every tool result is capped (≤ 20 rows, ≤ 6 insights) and built from SQL aggregates, never by loading all expenses into Java;
    - request caps: message ≤ 1,000 chars, history ≤ 20 messages and ≤ 12,000 chars in total (oldest dropped first);
    - notes are tiny (§6) and read from the DB per request (one small query), not kept in memory;
    - rate limiter = one counter + one timestamp (fixed 1-minute window), not a per-request log;
    - LLM calls are blocking with a 20 s timeout; no extra thread pools.
    The browser side is light too: history is trimmed to the last 40 messages in `sessionStorage`.

## 4. Tools the model can call (all read-only except `save_note`)
| Tool | Args | Returns | Reuses |
|---|---|---|---|
| `get_spend_summary` | `from, to` | total, count, per category (Other split by nickname), share % | dashboard / compare SQL |
| `compare_periods` | `aFrom, aTo, bFrom, bTo` | same shape as `/api/compare` | `CompareService` |
| `top_vendors` | `from, to, limit≤10` | vendor, total, count | dashboard top-vendor SQL |
| `find_expenses` | `month?, category?, label?, search?, anomaliesOnly?, limit≤20` | rows (high ones marked "3.2× category average") + count and total of all matches | `ExpenseFilter` + `ExpenseQueryRepository` |
| `get_saving_insights` | `month` | list of insights (§5) | new `InsightService` |
| `save_note` | `note` (≤ 200 chars) | ok | new (§6) |

- The system prompt gives **today's date** and the list of categories and nicknames, so "this month" and "last week" resolve correctly. Weeks run Monday to Sunday (same as Compare).
- Tool argument errors (bad date, range > 366 days, unknown category or tool) go back to the model as `{error: "..."}` so it can fix them. They are never thrown to the user.
- No separate "high expenses" tool: `find_expenses` with `anomaliesOnly` covers it (fewer tools = smaller prompt on the free tier).
- At most 6 tool calls are run per round; extra calls get `{error: "skipped"}` (the API needs an answer to every call).
- **Suggestions:** the prompt asks the model to end with a line `>> a | b | c`; the server takes it off the reply and returns it as `suggestions` (≤ 3, ≤ 60 chars each).

## 5. Saving insights (`InsightService`, plain Java + SQL, no AI)
Each insight is `{ type, title, evidence, amount, amountText, possibleSaving?, possibleSavingText?, askUser? }` (`askUser` = a cross question Muneem may ask), sorted by amount, at most 6. Amount texts come from `Rupees.format` (Indian grouping: ₹1,24,500).

| Type | Rule (for the chosen month vs the month before) | Example |
|---|---|---|
| `SPIKE` | category up ≥ 25 % **and** ≥ ₹500 | "Food ₹6,200, up 41 % from ₹4,400" |
| `FREQUENT_SMALL` | one vendor ≥ 8 times, average ≤ ₹500 | "Swiggy 18 orders, avg ₹233 = ₹4,200. Half as many saves ~₹2,100" |
| `SUBSCRIPTION` | same vendor charged exactly once this month and once last month (at most once the month before), all within 10 % | "Netflix ₹649 every month" (asks: still using it?) |
| `HIGH_EXPENSE` | anomalies this month (existing 3× rule), biggest 3 | "Zomato ₹3,000 on 12 Nov, 3.2× your Food average" |
| `TOP_SHARE` | one category > 40 % of the month (month total ≥ ₹1,000) | "Shopping is 46 % of November" |
| `WEEKEND` | Fri–Sun ≥ 50 % of the month's spend (month total ≥ ₹1,000) | "58% of November 2026 spend is on Fri–Sun" |

`possibleSaving` is a simple, stated rule (e.g. "half of the frequent orders", "the full subscription"), never a model guess.

## 6. Data (Flyway `V4__assistant.sql`)
Only **notes** are stored on the server (chat history stays in the browser, decision 11).
```sql
CREATE TABLE assistant_notes (                    -- what the user told Muneem ("Netflix is shared with family")
  id          BIGSERIAL PRIMARY KEY,
  note        VARCHAR(200) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
- `save_note` keeps **at most 30 notes**: when a 31st is saved, the oldest is deleted in the same transaction. A note that is the same as an existing one (case- and space-insensitive) is not saved twice.
- All notes (≤ 30 × 200 chars ≈ 6 KB worst case) go into every system prompt. The user can see and delete them.
- Notes survive closing the tab (that is what makes Muneem personal); chats don't.

## 7. REST API (base `/api/assistant`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/status` | `{ enabled, providers:["GEMINI","GROQ"] }` |
| POST | `/chat` | `{ message (1–1,000 chars), history:[{role:"user"\|"assistant", content}] (≤ 20), voice:false, lang:"en-IN" }` → `{ reply, provider, toolsUsed, suggestions:[≤ 3 short follow-ups] }` |
| GET | `/notes` | notes, newest first |
| DELETE | `/notes/{id}` | forget one note |
| DELETE | `/notes` | forget all notes |

Errors: `400` (empty or too long message, history over the caps, bad role), `404` (unknown note), `429` (app limit), `503 { message }` (assistant off, or both providers down).
v1 is **not streaming** (simpler, and it holds no open connections). The UI shows a "Muneem is thinking…" bubble.

## 8. Backend layout (new package `com.expensemanager.assistant`)
- `AssistantController`: the routes above.
- `AssistantService`: validates and trims the incoming history, builds the prompt (system + notes + history + message), runs the tool loop (≤ 5 rounds). Stores nothing except notes.
- **Gemini thought signatures:** newer Gemini models put `extra_content` (a thought signature) on each tool call and reject the next request with 400 unless it is sent back. `ChatMessage.ToolCall` keeps it; `OpenAiCompatibleClient` sends it to Gemini only and strips it for Groq (which takes over turns Gemini started).
- `llm/`: `LlmClient` interface (tests use a fake); `OpenAiCompatibleClient` (one provider, `RestClient` on the plain JDK connection: no pool, no extra threads); `LlmProvider` `{name, baseUrl, apiKey, model}`; `FallbackLlm` tries Gemini, then Groq, on **any** failure; logs only provider + status, never the key. `AssistantConfig` wires only providers that have a key.
- `AssistantTools`: tool JSON schemas + dispatch to existing services.
- `InsightService`: §5.
- `Prompts`: system prompt text (persona: Muneem, a friendly Indian accountant who is careful with numbers; rules from §3: numbers only from tools, one follow-up max, reply in the user's language, short in voice mode).
- `application.properties`: `app.assistant.gemini.*`, `app.assistant.groq.*` (`base-url`, `api-key=${GEMINI_API_KEY:}`, `model`), `app.assistant.timeout=20s`, `app.assistant.max-notes=30`.
- `docker-compose.yml`: pass `GEMINI_API_KEY`, `GROQ_API_KEY` from a git-ignored `.env`; add `.env.example`.

## 9. Frontend
- New page **`/muneem`** (`MuneemPage.tsx`) + nav item "Muneem".
  - One chat per tab, kept in `sessionStorage` (last 40 messages). A **New chat** button clears it. A small note says "Chats clear when you close the tab. Muneem remembers only the notes below."
  - Chat: bubbles, a "thinking…" state, suggestion chips under the last reply, and a provider tag in small grey text ("via Groq").
  - Empty state chips: "Where did I spend most this month?", "How can I save money?", "This month vs last month", "Any unusual expenses?".
  - **Mic button**: tap to talk, tap again (or silence) to stop; the transcript fills the box and is sent. While it listens: a pulsing red dot. The mic is hidden in browsers without `SpeechRecognition` (tooltip: "Voice works in Chrome / Edge").
  - Replies are **not** read aloud by default. A **🔊** button under each reply reads it aloud (tap again, ■, to stop). A **Stop speaking** button shows while it talks. Tapping the mic, sending, or New chat stops any speech.
  - **Language picker:** English (India) / हिन्दी, used for both listening and speaking.
  - "Remembered" panel: lists the notes, each with delete.
- Built in step 4: `pages/MuneemPage.tsx`, `lib/muneemSession.ts` (sessionStorage, last 40, history = last 20 non-error messages), API calls in `api/client.ts`, types in `types/index.ts`. Replies are shown as plain text with light markdown (bold, bullet lines), never as HTML. A failed reply shows a red bubble with **Try again**. A "Good to know" card explains read-only, numbers from data, and the free AI plans.
- `lib/useSpeechRecognition.ts`, `lib/useSpeechSynthesis.ts` *(built)*: small hooks with feature detection (`SpeechRecognition` or `webkitSpeechRecognition`). Listening stops by itself after a pause and sends the sentence with `voice:true`; while listening the box shows the words heard so far. Mic errors (permission denied, no speech, no internet) show under the box.
- Speaking cuts the reply into sentences (Chrome stops long utterances after ~15 s), drops markdown, reads "₹6,200" as "6,200 rupees" / "6,200 रुपये" and "×" as "times" / "गुना", and picks a voice for the chosen language.
- The mic and speech stop when the user leaves the page.
- `api/client.ts`: the new calls; `types/index.ts`: the new types.
- Optional (later): a floating 💬 button on every page that opens Muneem.

## 10. Example flow
User (voice): "Is mahine sabse zyada kharcha kahan hua?"
1. Browser STT (`hi-IN`) → text → `POST /chat {voice:true}`.
2. Gemini calls `get_spend_summary(2026-11-01, 2026-11-30)` → Java returns Food ₹6,200 (38 %), …
3. Gemini calls `get_saving_insights(2026-11)` → FREQUENT_SMALL Swiggy 18×.
4. Reply: "Is mahine sabse zyada Food par gaya, ₹6,200. Isme Swiggy ke 18 orders hain. Kya ye zyada tar office lunch hai?"
5. Browser speaks it. The user answers "haan, office lunch" → model calls `save_note("Swiggy orders are mostly office lunch")` and suggests a tiffin or budget idea.

## 11. Tests
- `InsightIT` (test DB): each insight type fires at its threshold and not just below it; sorting; other months ignored. `RupeesTest`: Indian grouping. *(built)*
- `AssistantToolsIT`: each tool gives the same numbers as the Compare page / list; limits capped; bad args → `{error}`. `AssistantServiceTest`: history caps, suggestion parsing. `RateLimiterTest`. *(built)*
- `FallbackLlmTest` (`MockRestServiceServer`): Gemini answers first; 429 / timeout / empty answer → Groq; both fail → busy; no keys → off; tools and tool calls in the API shape. `AssistantStatusIT`: off without keys. *(built)*
- `AssistantIT` *(built)* with a **scripted fake `LlmClient`** (`@MockitoBean`, no real API calls): tool loop runs, round limit of 5 enforced, history caps → 400 / oldest dropped, `save_note` stored, 31st note drops the oldest, duplicate note not saved, nothing else written to the DB.
- Frontend: `npm run build` (strict TS) passes. Manual voice check in Chrome (en-IN and hi-IN).

## 12. Build plan
1. Migration V4 + `InsightService` + tests.
2. `LlmClient` + fallback + config + `/status` + tests.
3. Tools + tool loop + `/chat` + notes routes + `AssistantIT`.
4. Frontend Muneem page (typing only, `sessionStorage` history).
5. Voice: STT + TTS hooks, mic, speaker, language picker.
6. README (setup of the free keys, privacy note), `.env.example`, docker-compose env, `SPEC.md` §15 link.

## 13. Out of scope (this version)
Streaming replies, adding or deleting expenses by voice, budgets / goals, wake word ("Hey …"), server-side speech (Whisper), voice in Firefox, multiple users.

## 14. Owner answers (2026-11-18)
1. Name: **Muneem** ("your accountant").
2. Chat history: **browser session only** (decision 11).
3. Notes: **yes**, kept small for free-tier hosting (decision 12, §6).

## 15. Owner checklist: first live run
Everything is built and tested without a real AI. These steps connect it to the free AI and check it end to end.

| # | Step | Who | Status |
|---|---|---|---|
| 1 | Create `.env` in the repo root with empty `GEMINI_API_KEY=` / `GROQ_API_KEY=` lines (git-ignored) | Claude | ✅ done |
| 2 | Get the free keys: Gemini https://aistudio.google.com/apikey, Groq https://console.groq.com/keys | Owner | ✅ done |
| 3 | Paste each key right after its `=` in `.env` (no quotes, no spaces). Never paste keys into chat, code or docs | Owner | ✅ done (Claude removed stray quotes/spaces) |
| 4 | Restart the backend: `cd backend && ./mvnw spring-boot:run` (reads `.env`; Flyway adds the `assistant_notes` table to the main DB). Docker: `docker compose up --build` | Owner | ⏳ |
| 5 | Start the frontend: `cd frontend && npm run dev` (use `-- --port 5180` if 5173 is taken) and open `/muneem` in **Chrome or Edge** | Owner | ⏳ |
| 6 ✅ (Claude, port 8095) | Check `GET /api/assistant/status` → `enabled: true`, `providers: ["GEMINI","GROQ"]` (the page shows no amber "Muneem is off" box) | Owner | ⏳ |
| 7 ✅ (Claude, API) | Typed test: "Where did I spend most this month?" → a reply with real ₹ amounts and a "via Gemini" / "via Groq" tag | Owner | ⏳ |
| 8 | Voice test: pick हिन्दी, tap the mic, allow the microphone, ask in Hindi → Hindi reply, read aloud | Owner | ⏳ |
| 9 ✅ (Claude, API) | Notes test: answer one of Muneem's follow-up questions → it appears under "What Muneem remembers" | Owner | ⏳ |
| 10 | Report any error text. A rejected model name → set `GEMINI_MODEL` / `GROQ_MODEL` in `.env` to a current free tool-calling model | Owner → Claude | ⏳ |

**Live check results (2026-11-18):** `gemini-2.5-flash` is no longer available to new keys (404); `gemini-flash-latest` was overloaded (503) and `gemini-3.5-flash` timed out; **`gemini-flash-lite-latest` works** (~7 s per answer) and is now the default. Groq `openai/gpt-oss-120b` works (~10 s). Answers matched the DB (Nov total ₹42,647.50, Shopping ₹30,249.50). Steps 4, 5 and 8 (the owner's own backend on 8080 + the browser with mic) are still the owner's.

**What to expect on free tiers:** limits are small and change often (some reports say about 20 requests a day for free Gemini Flash; unverified). When Gemini is out, Groq answers ("via Groq"). When both are out, Muneem says he is busy; try again later.

