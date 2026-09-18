package com.digitalnest.petmemorial.digitallife;

import java.util.List;
import java.util.UUID;

/** Reserved port. A future implementation must only index consented, traceable source material. */
public interface VectorSearchProvider {
    List<UUID> search(UUID memorialId, String query, int limit);
}
