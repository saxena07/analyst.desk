package dev.lakshya.analystdesk.mcp;

import dev.lakshya.analystdesk.tools.FinancialTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    // LESSON: the same @Tool beans from Phase 3, now published over a protocol.
    // Nothing about the tools changes; only who can reach them.
    //
    // Only the READ-ONLY tools are published. NotebookTools.saveNote is a write action, and anyone who can
    // reach /mcp (MCP Inspector, Claude Desktop, a script) could call it with no human approval. It stays
    // off the server until Phase 12 adds confirmation, and Phase 16 adds auth to the endpoint.
    @Bean
    ToolCallbackProvider analystTools(FinancialTools financialTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(financialTools)
                .build();
    }

    // LESSON: tools are model-controlled, resources are application-controlled,
    // prompts are user-controlled. Pick the primitive by who decides to use it.
    //   Tool     -> the model decides   -> getFinancials (see above)
    //   Resource -> the app/user decides -> FilingResources (a document by URI)
    //   Prompt   -> the user decides    -> ResearchPrompts (a template the user picks)
}
