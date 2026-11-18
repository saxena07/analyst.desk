# Analyst Desk: Project Overview

Analyst Desk is one Spring Boot application that answers equity research questions from public company filings and returns a cited research note. A coordinator agent splits each question across specialist agents, a critic checks every claim against its source, and a person approves the plan and any sensitive step.

It is a learning project. Every feature exists to teach one topic: RAG, tool calling, MCP servers and clients, multi-agent systems, coordinator design, human-in-the-loop, memory and context management, guardrails, evals and reliability. `docs/BUILD_GUIDE.md` says how and in what order to build it.

## What it does

The user asks one research question and gets back a structured, cited note after approving a plan.

Example question: "Compare HDFC Bank and ICICI Bank on asset quality over the last 3 years and flag red flags in management commentary."

1. **Plan.** The Research Director splits the question into sub-tasks (gross and net NPA trend, provision coverage, slippage ratio, management tone) and shows the plan with a cost estimate. The user approves or edits it.
2. **Ambiguity check.** If something is unclear (consolidated or standalone figures, which fiscal years), the app asks the user instead of guessing.
3. **Research.** The Filing Analyst searches annual reports through RAG. The Quant Analyst computes ratios and growth rates with calculator tools. The Transcript Analyst looks for guidance changes and red-flag language in earnings call transcripts.
4. **Challenge.** The Bear-Case Agent argues against the emerging thesis. The Fact-Checker rejects any claim that lacks a supporting citation or tool result, and sends it back for revision.
5. **Write-up.** The Writer returns a `ResearchNote`: summary, findings with page-level citations, red flags, a bear case, open questions and confidence.
6. **Escalation.** If a claim is still rejected after the revision rounds, or confidence stays low, the run pauses and hands the user the evidence.
7. **Notebook.** With the user's confirmation, the note is saved to a long-term research notebook that later questions can draw on.

A question like "Should I buy HDFC Bank?" is refused with an explanation. This is a research tool, not an investment advice engine.

## Agents

| Agent | Job | Tools and data |
| --- | --- | --- |
| Research Director (coordinator) | Plans, dispatches specialists, merges results, decides when the run is done or the budget is spent | Blackboard, budget guard |
| Filing Analyst | Answers from annual reports with page-level citations | RAG search over filings |
| Quant Analyst | Every number, trend and ratio | Financial lookup and calculator tools |
| Transcript Analyst | Tone shifts, guidance changes, red-flag language | RAG search over transcripts |
| Bear-Case Agent | Argues against the thesis | RAG search |
| Fact-Checker | Verifies each claim against its cited source | Read access to evidence |
| Writer | Produces the final `ResearchNote` | No tools |

The design is hub-and-spoke. Specialists never call each other. A specialist that needs something outside its scope returns a typed `NEEDS` result, and the Director routes it.

## Architecture

- **One Spring Boot 3.x service** (Java 21, Maven) built as a modular monolith. Packages are listed in `docs/BUILD_GUIDE.md`.
- **Gemini API** as the chat model through Spring AI. The key comes from the `GEMINI_API_KEY` environment variable.
- **Ollama** in Docker for embeddings and as a fallback chat model.
- **Postgres with pgvector** in Docker for document chunks, full-text search, run state, the blackboard, the notebook, the audit log and eval results.
- **An MCP server inside the app** at `/mcp`. The app's own agents consume it through the Spring AI MCP client, and external clients such as MCP Inspector or Claude Desktop can connect too.

## Data

- One sector to start (banks), 3 companies, expanding to 5.
- Annual reports, earnings call transcripts and a few news items as PDFs in `data/docs/`.
- A small, hand-checked `data/financials.csv` (company, metric, fiscal year, value) for the numeric tools.
- Public documents only.

## Rules that always hold

- Numbers come from tools or cited documents, never from the model's own arithmetic.
- Every claim in a note carries a citation.
- No investment advice.
- Write actions (saving to the notebook) need human confirmation, enforced in code.
- Hard rules are enforced in code, not only in prompts.

## Out of scope

Kafka, Temporal, Redis, Kubernetes, GraalVM, A2A and a front end are not part of the base build. Each can be added once the single-service version works.
