package dev.lakshya.analystdesk.agents;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import java.util.ArrayList;
import java.util.List;

/** A step-by-step record of the loop, so you can see exactly what the model asked and what came back. */
public class LoopTrace {

    public enum Kind { MODEL_TURN, TOOL_CALL }

    public record Entry(int step, Kind kind, String detail, long millis) {
    }

    private final List<Entry> entries = new ArrayList<>();

    void recordModelTurn(int step, AssistantMessage reply, long millis) {
        String detail = reply.hasToolCalls()
                ? "requested " + reply.getToolCalls().size() + " tool call(s)"
                : "final answer";
        entries.add(new Entry(step, Kind.MODEL_TURN, detail, millis));
    }

    void recordToolCall(int step, ToolCall call, String result, long millis) {
        entries.add(new Entry(step, Kind.TOOL_CALL,
                call.name() + "(" + call.arguments() + ") -> " + result, millis));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public long toolCalls() {
        return entries.stream().filter(e -> e.kind() == Kind.TOOL_CALL).count();
    }
}
