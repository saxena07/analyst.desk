package dev.lakshya.analystdesk.agents;

import java.util.List;

/** The loop either answered or ran out of steps. A caller must be able to tell which. */
public record LoopResult(Status status, String answer, int stepsUsed, String message, List<LoopTrace.Entry> trace) {

    public enum Status { ANSWERED, STOPPED_AT_LIMIT }

    static LoopResult answered(String answer, int steps, LoopTrace trace) {
        return new LoopResult(Status.ANSWERED, answer, steps, null, trace.entries());
    }

    static LoopResult stoppedAtLimit(int maxSteps, LoopTrace trace) {
        return new LoopResult(Status.STOPPED_AT_LIMIT, null, maxSteps,
                "Stopped after " + maxSteps + " steps without a final answer; the answer is incomplete.",
                trace.entries());
    }
}
