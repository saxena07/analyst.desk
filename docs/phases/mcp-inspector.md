# Connecting to the Analyst Desk MCP server

The server lives inside the Spring Boot app at `http://localhost:8080/mcp` (Streamable HTTP). It has no authentication yet (Phase 16), so it is only safe on localhost.

Start the app first:

```bash
docker compose up -d          # not needed for Phase 4, but harmless
./mvnw spring-boot:run        # needs GEMINI_API_KEY in .env, because the app also hosts the Gemini client
```

## MCP Inspector (needs Node)

```bash
npx @modelcontextprotocol/inspector
```

It prints a local URL (with a session token) and opens it. In the UI:

1. **Transport Type:** `Streamable HTTP`
2. **URL:** `http://localhost:8080/mcp`
3. Press **Connect**.
4. **Tools** tab, **List Tools**: you should see `getFinancials`, `compareMetric`, `computeCagr` (and no `saveNote`).
   Run `getFinancials` with `company=HDFCBANK`, `metric=GNPA_PCT`, `fiscalYear=FY24`; the result is `ok: true`, value 3.0 (SAMPLE data).
5. **Resources** tab, **List Resources**: five `filing://...` URIs. Click one to read it.
6. **Prompts** tab, **List Prompts**: `bank-asset-quality-review`. Fill `company` and `years` to see the generated message.

Inspector also has a CLI mode: `npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp --transport http --method tools/list`.

## Claude Code

```bash
claude mcp add --transport http analyst-desk http://localhost:8080/mcp
```

Then ask it to look up a bank's gross NPA; it should call `getFinancials`.

## Claude Desktop

Desktop's config file launches local stdio servers, so use the `mcp-remote` bridge to reach an HTTP server
(edit `~/Library/Application Support/Claude/claude_desktop_config.json`, then restart Desktop):

```json
{
  "mcpServers": {
    "analyst-desk": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```

The prompt appears in the attach menu, the resources can be attached to a chat, and the tools are called by the model.

## No client handy? Plain curl

```bash
H=(-H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
curl -si localhost:8080/mcp "${H[@]}" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
# copy the Mcp-Session-Id response header, then send it with every later request:
curl -s localhost:8080/mcp "${H[@]}" -H 'Mcp-Session-Id: <id>' -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```
