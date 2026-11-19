package dev.lakshya.analystdesk.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsTest {

    @TempDir Path dir;
    FinancialTools financial;
    NotebookTools notebook;
    ToolRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        Path csv = dir.resolve("financials.csv");
        Files.writeString(csv, """
                company,metric,fiscal_year,value,source
                HDFCBANK,GNPA_PCT,FY22,1.00,SAMPLE
                HDFCBANK,GNPA_PCT,FY23,2.00,SAMPLE
                HDFCBANK,GNPA_PCT,FY24,3.00,SAMPLE
                ICICIBANK,GNPA_PCT,FY22,4.00,SAMPLE
                ICICIBANK,GNPA_PCT,FY23,3.00,SAMPLE
                ICICIBANK,GNPA_PCT,FY24,2.00,SAMPLE
                ZEROBANK,GNPA_PCT,FY22,0.00,SAMPLE
                ZEROBANK,GNPA_PCT,FY24,1.00,SAMPLE
                """);
        financial = new FinancialTools(new FinancialRepository(csv));
        notebook = new NotebookTools(new InMemoryNoteStore());
        registry = new ToolRegistry(financial, notebook);
    }

    // ---- getFinancials ----

    @Test
    void getFinancialsReturnsTheValue() {
        ToolResult r = financial.getFinancials("hdfcbank", Metric.GNPA_PCT, "FY24");
        assertThat(r.ok()).isTrue();
        assertThat(((FinancialTools.FinancialValue) r.data()).value()).isEqualTo(3.0);
    }

    @Test
    void aMissingYearListsTheAvailableYears() {
        ToolResult r = financial.getFinancials("HDFCBANK", Metric.GNPA_PCT, "FY19");
        assertThat(r.ok()).isFalse();
        assertThat(r.error().code()).isEqualTo("NOT_FOUND");
        assertThat(r.error().hint()).contains("FY22", "FY23", "FY24");
    }

    @Test
    void anUnknownCompanyListsTheKnownOnes() {
        ToolResult r = financial.getFinancials("HDFC Bank Ltd", Metric.GNPA_PCT, "FY24");
        assertThat(r.error().code()).isEqualTo("UNKNOWN_COMPANY");
        assertThat(r.error().hint()).contains("HDFCBANK", "ICICIBANK");
    }

    @Test
    void aBadYearFormatSaysHowToWriteIt() {
        ToolResult r = financial.getFinancials("HDFCBANK", Metric.GNPA_PCT, "2024");
        assertThat(r.error().code()).isEqualTo("INVALID_FISCAL_YEAR");
        assertThat(r.error().hint()).contains("FY24");
    }

    // ---- compareMetric ----

    @Test
    void compareMetricSortsLowestFirst() {
        ToolResult r = financial.compareMetric(List.of("HDFCBANK", "ICICIBANK"), Metric.GNPA_PCT, "FY24");
        var c = (FinancialTools.Comparison) r.data();
        assertThat(c.lowest()).isEqualTo("ICICIBANK");
        assertThat(c.highest()).isEqualTo("HDFCBANK");
    }

    @Test
    void compareMetricNeedsTwoCompaniesAndPointsToTheOtherTool() {
        ToolResult r = financial.compareMetric(List.of("HDFCBANK"), Metric.GNPA_PCT, "FY24");
        assertThat(r.error().code()).isEqualTo("TOO_FEW_COMPANIES");
        assertThat(r.error().hint()).contains("getFinancials");
    }

    // ---- computeCagr ----

    @Test
    void cagrOverTwoYears() {
        // ICICIBANK 4.00 -> 2.00 over 2 years: (0.5 ^ 0.5 - 1) * 100 = -29.29
        ToolResult r = financial.computeCagr("ICICIBANK", Metric.GNPA_PCT, "FY22", "FY24");
        var c = (FinancialTools.Cagr) r.data();
        assertThat(c.years()).isEqualTo(2);
        assertThat(c.cagrPercent()).isCloseTo(-29.289, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void cagrOverOneYearIsThePercentChange() {
        var c = (FinancialTools.Cagr) financial.computeCagr("HDFCBANK", Metric.GNPA_PCT, "FY23", "FY24").data();
        assertThat(c.cagrPercent()).isCloseTo(50.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void cagrRejectsABackwardsRangeAndAZeroStart() {
        assertThat(financial.computeCagr("HDFCBANK", Metric.GNPA_PCT, "FY24", "FY22").error().code())
                .isEqualTo("INVALID_RANGE");
        assertThat(financial.computeCagr("ZEROBANK", Metric.GNPA_PCT, "FY22", "FY24").error().code())
                .isEqualTo("UNDEFINED");
    }

    // ---- saveNote ----

    @Test
    void savingTheSameNoteTwiceStoresOne() {
        ToolResult first = notebook.saveNote("n1", "Title", "Body");
        ToolResult second = notebook.saveNote("n1", "Title", "Body");

        assertThat(((NotebookTools.Saved) first.data()).created()).isTrue();
        assertThat(second.ok()).isTrue();
        assertThat(((NotebookTools.Saved) second.data()).created()).isFalse();
    }

    @Test
    void theSameIdWithDifferentContentIsRejected() {
        notebook.saveNote("n1", "Title", "Body");
        ToolResult r = notebook.saveNote("n1", "Title", "Different body");
        assertThat(r.error().code()).isEqualTo("ID_CONFLICT");
    }

    // ---- registry: read vs write is enforced in code ----

    @Test
    void theDefaultToolListHasNoWriteTools() {
        assertThat(registry.callbacks()).extracting(c -> c.getToolDefinition().name())
                .containsExactlyInAnyOrder("getFinancials", "compareMetric", "computeCagr");
        assertThat(registry.allCallbacks()).extracting(c -> c.getToolDefinition().name()).contains("saveNote");
    }

    @Test
    void theRegistryRefusesToRunAWriteToolEvenIfTheModelAsks() {
        String out = registry.execute(new ToolCall("1", "function", "saveNote",
                "{\"noteId\":\"n1\",\"title\":\"t\",\"text\":\"BUY\"}"));
        assertThat(out).contains("NEEDS_APPROVAL");
        // and nothing was stored: saving the same id with other content now succeeds as a first save
        assertThat(((NotebookTools.Saved) notebook.saveNote("n1", "t", "other").data()).created()).isTrue();
    }

    @Test
    void badArgumentsBecomeAnErrorTheModelCanReadNotACrash() {
        String out = registry.execute(new ToolCall("1", "function", "getFinancials",
                "{\"company\":\"HDFCBANK\",\"metric\":\"NOT_A_METRIC\",\"fiscalYear\":\"FY24\"}"));
        assertThat(out).contains("INVALID_INPUT").contains("input schema");
    }

    @Test
    void toolDescriptionsTellTheModelWhenToUseThemAndWhatTheyDoNot() {
        for (ToolCallback cb : registry.allCallbacks()) {
            assertThat(cb.getToolDefinition().description()).isNotBlank();
        }
        ToolCallback getFinancials = registry.callbacks().stream()
                .filter(c -> c.getToolDefinition().name().equals("getFinancials")).findFirst().orElseThrow();
        assertThat(getFinancials.getToolDefinition().description()).contains("Does NOT");
        // the enum values are in the schema the model sees
        assertThat(getFinancials.getToolDefinition().inputSchema()).contains("GNPA_PCT", "SLIPPAGE_PCT");
    }
}
