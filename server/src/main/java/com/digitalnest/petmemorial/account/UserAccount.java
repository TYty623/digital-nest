package com.digitalnest.petmemorial.account;

import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String status
) {
}
