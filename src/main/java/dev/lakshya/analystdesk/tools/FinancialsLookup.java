package dev.lakshya.analystdesk.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads one figure from data/financials.csv. Deliberately plain: Phase 3 rewrites this
 * tool with @Tool, a proper description and structured errors.
 */
@Component
public class FinancialsLookup {

    public record Input(String company, String metric, String fiscalYear) {
    }

    public record Output(String company, String metric, String fiscalYear, Double value, String error) {
    }

    private final Map<String, Double> values = new HashMap<>();

    public FinancialsLookup(@Value("${analyst.data.financials}") Path csv) {
        try {
            for (String line : Files.readAllLines(csv)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("company,")) {
                    continue;
                }
                String[] f = line.split(",");
                values.put(key(f[0], f[1], f[2]), Double.parseDouble(f[3]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + csv, e);
        }
    }

    public Output getFinancial(Input in) {
        Double v = values.get(key(in.company(), in.metric(), in.fiscalYear()));
        return v == null
                ? new Output(in.company(), in.metric(), in.fiscalYear(), null, "No such figure")
                : new Output(in.company(), in.metric(), in.fiscalYear(), v, null);
    }

    // LESSON: to the model a tool is just a name, a description and a JSON schema for the input.
    // The code behind it is ours; the model never sees it and never runs it.
    public ToolCallback callback() {
        return FunctionToolCallback.builder("getFinancial", this::getFinancial)
                .description("Look up one reported figure for a bank: company ticker (HDFCBANK, ICICIBANK, SBIN), "
                        + "metric (GNPA_PCT or NNPA_PCT) and fiscal year (FY22, FY23, FY24).")
                .inputType(Input.class)
                .build();
    }

    private static String key(String company, String metric, String year) {
        return (company + "|" + metric + "|" + year).toUpperCase();
    }
}
