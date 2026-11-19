package dev.lakshya.analystdesk.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Reads data/financials.csv (company, metric, fiscal_year, value, source) once at startup. */
@Component
public class FinancialRepository {

    public record Figure(double value, String source) {
    }

    // company -> metric -> fiscal year -> figure
    private final Map<String, Map<Metric, Map<String, Figure>>> data = new TreeMap<>();

    public FinancialRepository(@Value("${analyst.data.financials}") Path csv) {
        try {
            for (String line : Files.readAllLines(csv)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("company,")) {
                    continue;
                }
                String[] f = line.split(",");
                data.computeIfAbsent(f[0].toUpperCase(), k -> new TreeMap<>())
                        .computeIfAbsent(Metric.valueOf(f[1]), k -> new TreeMap<>())
                        .put(f[2].toUpperCase(), new Figure(Double.parseDouble(f[3]), f.length > 4 ? f[4] : ""));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + csv, e);
        }
    }

    public boolean knowsCompany(String company) {
        return data.containsKey(company.toUpperCase());
    }

    public List<String> companies() {
        return List.copyOf(data.keySet());
    }

    public Optional<Figure> find(String company, Metric metric, String fiscalYear) {
        return Optional.ofNullable(data.get(company.toUpperCase()))
                .map(m -> m.get(metric))
                .map(y -> y.get(fiscalYear.toUpperCase()));
    }

    public List<String> yearsFor(String company, Metric metric) {
        return Optional.ofNullable(data.get(company.toUpperCase()))
                .map(m -> m.get(metric))
                .map(y -> List.copyOf(new TreeSet<>(y.keySet())))
                .orElse(List.of());
    }
}
