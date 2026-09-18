package com.digitalnest.petmemorial.media;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaLifecycleIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };
    private static final byte[] MP4 = new byte[]{
            0x00, 0x00, 0x00, 0x10, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaService mediaService;

    @Test
    void deletingANonCoverGalleryPhotoImmediatelyRevokesItThenPurgesTheStoredFileLater() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"media-lifecycle@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"媒体测试用户\"}"))
                .andExpect(status().isCreated());

        String coverMediaId = upload(session, csrfCookie, csrfToken, "cover.png", PNG);
        String memorialId = mockMvc.perform(post("/api/v1/memorials")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"团子\",\"species\":\"小猫\",\"coverMediaId\":\"" + coverMediaId
                                + "\",\"farewellMessage\":\"谢谢你一直在。\",\"visibility\":\"PRIVATE\",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        String removableMediaId = upload(session, csrfCookie, csrfToken, "removable.png", PNG);
        String galleryItemId = mockMvc.perform(post("/api/v1/memorials/{id}/gallery", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + removableMediaId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String filename = jdbcTemplate.queryForObject(
                "SELECT storage_filename FROM media_assets WHERE id = ?", String.class, UUID.fromString(removableMediaId));
        Path storedFile = Path.of("target/test-uploads").toAbsolutePath().resolve(filename);
        assertTrue(Files.isRegularFile(storedFile));

        mockMvc.perform(delete("/api/v1/memorials/{id}/gallery/{galleryItemId}", memorialId, galleryItemId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/media/{id}/content", removableMediaId).session(session))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE id = ?", Integer.class, UUID.fromString(removableMediaId)));
        assertEquals(filename, jdbcTemplate.queryForObject(
                "SELECT storage_filename FROM media_deletion_queue", String.class));
        assertTrue(Files.isRegularFile(storedFile));

        mediaService.purgeDueFiles(OffsetDateTime.now().plusHours(25));

        assertFalse(Files.exists(storedFile));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_deletion_queue", Integer.class));
    }

    @Test
    void validatesShortVideosStreamsRangesAndEnforcesTheServerOwnedVideoLimit() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"video-lifecycle@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"短视频测试用户\"}"))
                .andExpect(status().isCreated());

        String videoId = mockMvc.perform(multipart("/api/v1/media/videos")
                        .file(new MockMultipartFile("file", "first-memory.mp4", "video/mp4", MP4))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("video/mp4"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/media/{id}/content", videoId)
                        .session(session)
                        .header("Range", "bytes=0-7"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 0-7/12"));
        mockMvc.perform(get("/api/v1/media/{id}/content", videoId)
                        .session(session)
                        .header("Range", "bytes=64-128"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.data.code").value("MEDIA_RANGE_NOT_SATISFIABLE"));

        mockMvc.perform(post("/api/v1/memorials")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"视频团子\",\"species\":\"小猫\",\"coverMediaId\":\"" + videoId
                                + "\",\"visibility\":\"PRIVATE\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("COVER_IMAGE_REQUIRED"));

        String coverMediaId = upload(session, csrfCookie, csrfToken, "cover.png", PNG);
        String memorialId = mockMvc.perform(post("/api/v1/memorials")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"视频团子\",\"species\":\"小猫\",\"coverMediaId\":\"" + coverMediaId
                                + "\",\"visibility\":\"PRIVATE\",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/memorials/{id}/timeline", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + videoId
                                + "\",\"datePrecision\":\"UNKNOWN\",\"title\":\"不应关联短视频\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("TIMELINE_IMAGE_REQUIRED"));

        mockMvc.perform(post("/api/v1/memorials/{id}/gallery", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + videoId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("PLAN_SHORT_VIDEO_LIMIT_REACHED"));

        String orderId = mockMvc.perform(post("/api/v1/billing/orders")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", "video-guardian-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"GUARDIAN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/v1/billing/orders/{orderId}/mock-pay", orderId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/memorials/{id}/gallery", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + videoId + "\",\"caption\":\"第一次听见团子呼噜的短片\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.data.mediaUrl").value(org.hamcrest.Matchers.containsString("/content")));

        MvcResult exportLink = mockMvc.perform(post("/api/v1/auth/export-links")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn();
        String exportUrl = exportLink.getResponse().getContentAsString()
                .replaceAll(".*\\\"downloadUrl\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String exportToken = exportUrl.substring(exportUrl.lastIndexOf('/') + 1);
        MvcResult export = mockMvc.perform(get("/api/v1/auth/export-download/{token}", exportToken).session(session))
                .andExpect(status().isOk())
                .andReturn();
        assertExportContainsVideo(export.getResponse().getContentAsByteArray(), videoId);
    }

    private void assertExportContainsVideo(byte[] archive, String videoId) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (("videos/" + videoId + ".mp4").equals(entry.getName())) {
                    assertArrayEquals(MP4, zip.readAllBytes());
                    return;
                }
            }
        }
        throw new AssertionError("Export did not contain the uploaded short video.");
    }

    private String upload(MockHttpSession session, Cookie csrfCookie, String csrfToken, String name, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, content))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
