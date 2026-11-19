package dev.lakshya.analystdesk.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchPrompts {

    // LESSON: a prompt is a template the USER picks (in Claude Desktop it shows up as a menu item).
    // The server fills in the arguments and hands back messages; the client decides what to do with them.
    @McpPrompt(name = "bank-asset-quality-review",
            description = "Review a bank's asset quality over a range of fiscal years using the financial tools.")
    public GetPromptResult bankAssetQualityReview(
            @McpArg(name = "company", description = "Bank ticker, for example HDFCBANK", required = true) String company,
            @McpArg(name = "years", description = "Fiscal years to cover, for example FY22 to FY24", required = true) String years) {
        String text = """
                Review the asset quality of %s over %s.

                1. Use getFinancials to get gross NPA (GNPA_PCT), net NPA (NNPA_PCT), provision coverage (PCR_PCT)
                   and slippages (SLIPPAGE_PCT) for each year.
                2. Use computeCagr to quantify the trend in gross NPA across the period.
                3. Say whether asset quality improved or worsened, citing the figures you used.
                4. Do not give investment advice and do not do any arithmetic yourself.
                """.formatted(company, years);
        return new GetPromptResult("Asset quality review of " + company,
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }
}
