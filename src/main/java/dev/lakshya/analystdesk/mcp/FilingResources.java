package dev.lakshya.analystdesk.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Publishes each document under data/docs as an MCP resource.
 * Layout: data/docs/{TICKER}/{FY}/{docType}.txt  ->  filing://{TICKER}/{FY}/{docType}
 * Only text files for now. PDFs are skipped until Phase 6 adds extraction (a 30 MB PDF is not a useful resource).
 */
@Configuration
public class FilingResources {

    private static final Logger log = LoggerFactory.getLogger(FilingResources.class);

    // LESSON: a filing is a resource, not a tool. The application (or the user) chooses to read it by URI,
    // and a large document is never pushed into the model's context unless someone asks for it.
    @Bean
    List<SyncResourceSpecification> filingResourceSpecs(@Value("${analyst.data.docs}") Path root) {
        List<SyncResourceSpecification> specs = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            log.warn("No document folder at {}; no filing resources published", root);
            return specs;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Path rel = root.relativize(file);
                if (rel.getNameCount() != 3 || !file.getFileName().toString().endsWith(".txt")) {
                    log.info("Skipping {} (resources need {TICKER}/{FY}/{docType}.txt)", rel);
                    continue;
                }
                String ticker = rel.getName(0).toString();
                String year = rel.getName(1).toString();
                String docType = rel.getName(2).toString().replaceFirst("\\.txt$", "");
                String uri = "filing://" + ticker + "/" + year + "/" + docType;

                McpSchema.Resource resource = McpSchema.Resource.builder()
                        .uri(uri)
                        .name(ticker + " " + year + " " + docType)
                        .description(docType + " for " + ticker + ", fiscal year " + year)
                        .mimeType("text/plain")
                        .build();
                log.info("Resource added with name : {}", resource.name());
                // The handler reads the one file this spec was built for. It never builds a path from the
                // request, so a client cannot ask for ../../.env.
                specs.add(new SyncResourceSpecification(resource, (exchange, request) -> read(uri, file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Published {} filing resource(s) from {}", specs.size(), root);
        return specs;
    }

    private static ReadResourceResult read(String uri, Path file) {
        try {
            return new ReadResourceResult(List.of(new TextResourceContents(uri, "text/plain", Files.readString(file))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
