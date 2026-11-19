package dev.lakshya.analystdesk.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LESSON: measure tool choice, do not assume it. Each question has the tool the model SHOULD pick first;
 * we show the model every tool (internal execution is off, so nothing runs) and look at its first request.
 *
 * Calls the live Gemini API, so it only runs with -Dlive=true. Run it:
 *   ./mvnw test -Dtest=ToolChoiceTest -Dlive=true
 * "Break it": make two tool descriptions nearly identical and compare the score.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "live", matches = "true")
class ToolChoiceTest {

    @Autowired ChatModel chatModel;
    @Autowired ToolRegistry registry;
    @Value("classpath:llm/prompts/agent-loop.v2.st") Resource systemPrompt;

    // question -> the tool the model should call first ("NONE" = it should answer or ask, not call a tool)
    private static final Map<String, String> CASES = new LinkedHashMap<>();
    static {
        CASES.put("What was HDFCBANK's gross NPA in FY24?", "getFinancials");
        CASES.put("Give me ICICIBANK's net NPA for FY23.", "getFinancials");
        CASES.put("What is SBIN's provision coverage ratio in FY22?", "getFinancials");
        CASES.put("What was the slippage ratio of HDFCBANK in FY24?", "getFinancials");
        CASES.put("Which of HDFCBANK, ICICIBANK and SBIN had the lowest gross NPA in FY24?", "compareMetric");
        CASES.put("Compare HDFCBANK and ICICIBANK on net NPA for FY23.", "compareMetric");
        CASES.put("Who had the higher provision coverage in FY24, SBIN or ICICIBANK?", "compareMetric");
        CASES.put("How fast did ICICIBANK's gross NPA change per year from FY22 to FY24?", "computeCagr");
        CASES.put("What is the CAGR of HDFCBANK's gross NPA between FY22 and FY24?", "computeCagr");
        CASES.put("What was HDFCBANK gross NPA growth from FY23 to FY24?", "computeCagr");
        CASES.put("Is SBIN's slippage ratio trending up or down from FY22 to FY24, and by how much per year?", "computeCagr");
        CASES.put("Save a note with id hdfc-npa-fy24 and title 'HDFC NPA' containing: gross NPA rose in FY24.", "saveNote");
        CASES.put("Please store this in my notebook as note id icici-pcr: ICICI provision coverage improved.", "saveNote");
        CASES.put("What does gross NPA mean?", "NONE");
        CASES.put("Should I buy HDFCBANK shares?", "NONE");
    }

    @Test
    void reportsHowOftenTheModelPicksTheExpectedTool() throws Exception {
        String system = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .toolCallbacks(registry.allCallbacks())
                .internalToolExecutionEnabled(false)
                .build();

        int hits = 0;
        List<String> misses = new ArrayList<>();
        for (var entry : CASES.entrySet()) {
            AssistantMessage reply = chatModel.call(new Prompt(
                    List.of(new SystemMessage(system), new UserMessage(entry.getKey())), options))
                    .getResult().getOutput();
            String picked = reply.hasToolCalls()
                    ? reply.getToolCalls().stream().map(AssistantMessage.ToolCall::name).distinct().collect(Collectors.joining("+"))
                    : "NONE";
            boolean hit = picked.equals(entry.getValue());
            hits += hit ? 1 : 0;
            System.out.printf("TOOLCHOICE %s expected=%-13s picked=%-13s  %s%n", hit ? "ok  " : "MISS", entry.getValue(), picked, entry.getKey());
            if (!hit) {
                misses.add(entry.getKey());
            }
        }
        System.out.printf("TOOLCHOICE RESULT: %d/%d = %.0f%%%n", hits, CASES.size(), 100.0 * hits / CASES.size());
    }
}
