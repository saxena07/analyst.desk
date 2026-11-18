package dev.lakshya.analystdesk.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/** Arithmetic belongs to code, not to the model. Phase 3 adds compareMetric and computeCagr. */
@Component
public class Calculator {

    public record Input(double from, double to) {
    }

    public record Output(Double percentChange, String error) {
    }

    public Output percentChange(Input in) {
        if (in.from() == 0) {
            return new Output(null, "Cannot compute a percent change from zero");
        }
        return new Output((in.to() - in.from()) / in.from() * 100.0, null);
    }

    public ToolCallback callback() {
        return FunctionToolCallback.builder("percentChange", this::percentChange)
                .description("Percent change from one number to another: (to - from) / from * 100.")
                .inputType(Input.class)
                .build();
    }
}
