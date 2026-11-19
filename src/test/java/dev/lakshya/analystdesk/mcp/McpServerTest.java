package dev.lakshya.analystdesk.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Talks to the running app exactly as MCP Inspector or Claude Desktop would: over HTTP, with a real MCP client.
 * No Gemini call is made, so the key below is a dummy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.google.genai.api-key=dummy-key-not-used")
class McpServerTest {

    @LocalServerPort int port;
    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").build()).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void listsTheReadOnlyToolsAndNotTheWriteTool() {
        var names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names).containsExactlyInAnyOrder("getFinancials", "compareMetric", "computeCagr");
        assertThat(names).doesNotContain("saveNote");
    }

    @Test
    void aToolCallWorksOverTheProtocol() {
        var result = client.callTool(new CallToolRequest("getFinancials",
                Map.of("company", "HDFCBANK", "metric", "GNPA_PCT", "fiscalYear", "FY24")));
        assertThat(result.isError()).isFalse();
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"ok\":true").contains("3.0");
    }

    @Test
    void listsFilingResourcesByUri() {
        var uris = client.listResources().resources().stream().map(McpSchema.Resource::uri).toList();
        assertThat(uris).contains("filing://HDFCBANK/FY24/annual-report",
                "filing://HDFCBANK/FY23/annual-report", "filing://SBIN/FY24/annual-report");
    }

    @Test
    void readsAFilingResource() {
        var result = client.readResource(new McpSchema.ReadResourceRequest("filing://HDFCBANK/FY24/annual-report"));
        String text = ((TextResourceContents) result.contents().get(0)).text();
        assertThat(text).contains("SAMPLE PLACEHOLDER").contains("HDFCBANK");
    }

    @Test
    void anUnknownOrTraversingUriIsRefused() {
        assertThatThrownBy(() -> client.readResource(new McpSchema.ReadResourceRequest("filing://../../.env")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void listsAndFillsThePromptTemplate() {
        var prompts = client.listPrompts().prompts();
        assertThat(prompts).extracting(McpSchema.Prompt::name).contains("bank-asset-quality-review");
        assertThat(prompts.get(0).arguments()).extracting(McpSchema.PromptArgument::name)
                .containsExactlyInAnyOrder("company", "years");

        var result = client.getPrompt(new McpSchema.GetPromptRequest("bank-asset-quality-review",
                Map.of("company", "ICICIBANK", "years", "FY22 to FY24")));
        String text = ((TextContent) result.messages().get(0).content()).text();
        assertThat(text).contains("ICICIBANK").contains("FY22 to FY24").contains("computeCagr");
    }
}
