package com.digitalnest.petmemorial.shared.access;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

public final class MemorialAccess {

    private static final String SESSION_PREFIX = "memorial-access:";

    private MemorialAccess() {
    }

    public static void grant(HttpSession session, UUID memorialId) {
        session.setAttribute(SESSION_PREFIX + memorialId, Boolean.TRUE);
    }

    public static boolean isGranted(HttpSession session, UUID memorialId) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_PREFIX + memorialId));
    }
}
