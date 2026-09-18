package com.digitalnest.petmemorial.digitallife;

/** Reserved moderation adapter for any future external AI, voice or avatar generation request. */
public interface ContentModerationPort {
    ModerationResult review(String content, String capability);

    record ModerationResult(boolean allowed, String reason) {}
}
