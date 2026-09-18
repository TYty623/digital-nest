package com.digitalnest.petmemorial.journey;

import java.util.List;

public record YearbookPreview(
        String petName,
        int year,
        List<YearbookChapter> chapters,
        boolean exportAvailable,
        String exportNotice
) {
    public record YearbookChapter(String kind, String title, String body, String dateLabel) {
    }
}
