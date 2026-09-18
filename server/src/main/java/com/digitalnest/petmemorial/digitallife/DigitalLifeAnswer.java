package com.digitalnest.petmemorial.digitallife;

import java.util.List;

public record DigitalLifeAnswer(
        String answer,
        String generationMode,
        List<Citation> citations,
        String safetyNotice
) {
    public record Citation(String sourceType, String sourceLabel, String statement) {
    }
}
