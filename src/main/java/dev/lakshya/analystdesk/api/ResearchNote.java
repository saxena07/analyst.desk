package dev.lakshya.analystdesk.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// The shape of the data is the contract with the model. The annotations are the
// validation rules StructuredCaller enforces after the model answers.
public record ResearchNote(
        @NotBlank String question,
        @NotBlank String summary,
        @NotEmpty List<@Valid Finding> findings,
        @NotNull Confidence confidence) {

    public record Finding(
            @NotBlank String claim,
            @NotBlank String evidence) {
    }

    public enum Confidence { LOW, MEDIUM, HIGH }
}
