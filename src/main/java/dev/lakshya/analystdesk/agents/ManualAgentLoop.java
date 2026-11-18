package dev.lakshya.analystdesk.agents;

import dev.lakshya.analystdesk.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ManualAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ManualAgentLoop.class);

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;

    public ManualAgentLoop(ChatModel chatModel, ToolRegistry toolRegistry,
                           @Value("classpath:llm/prompts/agent-loop.v1.st") Resource systemPrompt) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        try {
            this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // LESSON: this is the whole agent. Everything else is decoration.
    public LoopResult run(String question, int maxSteps) {
        log.info("Loop start: maxSteps={}, question=\"{}\"", maxSteps, question);
        LoopTrace trace = new LoopTrace();
        List<Message> messages = new ArrayList<>(List.of(
                new SystemMessage(systemPrompt), new UserMessage(question)));

        // LESSON: the tools are described to the model, but automatic execution is OFF.
        // Without this line the framework would run the loop for us and hide it.
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .toolCallbacks(toolRegistry.callbacks())
                .internalToolExecutionEnabled(false)
                .build();

        for (int step = 1; step <= maxSteps; step++) {
            long t0 = System.nanoTime();
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            AssistantMessage reply = response.getResult().getOutput();
            messages.add(reply);
            trace.recordModelTurn(step, reply, millisSince(t0));
            log.info("Step {}/{}: model replied in {} ms ({})", step, maxSteps, millisSince(t0),
                    reply.hasToolCalls() ? reply.getToolCalls().size() + " tool call(s)" : "final answer");

            // LESSON: "no tool calls" is the signal that the model is finished.
            if (!reply.hasToolCalls()) {
                log.info("Loop answered after {} step(s)", step);
                return LoopResult.answered(reply.getText(), step, trace);
            }

            // LESSON: the model only ASKS. Our code decides whether and how to run each tool,
            // which is where later phases add policy, approval and audit.
            List<ToolResponse> responses = new ArrayList<>();
            for (ToolCall call : reply.getToolCalls()) {
                long t1 = System.nanoTime();
                String result = toolRegistry.execute(call);
                trace.recordToolCall(step, call, result, millisSince(t1));
                log.info("Step {}: tool {}({}) -> {}", step, call.name(), call.arguments(), result);
                responses.add(new ToolResponse(call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
        // LESSON: without a cap, a confused model can loop forever and spend money.
        log.warn("Loop stopped at the step limit ({}) without a final answer", maxSteps);
        return LoopResult.stoppedAtLimit(maxSteps, trace);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
