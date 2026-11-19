# Phase 4: Building an MCP server

**Lesson:** MCP turns your tools into a standard service that any MCP client can discover and call. 
It offers three kinds of capability, chosen by who decides to use them: tools (the model), resources (the application or user), prompts (the user).

## Read in this order

1. `mcp/McpServerConfig.java`: the Phase 3 `@Tool` beans published over MCP, with the primitive table in a `// LESSON:` comment. `saveNote` is deliberately **not** published.
2. `mcp/FilingResources.java`: every file in `data/docs/{TICKER}/{FY}/{docType}.txt` becomes a resource `filing://{TICKER}/{FY}/{docType}`.
3. `mcp/ResearchPrompts.java`: the "bank asset quality review" prompt with `company` and `years` arguments.
4. `mcp/McpServerTest.java`: a real MCP client talking to the app over HTTP.
5. `docs/phases/mcp-inspector.md`: connect Inspector, Claude Code and Claude Desktop.

| Primitive | Who decides to use it   | Example here                                    |
|-----------|-------------------------|-------------------------------------------------|
| Tool      | The model               | `getFinancials`, `compareMetric`, `computeCagr` |
| Resource  | The application or user | `filing://HDFCBANK/FY24/annual-report`          |
| Prompt    | The user                | `bank-asset-quality-review`                     |

## Run the checks

```bash
./mvnw test                  # 28 tests; McpServerTest boots the app on a random port, no Gemini call
./mvnw spring-boot:run       # then follow docs/phases/mcp-inspector.md
```

## Results from the build session

- `./mvnw test`: 28 pass, 1 skipped (the live `ToolChoiceTest`). The 6 new `McpServerTest` cases list the three read-only tools (and not `saveNote`), call a tool, list and read resources, refuse a traversing URI, and list and fill the prompt.
- Real running app, plain `curl` over Streamable HTTP: `initialize` returned a session id; `tools/list` returned the 3 tools; `resources/list` the 5 filings; `prompts/list` the prompt with its 2 arguments; `tools/call getFinancials` for ICICIBANK FY24 returned `ok: true`, value 2.0.
- Not run by me: MCP Inspector's UI and Claude Desktop (they need a human at the screen); the steps are in `mcp-inspector.md`.

## Notes and decisions

- The starter is `spring-ai-starter-mcp-server-webmvc` with `spring.ai.mcp.server.protocol=STREAMABLE`; the endpoint is `/mcp`.
- Only read-only tools are published. Anyone who can reach `/mcp` could call a write tool with no approval, so `saveNote` waits for Phase 12 (confirmation) and Phase 16 (auth on the endpoint).
- The resource handler reads only the files it registered at startup; it never builds a path from the request, so `../../.env` cannot be requested.
- `data/docs/*.txt` are SAMPLE placeholders, not real filings. Real PDFs arrive for Phase 6; PDFs are skipped as resources for now.
- Startup logs show Spring `BeanPostProcessorChecker` warnings about the MCP annotation scanner. They are harmless framework noise.
- "Break it" (a tool description that contains an instruction) is not run yet.

## Surprise

The server knows nothing about the model on the other end. It only sees JSON-RPC requests, so the same code serves Inspector, Claude Code and, in Phase 5, our own agent.
