# Phase 3: Tool design

**Lesson:** the model chooses and uses a tool based only on its name, description and input schema, so a tool's interface is a prompt, and its errors are messages to the model.

## Read in this order

1. `tools/FinancialTools.java`: three `@Tool` methods; read the descriptions as prompts (when to use, what it returns, what it does NOT do) and the `NOT_FOUND` error with its hint.
2. `tools/ToolResult.java` and `tools/ToolError.java`: every tool returns `ok + data` or `ok=false + {code, message, hint}`.
3. `tools/NotebookTools.java`: the write tool, idempotent by a client-supplied `noteId`.
4. `tools/ToolRegistry.java`: read-only vs write split, enforced in code (a write tool is refused with `NEEDS_APPROVAL` until Phase 12).
5. `tools/ToolChoiceTest.java`: measures which tool the model picks first.

## Tools

| Tool | Kind | Use it for |
| --- | --- | --- |
| `getFinancials(company, metric, fiscalYear)` | read | one number for one bank and year |
| `compareMetric(companies, metric, fiscalYear)` | read | several banks, same metric and year |
| `computeCagr(company, metric, fromYear, toYear)` | read | growth over time (looks up both values itself) |
| `saveNote(noteId, title, text)` | **write** | saving a note; not offered to the loop by default |

## Run the checks

```bash
./mvnw test                                        # 21 tests, no key needed
./mvnw test -Dtest=ToolChoiceTest -Dlive=true      # live: ~15 questions, reports tool-choice hit rate
./mvnw spring-boot:run                             # then POST /loop/ask as in phase-2.md
```

## Results from the build session

- `./mvnw test`: 21 tests pass (15 new in `ToolsTest`): error codes and hints, CAGR maths, idempotent `saveNote`, `ID_CONFLICT`, write tools absent from the default list and refused by `execute`, bad arguments returned as `INVALID_INPUT`.
- Tool-choice test (`gemini-3.5-flash-lite`, 15 questions including 2 `saveNote` and 2 "no tool" cases): **15/15 = 100%**.
- Live loop, unknown company ("AXIS"): `getFinancials` returned `UNKNOWN_COMPANY` with the known tickers, the model retried with `AXISBANK`, got the same error, and answered honestly that the data is not available. Error, read, retry, stop, all visible in the trace.
- Live loop, missing year (FY19): the model read the `NOT_FOUND` hint and reported that data starts at FY22 instead of substituting another year, which is the right behaviour here. With a clear description it often avoids the error altogether (a malformed year like "FY2023" was normalised before the call).

## Notes and decisions

- `saveNote` writes to an in-memory `NoteStore` (lost on restart). Phase 12 adds human confirmation; Phase 13 replaces the store with the pgvector notebook.
- The loop is offered read-only tools only, and `ToolRegistry.execute` refuses write tools in code. The tool-choice test shows the model every tool but never executes any.
- Replaced the Phase 2 tools (`FinancialsLookup`, `Calculator`). The system prompt is now `agent-loop.v2.st` (it tells the model to read error hints and retry); v1 is kept for history. `data/financials.csv` gained `PCR_PCT` and `SLIPPAGE_PCT` rows, still SAMPLE values.
- "Break it" (near-identical descriptions, then a tool that throws) is not run yet.

## Surprise

A good description removes errors before they happen: the model normalised "fiscal year 2024" to `FY24` on its own. A tool's errors matter most for inputs the model cannot guess, like which ticker we use.
