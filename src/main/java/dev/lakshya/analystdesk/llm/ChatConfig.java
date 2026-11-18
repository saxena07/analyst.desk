package dev.lakshya.analystdesk.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /**
     * One ChatClient for the whole app. The autoconfigured builder is already
     * wired to the Gemini ChatModel (model and temperature come from application.yml).
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are an equity research assistant. "
                        + "You write careful, factual notes and never give investment advice.")
                .build();
    }
}
