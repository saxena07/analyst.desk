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
