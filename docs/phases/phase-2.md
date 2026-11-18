# Phase 2: The agent loop, written by hand

**Lesson:** an agent is a loop. The model asks for a tool, your code runs it and sends the result back, and this repeats until the model answers or a step cap stops it.

## Read in this order

1. `agents/ManualAgentLoop.java`: the whole agent (`// LESSON:` comments: auto-execution off, "no tool calls" means done, our code runs the tools, the cap).
2. `tools/ToolRegistry.java`: where a model's request meets our code; a failing tool becomes an error message, not a crash.
3. `tools/FinancialsLookup.java` and `tools/Calculator.java`: the two tools, as a name, a description and an input schema.
4. `agents/LoopTrace.java` and `agents/LoopResult.java`: what the caller gets back (`ANSWERED` or `STOPPED_AT_LIMIT`).

## Flow

```
question -> [system prompt + question] -> model
                ^                           |
                |  tool results             | reply has tool calls?
                +---- our code runs tools <-+  yes -> run each, append results, loop
                                               no  -> ANSWERED
                                  step > maxSteps -> STOPPED_AT_LIMIT
```

## Run the checks

```bash
./mvnw test        # ManualAgentLoopTest: 3 tests with a fake model
./mvnw spring-boot:run
curl -s localhost:8080/loop/ask -H 'Content-Type: application/json' \
  -d '{"question":"What was HDFCBANK gross NPA growth from FY23 to FY24?"}'
```

## Results from the build session

- `./mvnw test`: 6 tests pass (3 new). They cover: lookup, lookup, calculation, answer; a missing figure returned as an error the model can read; a model that always asks for a tool hits the cap and returns `STOPPED_AT_LIMIT`.
- Live call to `gemini-3.5-flash-lite`: `ANSWERED` in 3 steps. Step 1: two `getFinancial` calls (FY23 = 2.0, FY24 = 3.0). Step 2: `percentChange(2, 3)` = 50.0. Step 3: final answer. Each model turn took about 7.5 to 8.7 s; each tool call took 0 to 3 ms.

## Notes

- `data/financials.csv` holds **SAMPLE placeholder values, not real figures** (marked in the file and in a `source` column). Replace them with hand-checked numbers before Phase 6.
- Tools are plain functions with minimal descriptions on purpose. Phase 3 owns good descriptions, `@Tool` and structured errors.
- The `Prompt` object holds a reference to the loop's live message list. A test that inspects it after the run sees later messages too; snapshot what you need at call time.
- "Break it" (`maxSteps` = 1, and removing the system prompt) is not run yet.

## Surprise

The model asked for both FY23 and FY24 lookups in a single turn (parallel tool calls), so the loop handles a list of calls per step, not just one.
