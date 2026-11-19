package dev.lakshya.analystdesk.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Every tool returns this: either data, or a structured error, never an exception the model cannot read. */
public record ToolResult(boolean ok, Object data, ToolError error) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static ToolResult ok(Object data) {
        return new ToolResult(true, data, null);
    }

    public static ToolResult error(String code, String message, String hint) {
        return new ToolResult(false, null, new ToolError(code, message, hint));
    }

    public String json() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"ok\":false,\"error\":{\"code\":\"INTERNAL\",\"message\":\"Could not serialise the result\",\"hint\":null}}";
        }
    }
}
