package com.digitalnest.petmemorial.memorial;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MemorialExperience(
        List<DayMoment> dayMoments,
        List<LifeDetail> lifeDetails,
        List<SoundMemory> sounds,
        List<InterviewAnswer> interviewAnswers,
        List<Keepsake> keepsakes,
        BurialRecord burial
) {
    public record DayMoment(UUID id, String momentTime, String title, String placeName, String story,
                            UUID mediaId, String mediaUrl, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record LifeDetail(UUID id, String detailKey, String answer, OffsetDateTime updatedAt) {}

    public record SoundMemory(UUID id, UUID mediaId, String mediaUrl, String contentType, String title,
                              String story, int position, OffsetDateTime createdAt) {}

    public record InterviewAnswer(UUID id, String promptKey, String answer, OffsetDateTime updatedAt) {}

    public record Keepsake(UUID id, String title, String story, OffsetDateTime createdAt) {}

    public record BurialRecord(UUID id, UUID memorialId, String petName, String ownerDisplayName,
                               String dispositionType, LocalDate occurredOn, String region, String placeName,
                               String remembranceText, String reviewStatus, boolean complianceAttested,
                               String reviewNote, OffsetDateTime reviewedAt, OffsetDateTime updatedAt) {}
}
