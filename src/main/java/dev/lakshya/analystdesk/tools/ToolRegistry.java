package dev.lakshya.analystdesk.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds the tools and runs a model-requested call by name. This is where the model's request meets our code. */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolCallback> byName = new LinkedHashMap<>();

    public ToolRegistry(FinancialsLookup financials, Calculator calculator) {
        for (ToolCallback cb : List.of(financials.callback(), calculator.callback())) {
            byName.put(cb.getToolDefinition().name(), cb);
        }
    }

    public List<ToolCallback> callbacks() {
        return List.copyOf(byName.values());
    }

    public String execute(ToolCall call) {
        ToolCallback tool = byName.get(call.name());
        if (tool == null) {
            log.warn("Model requested unknown tool {}", call.name());
            return "{\"error\":\"Unknown tool: " + call.name() + "\"}";
        }
        try {
            return tool.call(call.arguments());
        } catch (RuntimeException e) {
            // A failing tool must not kill the loop; the model gets the error and can react.
            // (Phase 3 turns this into structured, actionable errors.)
            log.warn("Tool {} failed", call.name(), e);
            return "{\"error\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }
}
