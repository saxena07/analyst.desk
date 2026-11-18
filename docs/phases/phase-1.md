# Phase 1: First model call and structured output

**Lesson:** a model returns text; the app needs typed data. Define a schema, ask for it, validate what comes back, and on failure retry with the error fed back to the model.

## Read in this order

1. `api/ResearchNote.java`: the schema and its validation rules (the contract with the model).
2. `llm/StructuredCaller.java`: the call, validate, retry loop (`// LESSON:` comments).
3. `llm/prompts/research-note.v1.st`: the prompt as a versioned file.
4. `api/AskController.java`: `POST /ask`; a failed run becomes a 502, not a silent 200.

## Flow

```
POST /ask -> prompt file + question -> ChatClient (Gemini) -> .entity(ResearchNote)
                  ^                                              |
                  |  "Your last answer was invalid: <why>"       v
                  +------------- retry <------ parse + bean validation
                                                  | ok -> 200 ResearchNote
                                                  | out of attempts -> StructuredOutputFailed -> 502
```

## Run the checks

```bash
docker compose up -d      # Postgres on 5433 (5432 was taken locally), Ollama on 11434
./mvnw test               # 3 tests, fake model, no key or Docker needed
cp .env.example .env      # put GEMINI_API_KEY in .env
./mvnw spring-boot:run
curl -s localhost:8080/ask -H 'Content-Type: application/json' \
  -d '{"question":"What does gross NPA mean for a bank?"}'
```

## Results from the build session

- `./mvnw test`: 3 tests, 0 failures (broken JSON then valid; valid JSON failing validation; failing after the last attempt).
- `POST /ask` against `gemini-3.5-flash-lite`: HTTP 200 in about 11.5 s, valid `ResearchNote`, 3 findings, confidence HIGH. Passed on the first attempt, so the retry was exercised only by the unit tests.
- `docker compose up -d`: Postgres healthy with pgvector 0.8.7 available; Ollama answers "Ollama is running".

## Notes and deviations

- The Initializr skeleton used Boot 4.1.2 and Java 25. Moved to Boot 3.5.16, Java 21, Spring AI 1.1.8 to match the guide.
- The key can live in a gitignored `.env`, imported by `spring.config.import`. A real env var still wins.
- Postgres is mapped to host port 5433 because a local Postgres already uses 5432.
- Only parse and validation failures are retried. Transport errors (bad key, 429, network) are not; Phase 16 owns that.
- "Break it" results are below.

## Break it: results

10 tricky questions (ambiguous, off-topic, advice-seeking, "ignore the JSON format", a bare "?", a French question, a very long answer) against `gemini-3.5-flash-lite`. Run with `./mvnw test -Dtest=StructuredOutputExperiment#compareVariants -Dexperiment=true` (calls the live API; skipped in a normal test run).

| Variant | Attempts | OK | Parse failures | Validation failures |
| --- | --- | --- | --- | --- |
| A: retry off, schema only described in the prompt | 1 | 8 | 2 | 0 |
| B: retry off, schema enforced by the API (JSON mode + `responseSchema`) | 1 | 10 | 0 | 0 |
| C: retry on, schema only in the prompt (the app today) | up to 3 | 10 | 0 | 0 |

- A failed on Q6 ("ignore the JSON format") and Q9 ("?"): broken or truncated JSON.
- C recovered every question, but several needed a second attempt, so retry costs extra calls and latency (the C run took about 4 minutes against about 3 for A and B together).
- B produced valid JSON every time, so API-enforced schemas remove most parse failures at the source. Validation rules are still worth keeping: a schema cannot express everything.
- Caveats: 10 questions, one run, model output varies between runs. The raw replies were not saved, so the individual failures are known only from the logs.
- Not changed: the app still uses variant C. Moving it to an enforced schema is a design decision for later.

## Surprise

Spring AI's `.entity(type)` already appends the JSON schema to the prompt, so "ask for a schema" is partly done for you. Validation is still needed: well-formed JSON can be empty or semantically wrong.
