# Learning log

Answer each phase's "Check your understanding" questions here, in your own words.

## Phase 1: Structured output

- Why is "ask for JSON in the prompt" weaker than a schema enforced by the API?
- What should happen after the last retry fails, and who should know?
- Why keep prompts in versioned files instead of Java strings?

## Phase 2: The agent loop

- Who actually executes the tool: the model or your code? Why does that matter for security?
- What signals that the model is finished?
- What should a stopped-at-limit result tell the caller?

## Phase 3: Tool design

- When should two tools be merged into one, and when split?
- Why must write tools be idempotent in an agent system?
- What belongs in a tool description that would never go in a Java method comment?

## Phase 4: MCP server

- When would you use stdio instead of Streamable HTTP?
- Why is a filing better as a resource than as a tool that returns the whole document?
- What does the MCP server know about the model that calls it? (Hint: nothing.)
