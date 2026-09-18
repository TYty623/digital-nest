package com.digitalnest.petmemorial.digitallife;

import java.util.List;

public record DigitalLifeWorkspace(
        DigitalLifeProfile profile,
        List<DigitalLifeFact> facts,
        boolean profileAvailable,
        boolean qaAvailable,
        List<DigitalLifeProviderStatus> providerStatuses,
        String ethicsNotice
) {
}
