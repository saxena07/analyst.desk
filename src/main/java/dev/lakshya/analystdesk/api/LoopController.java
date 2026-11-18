package dev.lakshya.analystdesk.api;

import dev.lakshya.analystdesk.agents.LoopResult;
import dev.lakshya.analystdesk.agents.ManualAgentLoop;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoopController {

    public record LoopRequest(@NotBlank String question, @Min(1) @Max(20) Integer maxSteps) {
    }

    private final ManualAgentLoop loop;
    private final int defaultMaxSteps;

    public LoopController(ManualAgentLoop loop, @Value("${analyst.loop.max-steps}") int defaultMaxSteps) {
        this.loop = loop;
        this.defaultMaxSteps = defaultMaxSteps;
    }

    @PostMapping("/loop/ask")
    public LoopResult ask(@Valid @RequestBody LoopRequest request) {
        int maxSteps = request.maxSteps() != null ? request.maxSteps() : defaultMaxSteps;
        return loop.run(request.question(), maxSteps);
    }
}
