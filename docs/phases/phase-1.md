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
- "Break it" (retry off, 10 tricky questions, then a JSON-schema response setting) is not run yet.

## Surprise

Spring AI's `.entity(type)` already appends the JSON schema to the prompt, so "ask for a schema" is partly done for you. Validation is still needed: well-formed JSON can be empty or semantically wrong.
