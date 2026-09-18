package com.digitalnest.petmemorial.journey;

import java.util.UUID;

/**
 * Future physical-print adapter. No vendor or shipping flow is implemented in this release.
 * A real adapter must enforce consent, address minimization and a separately reviewed checkout.
 */
public interface YearbookPrintOrderPort {
    PrintOrderRequestResult create(UUID memorialId, UUID userId, int year);

    record PrintOrderRequestResult(String status, String message) {}
}
