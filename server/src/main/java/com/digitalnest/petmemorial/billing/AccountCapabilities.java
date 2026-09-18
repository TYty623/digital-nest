package com.digitalnest.petmemorial.billing;

import java.util.List;

public record AccountCapabilities(
        String planCode,
        List<String> enabled,
        List<String> themeCodes,
        boolean allThemeCollectionUnlocked
) {
    public AccountCapabilities(String planCode, List<String> enabled) {
        this(planCode, enabled, List.of("NIGHT"), false);
    }

    public boolean includes(FeatureCode code) {
        return enabled.contains(code.name());
    }
}
