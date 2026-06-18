# Signal Feed

A personal Spring Boot application that automatically discovers the latest trending AI tools and delivers them to your inbox throughout the day.

Each run makes one Claude API call (with web search enabled) to find a single trending AI tool, then immediately sends an HTML email with the tool's name, category, description, pros, and cons.

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| Gmail account | With 2-Step Verification enabled |
| Anthropic account | API key with Claude access |

---

## Environment Variables

Create a `.env` file (or export these in your shell) before running the application. See `.env.example` for a template.

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Your Anthropic API key — get it from [console.anthropic.com](https://console.anthropic.com) |
| `MAIL_USERNAME` | Gmail address used to send emails |
| `MAIL_PASSWORD` | Gmail App Password (see below) |
| `MAIL_TO` | Recipient email address |

### Setting Up a Gmail App Password

The application uses Gmail SMTP with SSL. Gmail requires an **App Password** (not your Google account password).

1. Go to your [Google Account](https://myaccount.google.com)
2. Navigate to **Security → How you sign in to Google**
3. Ensure **2-Step Verification** is enabled (required)
4. Go to **Security → App Passwords** (search for "App Passwords" in the search bar if not visible)
5. Select **Mail** as the app, then **Other (Custom name)** as the device; enter `Signal Feed`
6. Copy the generated 16-character password — this is your `MAIL_PASSWORD`

---

## Switching AI Providers

The app is decoupled from any specific AI provider via Spring AI's `ChatClient` abstraction. The provider is selected at build time using a **Maven profile** (`-P`), which pulls in the right starter dependency and activates the matching Spring config file.

| Maven profile | Provider | Default? |
|---|---|---|
| `anthropic` | Claude (Anthropic) | Yes |
| `openai` | GPT-4o (OpenAI) | No |

```bash
./mvnw spring-boot:run                  # Anthropic (default)
./mvnw spring-boot:run -Popenai         # OpenAI
./mvnw package -Popenai                 # Build JAR for OpenAI
```

The provider-specific config (API key reference, model name, web-search tool name) lives in `application-<profile>.yml`. To add a new provider, add a Maven profile in `pom.xml` and a matching `application-<name>.yml`.

---

## Running Locally

### 1. Set up environment variables

```bash
cp .env.example .env
# Edit .env — only set the key for the provider you plan to use
```

### 2. Export environment variables

```bash
# Anthropic (default)
export ANTHROPIC_API_KEY=your-key-here

# Or for OpenAI
export OPENAI_API_KEY=your-key-here

export MAIL_USERNAME=you@gmail.com
export MAIL_PASSWORD=your-app-password
export MAIL_TO=recipient@example.com
```

Or source a `.env` file directly:

```bash
export $(grep -v '^#' .env | xargs)
```

### 3. Run the application

```bash
./mvnw spring-boot:run              # Anthropic
./mvnw spring-boot:run -Popenai    # OpenAI
```

The application starts and schedules 5 daily runs (IST times):

| Run | Time |
|---|---|
| Morning | 8:00 AM |
| Mid-morning | 11:00 AM |
| Afternoon | 2:00 PM |
| Evening | 5:00 PM |
| Night | 8:00 PM |

### Quick test — trigger a run in 1 minute

To verify the full end-to-end flow without waiting for a scheduled time, temporarily override a cron expression:

```bash
# Get current time (e.g., 14:35) — set cron to fire at 14:36
export SCHEDULER_CRON_RUN1="0 36 14 * * *"
./mvnw spring-boot:run
```

Restore the original value in `application.yml` after the test.

---

## Running Tests

```bash
./mvnw test
```

To run tests and generate a coverage report:

```bash
./mvnw verify
```

The JaCoCo HTML report is generated at `target/site/jacoco/index.html`.
Coverage gates: **≥ 80% line coverage** and **≥ 80% branch coverage** are enforced on `./mvnw verify`.

---

## Building a Runnable JAR

```bash
./mvnw package -DskipTests
java -jar target/signal-feed-0.0.1-SNAPSHOT.jar
```

---

## Configuration Reference

All configuration lives in `src/main/resources/application.yml`. Secrets are injected via environment variables.

| Key | Default | Description |
|---|---|---|
| `spring.ai.anthropic.chat.options.model` | `claude-sonnet-4-6` | Claude model used for tool discovery |
| `spring.ai.anthropic.chat.options.max-tokens` | `500` | Max tokens per Claude response |
| `spring.mail.host` | `smtp.gmail.com` | SMTP host |
| `spring.mail.port` | `465` | SMTP port (SSL) |
| `scheduler.cron.run1–5` | See above | Cron expressions for the 5 daily runs |

---

## Known Limitations (MVP)

- No deduplication — the same tool may appear across multiple emails
- No retry logic — a failed run is logged and skipped
- No persistence — fully stateless, no data is written to disk
- Claude cannot truly honour "do not repeat recent tools" without conversation history

These are intentional MVP trade-offs. See the implementation plan for the post-MVP roadmap.
