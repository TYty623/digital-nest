package com.digitalnest.petmemorial.digitallife;

import java.util.UUID;

/** Reserved capability. It must remain disabled until explicit image-rights consent and review exist. */
public interface AvatarProvider {
    VoiceReplicaProvider.ProviderTask create(UUID memorialId, UUID authorizationId);
}
