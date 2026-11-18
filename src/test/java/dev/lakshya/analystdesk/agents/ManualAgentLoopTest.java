package dev.lakshya.analystdesk.agents;

import dev.lakshya.analystdesk.tools.Calculator;
import dev.lakshya.analystdesk.tools.FinancialsLookup;
import dev.lakshya.analystdesk.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ManualAgentLoopTest {

    @TempDir Path dir;
    ToolRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        Path csv = dir.resolve("financials.csv");
        Files.writeString(csv, """
                company,metric,fiscal_year,value,source
                HDFCBANK,GNPA_PCT,FY23,2.00,SAMPLE
                HDFCBANK,GNPA_PCT,FY24,3.00,SAMPLE
                """);
        registry = new ToolRegistry(new FinancialsLookup(csv), new Calculator());
    }

    /** A fake model: replies come from a function of the call number, and it records every prompt. */
    static class FakeModel implements ChatModel {
        final Function<Integer, AssistantMessage> script;
        final List<Prompt> prompts = new ArrayList<>();
        final List<Integer> messageCounts = new ArrayList<>(); // snapshot: the loop keeps mutating its list

        FakeModel(Function<Integer, AssistantMessage> script) {
            this.script = script;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            messageCounts.add(prompt.getInstructions().size());
            return new ChatResponse(List.of(new Generation(script.apply(prompts.size()))));
        }
    }

    private static AssistantMessage asks(String id, String tool, String json) {
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new ToolCall(id, "function", tool, json))).build();
    }

    private ManualAgentLoop loopWith(FakeModel model) {
        return new ManualAgentLoop(model, registry, new ByteArrayResource("system".getBytes()));
    }

    @Test
    void runsTwoLookupsThenACalculationThenAnswers() {
        FakeModel model = new FakeModel(n -> switch (n) {
            case 1 -> asks("1", "getFinancial", "{\"company\":\"HDFCBANK\",\"metric\":\"GNPA_PCT\",\"fiscalYear\":\"FY23\"}");
            case 2 -> asks("2", "getFinancial", "{\"company\":\"HDFCBANK\",\"metric\":\"GNPA_PCT\",\"fiscalYear\":\"FY24\"}");
            case 3 -> asks("3", "percentChange", "{\"from\":2.0,\"to\":3.0}");
            default -> new AssistantMessage("GNPA rose 50%.");
        });

        LoopResult result = loopWith(model).run("HDFC GNPA growth FY23 to FY24?", 6);

        assertThat(result.status()).isEqualTo(LoopResult.Status.ANSWERED);
        assertThat(result.answer()).isEqualTo("GNPA rose 50%.");
        assertThat(result.stepsUsed()).isEqualTo(4);
        var toolEntries = result.trace().stream().filter(e -> e.kind() == LoopTrace.Kind.TOOL_CALL).toList();
        assertThat(toolEntries).hasSize(3);
        assertThat(toolEntries.get(0).detail()).contains("\"value\":2.0");
        assertThat(toolEntries.get(2).detail()).contains("50.0");
        // The tool results were sent back to the model: the last prompt holds them.
        assertThat(model.messageCounts.get(3)).isEqualTo(2 + 3 * 2);
    }

    @Test
    void aMissingFigureComesBackAsAnErrorTheModelCanReadNotACrash() {
        FakeModel model = new FakeModel(n -> n == 1
                ? asks("1", "getFinancial", "{\"company\":\"HDFCBANK\",\"metric\":\"GNPA_PCT\",\"fiscalYear\":\"FY99\"}")
                : new AssistantMessage("No FY99 figure exists."));

        LoopResult result = loopWith(model).run("q", 6);

        assertThat(result.status()).isEqualTo(LoopResult.Status.ANSWERED);
        assertThat(result.trace().get(1).detail()).contains("No such figure");
    }

    @Test
    void aModelThatAlwaysRequestsAToolHitsTheStepCap() {
        FakeModel model = new FakeModel(n ->
                asks("x" + n, "percentChange", "{\"from\":1.0,\"to\":2.0}"));

        LoopResult result = loopWith(model).run("q", 3);

        assertThat(result.status()).isEqualTo(LoopResult.Status.STOPPED_AT_LIMIT);
        assertThat(result.answer()).isNull();
        assertThat(result.message()).contains("3 steps");
        assertThat(model.prompts).hasSize(3);
    }
}
