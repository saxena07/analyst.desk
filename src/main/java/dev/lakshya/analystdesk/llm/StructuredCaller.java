package dev.lakshya.analystdesk.llm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StructuredCaller {

    private static final Logger log = LoggerFactory.getLogger(StructuredCaller.class);

    private final ChatClient chatClient;
    private final Validator validator;

    public StructuredCaller(ChatClient chatClient, Validator validator) {
        this.chatClient = chatClient;
        this.validator = validator;
    }

    // LESSON: the model can return malformed or incomplete output.
    // We validate, and on failure we tell the model exactly what was wrong.
    public <T> T callTyped(String prompt, Class<T> type, int maxAttempts) {
        String feedback = "";
        String lastError = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // .entity(type) appends the JSON schema of `type` to the prompt and
                // parses the reply. Parsing failure surfaces as a RuntimeException.
                T result = chatClient.prompt()
                        .user(prompt + feedback)
                        .call()
                        .entity(type);
                if (result == null) {
                    throw new IllegalStateException("The reply was empty.");
                }
                validate(result);
                return result;
            } catch (RuntimeException e) {
                // Only parse and validation problems are worth retrying with feedback.
                // Transport errors (bad key, 429, network) are a different problem (Phase 16).
                if (!isRetryable(e)) {
                    throw e;
                }
                lastError = describe(e);
                log.warn("Attempt {}/{} for {} was invalid: {}", attempt, maxAttempts,
                        type.getSimpleName(), lastError);
                feedback = "\n\nYour last answer was invalid: " + lastError
                        + "\nReturn corrected JSON only.";
            }
        }
        // LESSON: after the last retry we fail loudly. The caller decides what to tell the user.
        throw new StructuredOutputFailed(type, maxAttempts, lastError);
    }

    private <T> void validate(T result) {
        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private boolean isRetryable(RuntimeException e) {
        return e instanceof ConstraintViolationException
                || e instanceof IllegalStateException
                || hasCause(e, com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    private String describe(RuntimeException e) {
        if (e instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
        }
        return "the reply was not valid JSON for the requested schema (" + e.getMessage() + ")";
    }
}
