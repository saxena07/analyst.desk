# Analyst Desk

A learning project: a multi-agent equity research assistant built in Java 21 and Spring Boot with Spring AI, Gemini, Ollama and Postgres with pgvector.

## Read first

1. `docs/PROJECT.md`: what the app does, its agents, architecture, data and the rules that always hold.
2. `docs/BUILD_GUIDE.md`: how to build it, one lesson per phase. Read "Rules for Claude" and "Project baseline" before any work.

Work only on the phase I name, and wait for my "go" before writing code.

## Conventions

- Base package: `dev.lakshya.analystdesk`
- Mark the core of each phase's lesson with `// LESSON:` comments.
- API keys come from environment variables only (`GEMINI_API_KEY`). Never commit them.
- End every phase with a runnable check and a `docs/phases/phase-N.md` note.

## Commands

Requires Java 21+ (the build targets 21) and Docker.

```bash
docker compose up -d                 # Postgres (pgvector) on 5433, Ollama on 11434
./mvnw test                          # unit tests (no API key or Docker needed)
cp .env.example .env                 # then put your GEMINI_API_KEY in .env (gitignored)
                                     # (or export GEMINI_API_KEY=...; never commit it)
./mvnw spring-boot:run               # app on http://localhost:8080
curl -s localhost:8080/ask -H 'Content-Type: application/json' \
  -d '{"question":"What does gross NPA mean for a bank?"}'
```

## Package layout

`api/` REST + SSE, `llm/` model config and versioned prompts, `tools/`, `mcp/`, `rag/`, `agents/`, `coordinator/`, `hitl/`, `memory/`, `safety/`, `evals/`. All under `dev.lakshya.analystdesk`. Phases fill these in; see `docs/BUILD_GUIDE.md`.

## Rules (from the build guide)

- One phase per session; no features from later phases.
- Numbers come from tools or cited documents, never the model's arithmetic.
- Every claim carries a citation; no investment advice.
- Write actions need human confirmation, enforced in code.
- Hard rules live in code, not only in prompts.
