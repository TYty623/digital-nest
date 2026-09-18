package com.digitalnest.petmemorial.journey;

import java.util.List;

public record RitualSpace(
        List<MemorialArchiveEntry> archiveEntries,
        List<MemorialAnniversary> anniversaries,
        List<MemoryCapsule> capsules,
        List<RitualRecord> rituals
) {
}
