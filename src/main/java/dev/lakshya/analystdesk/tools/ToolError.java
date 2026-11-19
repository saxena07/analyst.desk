package dev.lakshya.analystdesk.tools;

// LESSON: an error is a message to the model. A stack trace or an empty string gives it nothing to act on.
// "code" says what kind of failure, "message" says what happened, "hint" says what to try next.
public record ToolError(String code, String message, String hint) {
}
