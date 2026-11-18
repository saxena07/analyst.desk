package dev.lakshya.analystdesk.llm;

import dev.lakshya.analystdesk.api.ResearchNote;
import dev.lakshya.analystdesk.api.ResearchNote.Confidence;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredCallerTest {

    private static final String VALID = """
            {"question":"q","summary":"s",
             "findings":[{"claim":"c","evidence":"e"}],"confidence":"HIGH"}""";

    /** A fake model that replays canned replies and records the prompts it received. */
    static class FakeModel implements ChatModel {
        final List<String> replies;
        final List<String> prompts = new ArrayList<>();
        int calls = 0;

        FakeModel(String... replies) {
            this.replies = List.of(replies);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getContents());
            String reply = replies.get(Math.min(calls++, replies.size() - 1));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private StructuredCaller callerFor(FakeModel model) {
        return new StructuredCaller(ChatClient.create(model), validator);
    }

    @Test
    void retriesAfterBrokenJsonAndFeedsTheErrorBack() {
        FakeModel model = new FakeModel("this is not json {{{", VALID);

        ResearchNote note = callerFor(model).callTyped("question", ResearchNote.class, 3);

        assertThat(note.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(model.calls).isEqualTo(2);
        assertThat(model.prompts.get(1)).contains("Your last answer was invalid");
    }

    @Test
    void retriesWhenJsonParsesButFailsValidation() {
        String noFindings = """
                {"question":"q","summary":"s","findings":[],"confidence":"LOW"}""";
        FakeModel model = new FakeModel(noFindings, VALID);

        ResearchNote note = callerFor(model).callTyped("question", ResearchNote.class, 3);

        assertThat(note.findings()).hasSize(1);
        assertThat(model.prompts.get(1)).contains("findings");
    }

    @Test
    void failsLoudlyAfterTheLastAttempt() {
        FakeModel model = new FakeModel("nope");

        assertThatThrownBy(() -> callerFor(model).callTyped("question", ResearchNote.class, 2))
                .isInstanceOf(StructuredOutputFailed.class)
                .hasMessageContaining("2 attempts");
        assertThat(model.calls).isEqualTo(2);
    }
}
