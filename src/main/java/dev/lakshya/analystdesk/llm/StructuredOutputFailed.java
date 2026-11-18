package dev.lakshya.analystdesk.llm;

/** Thrown when the model still returns invalid output after every attempt. */
public class StructuredOutputFailed extends RuntimeException {

    private final Class<?> type;
    private final int attempts;

    public StructuredOutputFailed(Class<?> type, int attempts, String lastError) {
        super("Model failed to produce a valid " + type.getSimpleName()
                + " after " + attempts + " attempts. Last error: " + lastError);
        this.type = type;
        this.attempts = attempts;
    }

    public Class<?> type() {
        return type;
    }

    public int attempts() {
        return attempts;
    }
}
