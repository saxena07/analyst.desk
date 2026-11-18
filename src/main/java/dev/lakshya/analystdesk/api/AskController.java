package dev.lakshya.analystdesk.api;

import dev.lakshya.analystdesk.llm.StructuredCaller;
import dev.lakshya.analystdesk.llm.StructuredOutputFailed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    public record AskRequest(@NotBlank String question) {
    }

    private final StructuredCaller caller;
    private final String promptTemplate;
    private final int maxAttempts;

    public AskController(StructuredCaller caller,
                         @Value("classpath:llm/prompts/research-note.v1.st") Resource prompt,
                         @Value("${analyst.structured.max-attempts}") int maxAttempts) {
        this.caller = caller;
        this.maxAttempts = maxAttempts;
        try {
            // Prompts live in versioned files, not Java strings, so a change is a visible diff.
            this.promptTemplate = prompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostMapping("/ask")
    public ResearchNote ask(@Valid @RequestBody AskRequest request) {
        String prompt = promptTemplate.replace("{question}", request.question());
        return caller.callTyped(prompt, ResearchNote.class, maxAttempts);
    }

    @ExceptionHandler(StructuredOutputFailed.class)
    ResponseEntity<Map<String, String>> onStructuredFailure(StructuredOutputFailed e) {
        // The caller must know: a 502 says "the upstream model failed", not "you sent a bad request".
        log.error("Structured output failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "MODEL_OUTPUT_INVALID", "message", e.getMessage()));
    }
}
