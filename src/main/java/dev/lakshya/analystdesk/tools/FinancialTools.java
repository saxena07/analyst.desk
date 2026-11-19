package dev.lakshya.analystdesk.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only financial tools. All three are safe to call any number of times.
 * Numbers come from here (data/financials.csv), never from the model's own arithmetic.
 */
@Component
public class FinancialTools {

    private static final Pattern FISCAL_YEAR = Pattern.compile("FY(\\d{2})", Pattern.CASE_INSENSITIVE);

    public record FinancialValue(String company, Metric metric, String fiscalYear, double value,
                                 String unit, String source) {
    }

    public record Comparison(Metric metric, String fiscalYear, List<FinancialValue> valuesLowestFirst,
                             String lowest, String highest) {
    }

    public record Cagr(String company, Metric metric, String fromYear, String toYear,
                       double fromValue, double toValue, int years, double cagrPercent) {
    }

    private final FinancialRepository repo;

    public FinancialTools(FinancialRepository repo) {
        this.repo = repo;
    }

    // LESSON: the description is the tool's prompt. Say when to use it,
    // what it returns, and what it does NOT do.
    @Tool(description = """
            Returns one reported financial metric for one bank and one fiscal year, from annual reports.
            Use this for any single number you will state or cite.
            Does NOT calculate growth or compare banks: use computeCagr for growth over time and
            compareMetric for several banks in the same year.
            Fiscal year format: FY24 means April 2023 to March 2024.
            Returns ok=true with the value, or ok=false with an error code and a hint listing what is available.""")
    public ToolResult getFinancials(
            @ToolParam(description = "Bank ticker, for example HDFCBANK, ICICIBANK or SBIN") String company,
            @ToolParam(description = "GNPA_PCT = gross NPA %, NNPA_PCT = net NPA %, PCR_PCT = provision coverage %, "
                    + "SLIPPAGE_PCT = fresh slippages %") Metric metric,
            @ToolParam(description = "Fiscal year such as FY24") String fiscalYear) {
        if (!repo.knowsCompany(company)) {
            return unknownCompany(company);
        }
        if (!isFiscalYear(fiscalYear)) {
            return badYear(fiscalYear);
        }
        return repo.find(company, metric, fiscalYear)
                .map(f -> ToolResult.ok(new FinancialValue(company.toUpperCase(), metric, fiscalYear.toUpperCase(),
                        f.value(), "percent", f.source())))
                // LESSON: an error the model can act on, not an exception or an empty string.
                .orElseGet(() -> ToolResult.error("NOT_FOUND",
                        "No " + metric + " for " + company + " in " + fiscalYear,
                        "Available years for " + company + " and " + metric + ": " + repo.yearsFor(company, metric)));
    }

    @Tool(description = """
            Compares several banks on ONE metric in ONE fiscal year, and says which is lowest and highest.
            Use when the question puts banks side by side for the same year.
            Does NOT show change over time (use computeCagr) and is not needed for a single bank (use getFinancials).
            Returns the values sorted lowest first, or ok=false if any bank or figure is missing.""")
    public ToolResult compareMetric(
            @ToolParam(description = "Bank tickers to compare, at least two, for example [\"HDFCBANK\", \"ICICIBANK\"]")
            List<String> companies,
            @ToolParam(description = "The metric to compare") Metric metric,
            @ToolParam(description = "Fiscal year such as FY24") String fiscalYear) {
        if (companies == null || companies.size() < 2) {
            return ToolResult.error("TOO_FEW_COMPANIES", "compareMetric needs at least two companies",
                    "For one company use getFinancials. Known companies: " + repo.companies());
        }
        if (!isFiscalYear(fiscalYear)) {
            return badYear(fiscalYear);
        }
        List<FinancialValue> values = new java.util.ArrayList<>();
        for (String company : companies) {
            if (!repo.knowsCompany(company)) {
                return unknownCompany(company);
            }
            Optional<FinancialRepository.Figure> f = repo.find(company, metric, fiscalYear);
            if (f.isEmpty()) {
                return ToolResult.error("NOT_FOUND", "No " + metric + " for " + company + " in " + fiscalYear,
                        "Available years for " + company + ": " + repo.yearsFor(company, metric));
            }
            values.add(new FinancialValue(company.toUpperCase(), metric, fiscalYear.toUpperCase(),
                    f.get().value(), "percent", f.get().source()));
        }
        values.sort(Comparator.comparingDouble(FinancialValue::value));
        return ToolResult.ok(new Comparison(metric, fiscalYear.toUpperCase(), values,
                values.get(0).company(), values.get(values.size() - 1).company()));
    }

    @Tool(description = """
            Computes the compound annual growth rate (CAGR, in percent) of one metric for one bank between two
            fiscal years. It looks up both values itself, so do not fetch them first.
            Use for growth or trend questions. For a one-year span the CAGR equals the simple percent change.
            Does NOT compare banks (use compareMetric). Negative means the metric fell.
            Returns both values, the number of years and the CAGR, or ok=false with a hint.""")
    public ToolResult computeCagr(
            @ToolParam(description = "Bank ticker, for example HDFCBANK") String company,
            @ToolParam(description = "The metric to measure growth of") Metric metric,
            @ToolParam(description = "Start fiscal year, for example FY22") String fromYear,
            @ToolParam(description = "End fiscal year, later than the start, for example FY24") String toYear) {
        if (!repo.knowsCompany(company)) {
            return unknownCompany(company);
        }
        if (!isFiscalYear(fromYear)) {
            return badYear(fromYear);
        }
        if (!isFiscalYear(toYear)) {
            return badYear(toYear);
        }
        int years = yearNumber(toYear) - yearNumber(fromYear);
        if (years <= 0) {
            return ToolResult.error("INVALID_RANGE", "toYear " + toYear + " is not after fromYear " + fromYear,
                    "Pass the earlier year as fromYear and the later year as toYear.");
        }
        Optional<FinancialRepository.Figure> from = repo.find(company, metric, fromYear);
        Optional<FinancialRepository.Figure> to = repo.find(company, metric, toYear);
        if (from.isEmpty() || to.isEmpty()) {
            String missing = from.isEmpty() ? fromYear : toYear;
            return ToolResult.error("NOT_FOUND", "No " + metric + " for " + company + " in " + missing,
                    "Available years for " + company + " and " + metric + ": " + repo.yearsFor(company, metric));
        }
        if (from.get().value() <= 0) {
            return ToolResult.error("UNDEFINED", "CAGR is undefined because the start value is " + from.get().value(),
                    "Report the two values with getFinancials and describe the change in words.");
        }
        double ratio = to.get().value() / from.get().value();
        double cagr = (Math.pow(ratio, 1.0 / years) - 1.0) * 100.0;
        return ToolResult.ok(new Cagr(company.toUpperCase(), metric, fromYear.toUpperCase(), toYear.toUpperCase(),
                from.get().value(), to.get().value(), years, cagr));
    }

    private ToolResult unknownCompany(String company) {
        return ToolResult.error("UNKNOWN_COMPANY", "No data for company " + company,
                "Known companies: " + repo.companies() + ". Use the ticker, for example HDFCBANK.");
    }

    private ToolResult badYear(String year) {
        return ToolResult.error("INVALID_FISCAL_YEAR", "\"" + year + "\" is not a fiscal year",
                "Use the form FY24 (FY24 = April 2023 to March 2024).");
    }

    private static boolean isFiscalYear(String year) {
        return year != null && FISCAL_YEAR.matcher(year.trim()).matches();
    }

    private static int yearNumber(String year) {
        Matcher m = FISCAL_YEAR.matcher(year.trim());
        m.matches();
        return Integer.parseInt(m.group(1));
    }
}
