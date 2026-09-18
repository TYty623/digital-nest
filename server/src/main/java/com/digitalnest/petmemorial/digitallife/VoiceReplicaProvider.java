package com.digitalnest.petmemorial.digitallife;

import java.util.UUID;

/** Reserved capability. It must remain disabled until explicit voice-rights consent and review exist. */
public interface VoiceReplicaProvider {
    ProviderTask create(UUID memorialId, UUID authorizationId);

    record ProviderTask(String providerTaskId, String status) {}
}
