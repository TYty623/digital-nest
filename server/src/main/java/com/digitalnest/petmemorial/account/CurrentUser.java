package com.digitalnest.petmemorial.account;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public record CurrentUser(UUID id, String email, String displayName, Set<String> roles) implements Serializable {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public boolean isStaff() {
        return roles.contains("ADMIN") || roles.contains("MODERATOR");
    }
}
