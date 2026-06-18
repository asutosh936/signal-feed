# Trend Tracker — Implementation Plan

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [MVP](#2-mvp)
   - 2.1 [MVP Goals and Non-Goals](#21-mvp-goals-and-non-goals)
   - 2.2 [MVP Architecture](#22-mvp-architecture)
   - 2.3 [Tech Stack](#23-tech-stack)
   - 2.4 [Project Structure](#24-project-structure)
   - 2.5 [Component Responsibilities](#25-component-responsibilities)
   - 2.6 [Schedule Design](#26-schedule-design)
   - 2.7 [Email Design](#27-email-design)
   - 2.8 [Claude Prompt Design](#28-claude-prompt-design)
   - 2.9 [Configuration](#29-configuration)
   - 2.10 [Task List](#210-task-list)
   - 2.11 [MVP Acceptance Criteria](#211-mvp-acceptance-criteria)
3. [Post-MVP](#3-post-mvp)
   - 3.1 [What to Observe During MVP](#31-what-to-observe-during-mvp)
   - 3.2 [Expected Learnings](#32-expected-learnings)
   - 3.3 [Recommended Next Steps](#33-recommended-next-steps)

---

## 1. Project Overview

**Trend Tracker** is a personal backend-only Spring Boot application that automatically discovers the latest trending AI tools and delivers them via email throughout the day.

In its MVP form it is intentionally minimal: a scheduler fires 5 times per day, each run makes one Claude API call to find a single trending AI tool (with details, pros, and cons), and immediately sends an email. No database. No UI. No persistence.

---

## 2. MVP

### 2.1 MVP Goals and Non-Goals

#### Goals

- Spring Boot 3.x application that boots and runs as a standalone JAR
- Scheduler fires 5 times per day at fixed times
- Each run makes exactly one Claude API call (web_search enabled) to find one trending AI tool
- Each run sends one HTML email with: tool name, category, description, pros, cons, and link
- Configuration (API key, email, schedule) driven entirely by `application.yml` and environment variables
- Application is runnable locally with `mvn spring-boot:run`

#### Non-Goals (explicitly excluded from MVP)

- No database or any data persistence
- No web UI or Thymeleaf templates
- No deduplication (the same tool may appear across multiple emails)
- No retry logic
- No error recovery beyond basic logging
- No feed extensibility framework
- No authentication

---

### 2.2 MVP Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot App                      │
│                                                       │
│  ┌─────────────────────────────────────────────┐    │
│  │           AIToolsScheduler                   │    │
│  │  @Scheduled × 5 per day                      │    │
│  │  (fixed cron expressions)                    │    │
│  └────────────────┬────────────────────────────┘    │
│                   │ calls                             │
│  ┌────────────────▼────────────────────────────┐    │
│  │           AIToolsService                     │    │
│  │  1. Build prompt                             │    │
│  │  2. Call Claude API (web_search enabled)     │    │
│  │  3. Parse JSON response → AITool POJO        │    │
│  └────────────────┬────────────────────────────┘    │
│                   │ passes AITool                     │
│  ┌────────────────▼────────────────────────────┐    │
│  │           EmailService                       │    │
│  │  1. Build HTML email string                  │    │
│  │  2. Send via JavaMailSender                  │    │
│  └─────────────────────────────────────────────┘    │
│                                                       │
│  ┌─────────────────────────────────────────────┐    │
│  │           AppConfig                          │    │
│  │  ChatClient bean (Anthropic)                 │    │
│  │  JavaMailSender bean                         │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

**Data flow per run:**

```
Scheduler tick
    → AIToolsService.fetchTrendingTool()
        → Build prompt (PromptBuilder)
        → ChatClient.call() with web_search tool
        → Parse JSON response → AITool
    → EmailService.send(AITool)
        → Build HTML string
        → JavaMailSender.send()
```

---

### 2.3 Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.x |
| AI Client | Spring AI — Anthropic adapter | 1.x |
| Email | Spring Mail (`JavaMailSender`) | — |
| Scheduler | Spring Task (`@Scheduled` + `@EnableScheduling`) | — |
| JSON parsing | Jackson (`ObjectMapper`) | bundled with Spring Boot |
| Build | Maven | 3.9.x |
| Java toolchain | Eclipse Temurin 17 | — |

**Key Maven dependencies:**

| Artifact | Purpose |
|---|---|
| `spring-boot-starter` | Core Spring Boot |
| `spring-ai-anthropic-spring-boot-starter` | Claude API via Spring AI |
| `spring-boot-starter-mail` | `JavaMailSender` |
| `spring-boot-starter-test` | Unit testing |

No `spring-boot-starter-web`, no `spring-boot-starter-data-jpa`, no `spring-boot-starter-thymeleaf` in MVP.

---

### 2.4 Project Structure

```
trend-tracker/
├── pom.xml
│
└── src/
    ├── main/
    │   ├── java/com/trendtracker/
    │   │   │
    │   │   ├── TrendTrackerApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   └── AppConfig.java              # ChatClient bean, JavaMailSender bean
    │   │   │
    │   │   ├── model/
    │   │   │   └── AITool.java                 # Plain record: name, category, description,
    │   │   │                                   #   pros (List<String>), cons (List<String>), link
    │   │   │
    │   │   ├── service/
    │   │   │   ├── AIToolsService.java         # Calls Claude, parses AITool
    │   │   │   ├── PromptBuilder.java          # Builds system + user prompt strings
    │   │   │   └── EmailService.java           # Builds HTML + sends email
    │   │   │
    │   │   └── scheduler/
    │   │       └── AIToolsScheduler.java       # 5 @Scheduled methods
    │   │
    │   └── resources/
    │       └── application.yml                 # All config lives here
    │
    └── test/
        └── java/com/trendtracker/
            ├── service/
            │   ├── AIToolsServiceTest.java     # Stub ChatClient
            │   └── EmailServiceTest.java       # MockJavaMailSender
            └── scheduler/
                └── AIToolsSchedulerTest.java   # Verify scheduler calls service
```

Total: **8 Java files.** That is the entire MVP.

---

### 2.5 Component Responsibilities

#### `TrendTrackerApplication`
- Standard `@SpringBootApplication` entry point
- Annotated with `@EnableScheduling` to activate the scheduler

#### `AppConfig`
- Defines `ChatClient` bean configured with the Anthropic adapter and API key from `application.yml`
- Defines `JavaMailSender` bean configured with SMTP properties from `application.yml`
- No business logic

#### `AITool` (record)
- Immutable data carrier for one tool's details
- Fields: `name` (String), `category` (String), `description` (String), `pros` (List\<String\>), `cons` (List\<String\>), `link` (String, nullable)
- Deserialised from Claude's JSON response by Jackson

#### `PromptBuilder`
- Stateless class (or `@Component`) with two methods: `buildSystemPrompt()` and `buildUserPrompt()`
- System prompt defines Claude's role and the exact JSON output schema
- User prompt includes today's date and the instruction to find exactly one tool
- Keeping prompts here makes them easy to iterate without touching service logic

#### `AIToolsService`
- Injects `ChatClient` and `PromptBuilder`
- Single public method: `fetchTrendingTool()` → returns `AITool`
- Calls Claude with the `web_search_20250305` built-in tool enabled in request options
- Extracts text blocks from the response content array
- Uses a regex to isolate the JSON object from any surrounding model preamble
- Deserialises JSON to `AITool` via `ObjectMapper`
- On parse failure or empty response: logs error and throws `AIToolsFetchException` (runtime)
- The scheduler catches this and logs; no email is sent for a failed run

#### `EmailService`
- Injects `JavaMailSender` and reads recipient address + sender from `application.yml`
- Single public method: `send(AITool tool)`
- Builds the HTML email as an inline String (no template engine — plain string concatenation or a `StringBuilder` is sufficient for MVP)
- Creates a `MimeMessage`, sets subject, HTML body, from, to
- Sends via `JavaMailSender.send()`
- Subject line format: `🤖 AI Tool Spotlight — {tool.name()} [{HH:mm}]`

#### `AIToolsScheduler`
- Five `@Scheduled` methods, each with a distinct `cron` expression
- Each method calls `aiToolsService.fetchTrendingTool()` and passes the result to `emailService.send()`
- All five methods contain identical logic — they differ only in their cron expression
- Wraps calls in try/catch; logs failure without rethrowing (so one failed run doesn't affect the next)

---

### 2.6 Schedule Design

Five fixed times spread across the day. Cron expressions are for a server running in UTC — adjust the hour offsets to match your local timezone in `application.yml`.

| Run | Local Time (IST, UTC+5:30) | Cron (UTC) |
|---|---|---|
| Morning | 8:00 AM | `0 30 2 * * *` |
| Mid-morning | 11:00 AM | `0 30 5 * * *` |
| Afternoon | 2:00 PM | `0 30 8 * * *` |
| Evening | 5:00 PM | `0 30 11 * * *` |
| Night | 8:00 PM | `0 30 14 * * *` |

Cron expressions are externalised to `application.yml` so they can be changed without recompiling.

```yaml
# application.yml
scheduler:
  cron:
    run1: "0 30 2 * * *"
    run2: "0 30 5 * * *"
    run3: "0 30 8 * * *"
    run4: "0 30 11 * * *"
    run5: "0 30 14 * * *"
```

Each `@Scheduled` method reads its cron from `${scheduler.cron.runN}`.

---

### 2.7 Email Design

Each email is a single-tool spotlight. No digest. No list.

**Subject:** `🤖 AI Tool Spotlight — {Tool Name} [08:00]`

**Body (HTML, inline styles for email client compatibility):**

```
┌──────────────────────────────────────────────────────┐
│  🤖  AI Tool Spotlight                                │
│  {Day, Date} · {Time}                                 │
├──────────────────────────────────────────────────────┤
│                                                        │
│  CATEGORY BADGE        Tool Name                      │
│                                                        │
│  Short description of what the tool does.             │
│                                                        │
│  ✅ Pros                                               │
│    · Pro point 1                                       │
│    · Pro point 2                                       │
│    · Pro point 3                                       │
│                                                        │
│  ⚠️ Cons                                               │
│    · Con point 1                                       │
│    · Con point 2                                       │
│                                                        │
│  [ Visit Tool → ]    (link if available)              │
│                                                        │
├──────────────────────────────────────────────────────┤
│  Powered by Trend Tracker + Claude                    │
└──────────────────────────────────────────────────────┘
```

HTML is built as a `String` in `EmailService` using `StringBuilder`. No Thymeleaf in MVP.

---

### 2.8 Claude Prompt Design

#### System prompt (defined in `PromptBuilder.buildSystemPrompt()`)

```
You are an AI tool researcher with access to real-time web search.
Your job is to find ONE trending AI tool being discussed right now
across social media platforms such as X/Twitter, Reddit, LinkedIn,
Product Hunt, and Hacker News.

You must respond with ONLY a valid JSON object — no markdown fences,
no preamble, no explanation. The JSON must match this exact schema:

{
  "name":        string,            // Tool name
  "category":    string,            // e.g. "Image Generation", "Coding", "Writing"
  "description": string,            // What it does — max 120 characters
  "pros": [string, string, string], // Exactly 3 pros
  "cons": [string, string],         // Exactly 2 cons
  "link":        string | null      // Official URL if available
}
```

#### User prompt (defined in `PromptBuilder.buildUserPrompt()`)

```
Today is {full date}. Search the web and find ONE AI tool that is
trending right now. Choose a tool you have not mentioned recently.
Return ONLY the JSON object.
```

**Note on "not mentioned recently":** Without memory or conversation history, Claude cannot truly honour this. This is a known MVP limitation. Deduplication is a Post-MVP concern.

---

### 2.9 Configuration

All configurable values live in `application.yml`. Secrets are injected via environment variables and referenced with `${ENV_VAR}` placeholders — never hardcoded.

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-6
          max-tokens: 500

  mail:
    host: smtp.gmail.com
    port: 465
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}       # Gmail App Password
    properties:
      mail.smtp.ssl.enable: true

app:
  email:
    from: ${MAIL_USERNAME}
    to: ${MAIL_TO}                   # Recipient address

scheduler:
  cron:
    run1: "0 30 2 * * *"
    run2: "0 30 5 * * *"
    run3: "0 30 8 * * *"
    run4: "0 30 11 * * *"
    run5: "0 30 14 * * *"
```

**Required environment variables:**

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `MAIL_USERNAME` | Gmail address used to send |
| `MAIL_PASSWORD` | Gmail App Password |
| `MAIL_TO` | Recipient email address |

---

### 2.10 Task List

Status key: `[ ]` Not started · `[~]` In progress · `[x]` Done

---

#### Task Group 1 — Project Setup

- [x] Create Maven project with `spring-boot-starter-parent` 3.3.5
- [x] Set Java 17 compiler source and target in `pom.xml`
- [x] Add dependencies: `spring-boot-starter`, `spring-ai-starter-model-anthropic` *(renamed from `spring-ai-anthropic-spring-boot-starter` in Spring AI 1.0.x — old name absent from BOM)*, `spring-boot-starter-mail`, `spring-boot-starter-test`
- [x] Create `application.yml` with all config placeholders — split into base + `application-anthropic.yml` / `application-openai.yml` provider profiles for provider decoupling
- [x] Create `.env.example` documenting all required environment variables (pre-filled sender/recipient; includes OpenAI key as optional)
- [x] Create `SignalFeedApplication.java` with `@SpringBootApplication` and `@EnableScheduling` *(renamed from `TrendTrackerApplication` to match project name; package `com.signalfeed`)*
- [x] Verify application starts with `./mvnw spring-boot:run` — clean boot confirmed
- [x] **Added (not in original plan):** Provider decoupling via Maven profiles (`-Pantropic` default, `-Popenai`); `app.ai.web-search-tool-name` property externalised per profile; Spring AI `ChatClient` used as sole abstraction in code
- [x] **Added (not in original plan):** `.gitignore` covering build output, secrets, IDE files, OS artifacts, logs
- [x] **Added (not in original plan):** Maven wrapper (`mvnw` / `mvnw.cmd`) generated for Maven 3.9.9
- [x] **Added (not in original plan):** JaCoCo plugin configured to enforce ≥ 80% line and branch coverage on `./mvnw verify`

---

#### Task Group 2 — Model

- [x] Create `AITool.java` as a Java `record` with fields: `name`, `category`, `description`, `pros` (List\<String\>), `cons` (List\<String\>), `link` — also annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` for robustness against unexpected fields from Claude
- [x] Annotate fields with `@JsonProperty` Jackson annotations for exact JSON key mapping
- [x] Create `AIToolsFetchException.java` as a runtime exception with a message and optional cause
- [x] **Tests:** `AIToolTest` (16 tests — constructor, accessors, equality/hashCode, toString, full Jackson deserialization, null link, unknown-field tolerance, missing link, empty lists, parametrized categories, round-trip); `AIToolsFetchExceptionTest` (5 tests — both constructors, is-RuntimeException, cause chain, throw-and-catch)

---

#### Task Group 3 — Configuration Beans

- [x] Create `AppConfig.java`
  - [x] Define `ChatClient` bean: injects `ChatClient.Builder` (Spring AI auto-configures the concrete implementation based on the active Maven/Spring profile — zero provider-specific code in `AppConfig`)
  - [x] Verify Spring AI auto-configuration picks up provider API key from `application-<profile>.yml` — confirmed
  - [x] `JavaMailSender` bean — confirmed Spring Boot 3.x auto-configures `JavaMailSenderImpl` from `spring.mail.*` properties; no manual bean definition needed
- [x] **Tests:** `AppConfigTest` (2 tests — builder result returned, `build()` called exactly once; uses Mockito mocks of `ChatClient.Builder` and `ChatClient`)

---

#### Task Group 4 — Prompt Builder

- [x] Create `PromptBuilder.java` (`@Component`)
  - [x] Implement `buildSystemPrompt()` — returns system prompt as a Java text block; includes JSON schema, field rules, and explicit "ONLY JSON" instruction
  - [x] Implement `buildUserPrompt(LocalDate)` — formats date as `"EEEE, MMMM d, yyyy"` with `Locale.ENGLISH`; `web-search-tool-name` remains in config, not the prompt
- [x] Unit test `PromptBuilder`
  - [x] Assert system prompt contains all 6 JSON schema fields
  - [x] Assert system prompt requires JSON-only response, prohibits markdown fences, specifies exactly 3 pros and 2 cons, mentions web search and social platforms
  - [x] Assert user prompt contains fully formatted date (day-of-week, month, day, year)
  - [x] Assert user prompt instructs web search and JSON-only return
  - [x] Parametrized tests: all 7 days of week, 5 different months, leap day (2024-02-29), divergence between two dates
  - [x] **Tests:** `PromptBuilderTest` (28 tests total)

---

#### Task Group 5 — AI Tools Service

- [x] Create `AIToolsService.java` (`@Service`)
  - [x] Inject `ChatClient`, `PromptBuilder`, and `ObjectMapper` via constructor
  - [x] Implement `fetchTrendingTool()`:
    - [x] Build system + user prompts via `PromptBuilder`
    - [x] Configure web-search tool name via `${app.ai.web-search-tool-name}` — **NOTE:** Spring AI 1.0.1 does not expose a native API for Anthropic's built-in server-side tools (`AnthropicApi.Tool` has no `type` field; string `web_search` is absent from the jar). Property is kept for observability and future upgrade; a `WARN` log is emitted on every run
    - [x] Call `ChatClient` fluent API: `.prompt().system().user().call().content()`
    - [x] Guard against null/blank response — throw `AIToolsFetchException("AI returned empty response")`
    - [x] Two-attempt JSON extraction: fast-path direct parse if response starts with `{`; regex fallback `\{[\s\S]*\}` (DOTALL, greedy) for preamble/markdown-fence cases
    - [x] Deserialise to `AITool` via `ObjectMapper`
    - [x] Field validation — collect all missing fields in one pass and throw `AIToolsFetchException` listing them all
    - [x] Throw `AIToolsFetchException` (wrapping cause) on ChatClient exception, parse failure, or missing required fields
- [x] Extensive logging: `INFO` on fetch start/complete; `WARN` on web-search tool limitation; `DEBUG` on prompt sizes, raw response, extracted JSON; `ERROR` with full context on every failure path
- [x] **Tests:** `AIToolsServiceTest` (28 tests)
  - [x] Stub `ChatClient` fluent chain via Mockito mocks of `ChatClient`, `ChatClientRequestSpec`, `CallResponseSpec`
  - [x] Valid JSON → correct `AITool` fields, null link allowed, unknown fields ignored
  - [x] JSON with preamble / trailing text / both / markdown fences → extraction still works (4 tests)
  - [x] Empty, blank, null response → `AIToolsFetchException` with "empty" message
  - [x] Response with no `{…}` → `AIToolsFetchException` with "No JSON object found"
  - [x] Malformed JSON (4 variants) → `AIToolsFetchException` (parametrized)
  - [x] Missing each required field individually + multiple fields at once → `AIToolsFetchException` listing missing fields
  - [x] `ChatClient` throws `RuntimeException` / `IllegalStateException` → wrapped `AIToolsFetchException`
  - [x] Interaction verification: system prompt passed, user prompt passed, `LocalDate.now()` used, chain called exactly once

---

#### Task Group 6 — Email Service

- [x] Create `EmailSendException.java` (RuntimeException, 2 constructors)
- [x] Create `EmailService.java` (`@Service`)
  - [x] Constructor injection: `JavaMailSender`, `@Value("${app.email.from}")`, `@Value("${app.email.to}")` — avoids `ReflectionTestUtils` in tests
  - [x] `send(AITool tool)` public method:
    - [x] Captures `LocalDateTime.now()` at method entry for consistent subject + header date
    - [x] Subject format: `🤖 AI Tool Spotlight — {name} [HH:mm]`
    - [x] HTML body built with `StringBuilder` per §2.7 layout — header banner, category badge, name, description, ✅ Pros, ⚠️ Cons, "Visit Tool →" button (only when link is non-null/non-blank), footer branding
    - [x] Inline CSS throughout for email-client compatibility (no Thymeleaf)
    - [x] `htmlEscape()` helper prevents XSS in AI-supplied text fields
    - [x] `MimeMessageHelper(message, false, "UTF-8")` — non-multipart HTML
    - [x] Throws `EmailSendException` wrapping cause on both `MailException` (transport) and `MessagingException` (MIME construction)
  - [x] Extensive logging: `INFO` on service init, send start, send success; `DEBUG` on subject, from/to, HTML size, MimeMessage assembly; `ERROR` with full cause on both failure paths
- [x] Package-private helpers (`buildSubject`, `buildHtml`) for direct unit-test access
- [x] **Tests:** `EmailSendExceptionTest` (8 tests) + `EmailServiceTest` (28 tests) — total 36
  - [x] `EmailSendExceptionTest`: message-only constructor, message+cause constructor, is-RuntimeException, throwable from both constructors
  - [x] `EmailServiceTest`: subject contains tool name / "AI Tool Spotlight" / robot emoji / `[HH:mm]` bracket; setFrom/setTo address assertions; `buildHtml` asserts for name, category, description, all pros, all cons, link present, no Visit button when link null, header date, header time, valid HTML document structure, footer branding, HTML escaping of `<`, `>`, `&`, `'`, `"` in tool fields; `buildSubject` helper assertions; `MailException` → `EmailSendException` (thrown + cause preserved); `MessagingException` → `EmailSendException` (thrown + cause preserved) via anonymous `MimeMessage` subclass override; `createMimeMessage` called once; `send` called once; no-link tool still sends
  - [x] `@MockitoSettings(strictness = Strictness.LENIENT)` to allow shared `@BeforeEach` stub for tests that exercise helpers directly without calling `send()`

---

#### Task Group 7 — Scheduler

- [ ] Create `AIToolsScheduler.java` (`@Component`)
  - [ ] Inject `AIToolsService` and `EmailService`
  - [ ] Define 5 methods each annotated `@Scheduled(cron = "${scheduler.cron.runN}")`
  - [ ] Each method: call `aiToolsService.fetchTrendingTool()`, pass result to `emailService.send()`
  - [ ] Wrap in try/catch (`AIToolsFetchException`, `EmailSendException`, `Exception`); log each failure with the run identifier; do not rethrow
  - [ ] Log run start and completion with timestamp for each method
- [ ] Unit test `AIToolsSchedulerTest`
  - [ ] Mock `AIToolsService` and `EmailService`
  - [ ] Call each scheduler method directly
  - [ ] Assert `emailService.send()` is called once per invocation when service succeeds
  - [ ] Assert `emailService.send()` is NOT called when `AIToolsService` throws

---

#### Task Group 8 — End-to-End Smoke Test

- [ ] With real API key and Gmail App Password configured, run `mvn spring-boot:run`
- [ ] Temporarily change one cron to fire in 1 minute: `0 {now+1} {currentHour} * * *`
- [ ] Confirm email arrives in inbox with correct content
- [ ] Restore cron expressions to production values
- [ ] Confirm all 5 scheduled times are logged at startup

---

#### Task Group 9 — Wrap-up

- [ ] Write `README.md`: prerequisites, environment variable setup, Gmail App Password steps, how to run
- [ ] Verify clean `mvn package` produces a runnable JAR
- [ ] Document any Spring AI version gotchas encountered (especially `web_search` tool config) in README

---

### 2.11 MVP Acceptance Criteria

| # | Criteria |
|---|---|
| 1 | Application starts without errors with all environment variables set |
| 2 | Application logs 5 upcoming scheduled run times at startup |
| 3 | Each scheduler tick produces exactly one Claude API call |
| 4 | A valid `AITool` with name, description, at least 1 pro, at least 1 con is returned from each run |
| 5 | An HTML email arrives in the inbox within 30 seconds of each scheduled tick |
| 6 | Email subject contains the tool name and the run time |
| 7 | A failed Claude API call is logged but does not crash the application or affect subsequent runs |
| 8 | A failed email send is logged but does not crash the application |
| 9 | No data is written to disk anywhere — process is fully stateless |

---

## 3. Post-MVP

This section is completed **after running the MVP for at least 1–2 weeks**. Its purpose is to translate real observed behaviour into prioritised next steps — not to plan features in advance.

---

### 3.1 What to Observe During MVP

Before making any Post-MVP decisions, collect the following signals from the live MVP:

| Observation | How to measure |
|---|---|
| **Duplicate tools** | Manually note how often the same tool appears across the 5 daily emails or across days |
| **Tool quality** | Are the tools genuinely trending, or are they stale/well-known ones? |
| **Email engagement** | Which emails do you actually read vs. delete? Does 5/day feel like too many or too few? |
| **Run reliability** | Check logs: how often does a run fail? What is the failure mode (API timeout, parse error, mail error)? |
| **Parse failures** | How often does Claude return a response that fails the JSON extraction regex? |
| **Email readability** | Is the plain-StringBuilder HTML good enough, or does it render poorly in some clients? |
| **Claude cost** | Check Anthropic usage dashboard — 5 calls/day × 30 days = 150 calls/month. Is cost acceptable? |
| **Prompt quality** | Are pros/cons actually insightful, or generic? Does `max-tokens: 500` ever truncate the response? |

Log all anomalies. After 2 weeks, revisit this section.

---

### 3.2 Expected Learnings

These are the issues most likely to surface during the MVP run. Review each one honestly after the observation period.

#### L1 — Duplicate tools will appear
**Why:** Claude has no memory across runs. The prompt says "choose a tool you have not mentioned recently" but without context, this is unenforceable.
**Impact:** High — the whole value proposition is discovering new tools, not seeing the same ones repeatedly.

#### L2 — 5 emails/day may feel too many
**Why:** In practice, 5 interruptions a day for a personal tool may become noise and get ignored.
**Impact:** Medium — if you're deleting them without reading, the delivery frequency needs to change.

#### L3 — JSON parse failures will happen occasionally
**Why:** Claude sometimes adds preamble ("Here is the JSON:") or wraps in markdown fences despite explicit instruction. The MVP regex handles this partially, but edge cases exist.
**Impact:** Low-medium — runs fail silently, you miss an email, but the app stays up.

#### L4 — Email HTML may render inconsistently
**Why:** StringBuilder HTML without tested inline styles may look different across Gmail, Outlook, Apple Mail.
**Impact:** Low — cosmetic, but degrades the experience.

#### L5 — No visibility into run history
**Why:** MVP has no UI and no persistence. If you want to know what tools were found last Tuesday, there is no way to retrieve that.
**Impact:** Medium — as tools accumulate, not being able to reference past results becomes limiting.

#### L6 — Prompt may produce generic pros/cons
**Why:** Asking for exactly 3 pros and 2 cons in a JSON schema can produce formulaic, low-quality content.
**Impact:** Medium — content quality is the core value, so this matters.

---

### 3.3 Recommended Next Steps

Prioritise based on what L1–L6 above actually showed. This is a decision framework, not a committed roadmap.

---

#### Priority 1 — Deduplication (addresses L1)

Before adding any UI or persistence, solve the duplicate problem. Two approaches:

- **In-memory set (low effort):** Keep a `Set<String>` of tool names seen in the current JVM session. Append recent names to the prompt: *"Do not suggest any of these tools: {list}."* Resets on restart, which is acceptable for a personal app.
- **File-based seen list (medium effort):** Persist a `seen_tools.txt` file (one name per line) on disk. Append after each successful run. Include the list in the prompt. Survives restarts. No database needed.

The file-based approach is the recommended starting point — it solves the problem with minimal infrastructure.

#### Priority 2 — Tune delivery frequency (addresses L2)

If 5/day is too many after the MVP period, change to:
- 3/day (morning, afternoon, evening) — most likely outcome
- 1/day digest (reverting to Option B from initial planning) — if individual emails are ignored

This is a cron change plus potentially restructuring `AIToolsScheduler`.

#### Priority 3 — Improve parse robustness (addresses L3)

Add a fallback strategy to `AIToolsService`:
- Attempt 1: regex `\{[\s\S]*\}` (current)
- Attempt 2: strip markdown fences, retry Jackson parse
- Attempt 3: if still failing, re-call Claude with a correction prompt: *"Your previous response was not valid JSON. Return ONLY the JSON object."*
- If all three fail: log and skip, do not throw

This makes each run more resilient without adding retry infrastructure.

#### Priority 4 — Persistence and run history (addresses L5)

Once the above are stable, add SQLite via Spring JDBC (no JPA):
- Two tables: `tool_seen` (name, first_seen_at) and `run_log` (run_time, tool_name, status)
- `tool_seen` replaces the file-based seen list from Priority 1
- `run_log` gives you a queryable history

This is the natural on-ramp to the full architecture described in the original implementation plan.

#### Priority 5 — Web dashboard (addresses L5 + L4)

After persistence exists, add Thymeleaf:
- Single read-only page at `/runs` listing the last 30 runs with tool name, category, and status
- Single read-only page at `/runs/{date}` showing tool details for that run
- No edit capability in v1 of the UI

This is the entry point back to the full architecture defined in the original plan.

#### Priority 6 — Prompt quality iteration (addresses L6)

Experiment with prompt changes before adding more infrastructure:
- Remove the fixed "exactly 3 pros, 2 cons" constraint — let Claude decide
- Add: *"Pros and cons must be specific to this tool, not generic AI tool observations"*
- Increase `max-tokens` to 800 and observe whether response quality improves
- Consider adding a `prompt_override` field in config so you can A/B different prompts without redeploying

#### Priority 7 — Feed extensibility (original architecture goal)

Once the above are addressed, the MVP codebase is ready to evolve into the full pluggable `FeedProvider` architecture described in the original `IMPLEMENTATION_PLAN.md`. At that point:
- Introduce `FeedProvider` interface
- Refactor `AIToolsService` into `AIToolsFeedProvider`
- Add `RemoteJobsFeedProvider` as the first extension

This is the final milestone that brings the MVP all the way to the extensible platform originally designed.

---

*Last updated: 2026-06-18. Task Groups 1–6 complete (115 tests, ≥ 80% line + branch coverage). Task Groups 7–9 pending.*
