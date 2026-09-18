package com.digitalnest.petmemorial.account;

import com.digitalnest.petmemorial.media.MediaAsset;
import com.digitalnest.petmemorial.media.MediaService;
import com.digitalnest.petmemorial.memorial.Memorial;
import com.digitalnest.petmemorial.memorial.MemorialLetter;
import com.digitalnest.petmemorial.memorial.MemorialRepository;
import com.digitalnest.petmemorial.memorial.MemorialExperience;
import com.digitalnest.petmemorial.memorial.MemorialExperienceRepository;
import com.digitalnest.petmemorial.memorial.TimelineEntry;
import com.digitalnest.petmemorial.memorial.TributeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccountDataService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final AccountDeletionRepository accountDeletionRepository;
    private final AccountExportTokenRepository accountExportTokenRepository;
    private final MemorialRepository memorialRepository;
    private final MemorialExperienceRepository memorialExperienceRepository;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;
    private final int deletionRevokeDays;
    private final int exportLinkTtlMinutes;

    public AccountDataService(
            AccountRepository accountRepository,
            AccountDeletionRepository accountDeletionRepository,
            AccountExportTokenRepository accountExportTokenRepository,
            MemorialRepository memorialRepository,
            MemorialExperienceRepository memorialExperienceRepository,
            MediaService mediaService,
            ObjectMapper objectMapper,
            @Value("${app.privacy.deletion-revoke-days:7}") int deletionRevokeDays,
            @Value("${app.privacy.export-link-ttl-minutes:15}") int exportLinkTtlMinutes
    ) {
        this.accountRepository = accountRepository;
        this.accountDeletionRepository = accountDeletionRepository;
        this.accountExportTokenRepository = accountExportTokenRepository;
        this.memorialRepository = memorialRepository;
        this.memorialExperienceRepository = memorialExperienceRepository;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
        this.deletionRevokeDays = deletionRevokeDays;
        this.exportLinkTtlMinutes = exportLinkTtlMinutes;
    }

    public ExportDownloadLink createExportDownloadLink(UUID userId) {
        requireAccount(userId);
        OffsetDateTime now = OffsetDateTime.now();
        String token = newToken();
        OffsetDateTime expiresAt = now.plusMinutes(exportLinkTtlMinutes);
        accountExportTokenRepository.deleteExpiredBefore(now.minus(1, ChronoUnit.DAYS));
        accountExportTokenRepository.issue(UUID.randomUUID(), userId, hashToken(token), expiresAt);
        return new ExportDownloadLink(token, expiresAt);
    }

    @Transactional
    public byte[] exportForDownload(UUID userId, String token) {
        if (token == null || token.length() > 128
                || !accountExportTokenRepository.consume(userId, hashToken(token), OffsetDateTime.now())) {
            throw new com.digitalnest.petmemorial.shared.error.ApiException(
                    HttpStatus.GONE, "EXPORT_DOWNLOAD_LINK_INVALID", "这个资料包下载链接已使用或已过期，请重新生成。"
            );
        }
        return export(userId);
    }

    public byte[] export(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new com.digitalnest.petmemorial.shared.error.ApiException(
                        HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "没有找到这个账户。"));
        List<Memorial> memorials = memorialRepository.findAllByUserId(userId);
        List<MediaAsset> assets = mediaService.listOwned(userId);
        List<ExportMediaAsset> mediaManifest = assets.stream()
                .map(asset -> new ExportMediaAsset(
                        asset.id(),
                        archivePathFor(asset),
                        asset.contentType(),
                        asset.byteSize(),
                        asset.createdAt()
                ))
                .toList();
        Map<UUID, String> archivePathsByMediaId = mediaManifest.stream()
                .collect(java.util.stream.Collectors.toMap(ExportMediaAsset::id, ExportMediaAsset::archivePath));
        List<ExportMemorial> exportMemorials = memorials.stream()
                .map(memorial -> new ExportMemorial(
                        memorial.id(), memorial.slug(), memorial.petName(), memorial.species(), memorial.coverMediaId(),
                        memorial.farewellMessage(), memorial.aboutTa(), memorial.companionStartedOn(), memorial.companionEndedOn(),
                        memorial.status(), memorial.visibility(), memorial.publishedAt(),
                        memorial.createdAt(), memorial.updatedAt(), memorialRepository.findTimelineEntries(memorial.id()),
                        memorialRepository.findLetter(memorial.id()).orElse(null), memorialRepository.findAllTributes(memorial.id()),
                        memorialRepository.findGalleryItems(memorial.id()).stream()
                                .map(item -> new ExportGalleryItem(
                                        item.id(), item.mediaId(), archivePathsByMediaId.get(item.mediaId()), item.caption(),
                                        item.mediaContentType(), item.position(), item.createdAt()))
                                .toList(),
                        new MemorialExperience(
                                memorialExperienceRepository.dayMoments(memorial.id()),
                                memorialExperienceRepository.lifeDetails(memorial.id()),
                                memorialExperienceRepository.sounds(memorial.id()),
                                memorialExperienceRepository.interviews(memorial.id()),
                                memorialExperienceRepository.keepsakes(memorial.id()),
                                memorialExperienceRepository.burial(memorial.id()).orElse(null))
                ))
                .toList();
        ExportManifest manifest = new ExportManifest(
                OffsetDateTime.now(), new ExportAccount(account.id(), account.email(), account.displayName()), exportMemorials);

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeJsonEntry(zip, "digital-nest-export.json", manifest);
            writeTextEntry(zip, "digital-nest-export.md", readableMarkdown(manifest, mediaManifest, archivePathsByMediaId));
            writeJsonEntry(zip, "images/manifest.json", mediaManifest);
            for (MediaAsset asset : assets) {
                zip.putNextEntry(new ZipEntry(archivePathFor(asset)));
                Files.copy(mediaService.resolve(asset), zip);
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new com.digitalnest.petmemorial.shared.error.ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "资料包暂时无法生成，请稍后重试。");
        }
    }

    private void writeJsonEntry(ZipOutputStream zip, String filename, Object content) throws IOException {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(content));
        zip.closeEntry();
    }

    private void writeTextEntry(ZipOutputStream zip, String filename, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String readableMarkdown(
            ExportManifest manifest,
            List<ExportMediaAsset> mediaManifest,
            Map<UUID, String> archivePathsByMediaId
    ) {
        StringBuilder markdown = new StringBuilder("# 数字小窝资料导出\n\n");
        markdown.append("导出时间：").append(manifest.exportedAt()).append("\n\n");
        markdown.append("## 账户\n\n")
                .append("- 邮箱：").append(display(manifest.account().email())).append("\n")
                .append("- 昵称：").append(display(manifest.account().displayName())).append("\n\n");
        markdown.append("## 纪念页\n\n");
        if (manifest.memorials().isEmpty()) {
            markdown.append("暂无纪念页。\n");
        }
        for (ExportMemorial memorial : manifest.memorials()) {
            markdown.append("### ").append(display(memorial.petName())).append("\n\n")
                    .append("- 物种：").append(display(memorial.species())).append("\n")
                    .append("- 地址标识：").append(memorial.slug()).append("\n")
                    .append("- 状态：").append(memorial.status()).append("\n")
                    .append("- 可见范围：").append(memorial.visibility()).append("\n")
                    .append("- 告别语：").append(display(memorial.farewellMessage())).append("\n")
                    .append("- 关于 TA：").append(display(memorial.aboutTa())).append("\n")
                    .append("- 陪伴日期：").append(companionDateRange(memorial.companionStartedOn(), memorial.companionEndedOn())).append("\n\n");
            if (!memorial.timelineEntries().isEmpty()) {
                markdown.append("#### 时间线\n\n");
                for (TimelineEntry entry : memorial.timelineEntries()) {
                    markdown.append("- ").append(display(entry.title()))
                            .append("（").append(display(entry.eventDate() == null ? null : entry.eventDate().toString())).append("）：")
                            .append(display(entry.body()));
                    if (entry.mediaId() != null) {
                        markdown.append("（关联图片：`")
                                .append(display(archivePathsByMediaId.get(entry.mediaId())))
                                .append("`）");
                    }
                    markdown.append("\n");
                }
                markdown.append("\n");
            }
            if (memorial.letter() != null) {
                markdown.append("#### 写给 TA 的信\n\n")
                        .append("**").append(display(memorial.letter().subject())).append("**\n\n")
                        .append(display(memorial.letter().body())).append("\n\n");
            }
            if (!memorial.galleryItems().isEmpty()) {
                markdown.append("#### 相册\n\n");
                for (ExportGalleryItem item : memorial.galleryItems()) {
                    markdown.append("- ").append(item.position() + 1).append(". `")
                            .append(display(item.archivePath())).append("`：")
                            .append(display(item.caption())).append("（")
                            .append(mediaLabel(item.contentType())).append("）\n");
                }
                markdown.append("\n");
            }
            if (!memorial.tributes().isEmpty()) {
                markdown.append("#### 亲友留言\n\n");
                for (TributeMessage tribute : memorial.tributes()) {
                    markdown.append("- ").append(display(tribute.authorName())).append("：")
                            .append(display(tribute.message())).append("（状态：")
                            .append(display(tribute.status())).append("）\n");
                }
                markdown.append("\n");
            }
            if (!memorial.experience().sounds().isEmpty()) {
                markdown.append("#### 声音记忆盒\n\n");
                for (MemorialExperience.SoundMemory sound : memorial.experience().sounds()) {
                    markdown.append("- ").append(display(sound.title())).append("：")
                            .append(display(sound.story())).append("（文件：`")
                            .append(display(archivePathsByMediaId.get(sound.mediaId()))).append("`）\n");
                }
                markdown.append("\n");
            }
            if (!memorial.experience().interviewAnswers().isEmpty()) {
                markdown.append("#### 回忆采访\n\n");
                for (MemorialExperience.InterviewAnswer answer : memorial.experience().interviewAnswers())
                    markdown.append("- ").append(answer.promptKey()).append("：").append(display(answer.answer())).append("\n");
                markdown.append("\n");
            }
            if (!memorial.experience().keepsakes().isEmpty()) {
                markdown.append("#### 旧物故事\n\n");
                for (MemorialExperience.Keepsake item : memorial.experience().keepsakes())
                    markdown.append("- ").append(display(item.title())).append("：").append(display(item.story())).append("\n");
                markdown.append("\n");
            }
            if (memorial.experience().burial() != null) {
                MemorialExperience.BurialRecord burial = memorial.experience().burial();
                markdown.append("#### 安葬与归处\n\n- 方式：").append(burial.dispositionType())
                        .append("\n- 日期：").append(display(burial.occurredOn() == null ? null : burial.occurredOn().toString()))
                        .append("\n- 地区：").append(display(burial.region()))
                        .append("\n- 场所称呼：").append(display(burial.placeName()))
                        .append("\n- 纪念文字：").append(display(burial.remembranceText()))
                        .append("\n- 审核状态：").append(burial.reviewStatus()).append("\n\n");
            }
        }
        markdown.append("## 原始媒体\n\n");
        if (mediaManifest.isEmpty()) {
            markdown.append("暂无媒体文件。\n");
        }
        for (ExportMediaAsset asset : mediaManifest) {
            markdown.append("- `").append(asset.archivePath()).append("`（")
                    .append(asset.contentType()).append("，").append(asset.byteSize()).append(" 字节）\n");
        }
        return markdown.toString();
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "（未填写）" : value;
    }

    private String companionDateRange(java.time.LocalDate startedOn, java.time.LocalDate endedOn) {
        if (startedOn == null && endedOn == null) {
            return "（未填写）";
        }
        return display(startedOn == null ? null : startedOn.toString()) + " 至 "
                + display(endedOn == null ? null : endedOn.toString());
    }

    public AccountDeletionRequest requestDeletion(UUID userId) {
        requireAccount(userId);
        return accountDeletionRepository.request(userId, OffsetDateTime.now().plusDays(deletionRevokeDays));
    }

    public AccountDeletionRequest deletionStatus(UUID userId) {
        requireAccount(userId);
        return accountDeletionRepository.findByUserId(userId).orElse(null);
    }

    public void cancelDeletion(UUID userId) {
        requireAccount(userId);
        if (!accountDeletionRepository.cancelBeforeDue(userId, OffsetDateTime.now())) {
            throw new com.digitalnest.petmemorial.shared.error.ApiException(
                    HttpStatus.CONFLICT,
                    "DELETION_REVOKE_WINDOW_EXPIRED",
                    "删除撤销期已结束，账户正在等待永久清理。"
            );
        }
    }

    @Transactional
    public void purgeDueAccount(UUID userId, OffsetDateTime now) {
        if (!accountDeletionRepository.consumeDue(userId, now)) {
            return;
        }
        permanentlyDeleteAccount(userId);
    }

    private void permanentlyDeleteAccount(UUID userId) {
        List<MediaAsset> assets = mediaService.listOwned(userId);
        if (!accountRepository.deleteById(userId)) {
            return;
        }
        for (MediaAsset asset : assets) {
            mediaService.deleteStored(asset);
        }
    }

    private UserAccount requireAccount(UUID userId) {
        return accountRepository.findById(userId)
                .orElseThrow(() -> new com.digitalnest.petmemorial.shared.error.ApiException(
                        HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "没有找到这个账户。"));
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> ".bin";
        };
    }

    private String archivePathFor(MediaAsset asset) {
        String directory = asset.isVideo() ? "videos/" : asset.isAudio() ? "audio/" : "images/";
        return directory + asset.id() + extensionFor(asset.contentType());
    }

    private String mediaLabel(String contentType) {
        return contentType.startsWith("video/") ? "短视频" : "图片";
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Objects.requireNonNullElse(token, "").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    public record ExportManifest(OffsetDateTime exportedAt, ExportAccount account, List<ExportMemorial> memorials) {
    }

    public record ExportDownloadLink(String token, OffsetDateTime expiresAt) {
    }

    public record ExportAccount(UUID id, String email, String displayName) {
    }

    public record ExportMediaAsset(
            UUID id,
            String archivePath,
            String contentType,
            long byteSize,
            OffsetDateTime createdAt
    ) {
    }

    public record ExportMemorial(
            UUID id,
            String slug,
            String petName,
            String species,
            UUID coverMediaId,
            String farewellMessage,
            String aboutTa,
            java.time.LocalDate companionStartedOn,
            java.time.LocalDate companionEndedOn,
            String status,
            String visibility,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<TimelineEntry> timelineEntries,
            MemorialLetter letter,
            List<TributeMessage> tributes,
            List<ExportGalleryItem> galleryItems,
            MemorialExperience experience
    ) {
    }

    public record ExportGalleryItem(
            UUID id,
            UUID mediaId,
            String archivePath,
            String caption,
            String contentType,
            int position,
            OffsetDateTime createdAt
    ) {
    }
}
