package dev.lakshya.analystdesk.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the tools and runs a model-requested call by name. This is where the model's request meets our code.
 * Tools are split into read-only and write tools, and the split is enforced here, in code.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolCallback> readTools = new LinkedHashMap<>();
    private final Map<String, ToolCallback> writeTools = new LinkedHashMap<>();

    public ToolRegistry(FinancialTools financialTools, NotebookTools notebookTools) {
        // LESSON: @Tool methods become ToolCallbacks (name, description, JSON schema) automatically.
        // The names, descriptions and parameter schemas the model sees come straight from the annotations.
        for (ToolCallback cb : MethodToolCallbackProvider.builder().toolObjects(financialTools).build().getToolCallbacks()) {
            readTools.put(cb.getToolDefinition().name(), cb);
        }
        for (ToolCallback cb : MethodToolCallbackProvider.builder().toolObjects(notebookTools).build().getToolCallbacks()) {
            writeTools.put(cb.getToolDefinition().name(), cb);
        }
    }

    /** What an agent is offered by default: read-only tools only. */
    public List<ToolCallback> callbacks() {
        return List.copyOf(readTools.values());
    }

    /** Every tool, including writes. Used to test which tool the model picks; execute() still refuses writes. */
    public List<ToolCallback> allCallbacks() {
        List<ToolCallback> all = new ArrayList<>(readTools.values());
        all.addAll(writeTools.values());
        return List.copyOf(all);
    }

    public Set<String> writeToolNames() {
        return Set.copyOf(writeTools.keySet());
    }

    public String execute(ToolCall call) {
        if (writeTools.containsKey(call.name())) {
            // LESSON: a rule in code, not in the prompt. Writes need a human; Phase 12 builds the approval step.
            log.warn("Refused write tool {} (no approval mechanism yet)", call.name());
            return ToolResult.error("NEEDS_APPROVAL", call.name() + " is a write action and needs human approval",
                    "Tell the user what you would save and ask them to confirm.").json();
        }
        ToolCallback tool = readTools.get(call.name());
        if (tool == null) {
            log.warn("Model requested unknown tool {}", call.name());
            return ToolResult.error("UNKNOWN_TOOL", "There is no tool named " + call.name(),
                    "Available tools: " + readTools.keySet()).json();
        }
        try {
            return tool.call(call.arguments());
        } catch (RuntimeException e) {
            // Most often the model sent arguments that do not match the schema (a wrong enum value, a missing field).
            // A failing tool must not kill the loop; the model gets an error it can act on.
            log.warn("Tool {} failed", call.name(), e);
            return ToolResult.error("INVALID_INPUT", "The call to " + call.name() + " failed: " + rootMessage(e),
                    "Check the argument names and types against the tool's input schema and try again.").json();
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String m = String.valueOf(t.getMessage());
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
