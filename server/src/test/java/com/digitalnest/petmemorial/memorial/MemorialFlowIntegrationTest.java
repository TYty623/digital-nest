package com.digitalnest.petmemorial.memorial;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemorialFlowIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void changingAPublishedMemorialToPrivateImmediatelyArchivesIt() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession session = new MockHttpSession();
        String email = "private-transition@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-long-enough-password\",\"displayName\":\"隐私所有者\"}"))
                .andExpect(status().isCreated());

        UUID ownerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        UUID memorialId = UUID.randomUUID();
        String slug = "private-transition-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility, theme, version, published_at)
                        VALUES (?, ?, ?, '小黑', '小狗', 'PUBLISHED', 'LINK', 'SUNNY', 0, CURRENT_TIMESTAMP)
                        """, memorialId, ownerId, slug);

        mockMvc.perform(put("/api/v1/memorials/{id}", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"小黑\",\"species\":\"小狗\",\"visibility\":\"PRIVATE\",\"theme\":\"SUNNY\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicVisitsAreStoredAsOneDailyAnonymousRecord() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memorialId = UUID.randomUUID();
        String slug = "visit-test-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO users (id, email, password_hash, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """, ownerId, "visit-owner-" + UUID.randomUUID() + "@example.com", "not-used", "访问统计主人");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'USER')", ownerId);
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility)
                        VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC')
                        """, memorialId, ownerId, slug, "访问测试小猫", "小猫");

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug)).andExpect(status().isOk());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memorial_visit_events WHERE memorial_id = ?", Integer.class, memorialId));
        String fingerprint = jdbcTemplate.queryForObject(
                "SELECT visitor_fingerprint FROM memorial_visit_events WHERE memorial_id = ?", String.class, memorialId);
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertNotEquals("127.0.0.1", fingerprint);
    }

    @Test
    void dynamicShareMetadataEscapesMemorialText() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String slug = "escaped-share-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO users (id, email, password_hash, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """, ownerId, "escaped-owner-" + UUID.randomUUID() + "@example.com", "not-used", "分享页主人");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'USER')", ownerId);
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, farewell_message, status, visibility)
                        VALUES (?, ?, ?, ?, ?, ?, 'PUBLISHED', 'LINK')
                        """, UUID.randomUUID(), ownerId, slug, "<script>alert(1)</script>", "小猫", "\"<script>alert(2)</script>");

        MvcResult page = mockMvc.perform(get("/m/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();
        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("&quot;&lt;script&gt;alert(2)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertFalse(html.contains("<script>alert(2)</script>"));
    }

    @Test
    void ownerCanRecordOnlyPublishedShareEvents() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession session = new MockHttpSession();
        String email = "share-owner@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-long-enough-password\",\"displayName\":\"分享主人\"}"))
                .andExpect(status().isCreated());

        UUID ownerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        UUID publishedId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility, published_at)
                        VALUES (?, ?, ?, '分享小猫', '小猫', 'PUBLISHED', 'LINK', CURRENT_TIMESTAMP)
                        """, publishedId, ownerId, "published-share-" + UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility)
                        VALUES (?, ?, ?, '草稿小猫', '小猫', 'DRAFT', 'PRIVATE')
                        """, draftId, ownerId, "draft-share-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/memorials/{id}/share-events", publishedId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LINK_COPIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recorded").value(true));
        mockMvc.perform(post("/api/v1/memorials/{id}/share-events", publishedId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SHARE_CARD_DOWNLOADED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/memorials/{id}/share-events", draftId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LINK_COPIED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_PUBLISHED"));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memorial_share_events WHERE memorial_id = ?", Integer.class, publishedId));
    }

    @Test
    void ownerCanCreateAndPublishMemorialThenVisitorsCanLeaveTributes() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession session = new MockHttpSession();
        String anonymousSessionId = session.getId();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"小麦的家人\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("friend@example.com"));
        assertNotEquals(anonymousSessionId, session.getId());

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", "maimai.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.containsString("/content")))
                .andReturn();
        String mediaId = uploaded.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult created = mockMvc.perform(post("/api/v1/memorials")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"小麦\",\"species\":\"小狗\",\"coverMediaId\":\"" + mediaId + "\",\"farewellMessage\":\"谢谢你等我回家。\",\"visibility\":\"PRIVATE\",\"version\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.theme").value("NIGHT"))
                .andExpect(jsonPath("$.data.slug").value(org.hamcrest.Matchers.startsWith("m-")))
                .andReturn();
        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String slug = response.replaceAll(".*\\\"slug\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult secondUpload = mockMvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", "walk.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andReturn();
        String secondMediaId = secondUpload.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        MvcResult secondGallery = mockMvc.perform(post("/api/v1/memorials/{id}/gallery", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + secondMediaId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secondGalleryItemId = secondGallery.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult thirdUpload = mockMvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", "sleep.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andReturn();
        String thirdMediaId = thirdUpload.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        MvcResult thirdGallery = mockMvc.perform(post("/api/v1/memorials/{id}/gallery", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + thirdMediaId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String thirdGalleryItemId = thirdGallery.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult galleryList = mockMvc.perform(get("/api/v1/memorials/{id}/gallery", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();
        String coverGalleryItemId = galleryList.getResponse().getContentAsString()
                .replaceFirst(".*?\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(put("/api/v1/memorials/{id}/gallery/{galleryItemId}", id, secondGalleryItemId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"散步时总会回头等我\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caption").value("散步时总会回头等我"));

        mockMvc.perform(put("/api/v1/memorials/{id}/gallery/order", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[\"" + thirdGalleryItemId + "\",\"" + secondGalleryItemId + "\",\"" + coverGalleryItemId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mediaId").value(thirdMediaId))
                .andExpect(jsonPath("$.data[1].caption").value("散步时总会回头等我"));

        mockMvc.perform(get("/api/v1/media/{id}/content", mediaId))
                .andExpect(status().isNotFound());

        MvcResult firstTimeline = mockMvc.perform(post("/api/v1/memorials/{id}/timeline", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + secondMediaId + "\",\"eventDate\":\"2020-06-01\",\"datePrecision\":\"DAY\",\"title\":\"第一次回家\",\"body\":\"我们一起走进了家门。\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("第一次回家"))
                .andExpect(jsonPath("$.data.mediaId").value(secondMediaId))
                .andReturn();
        String firstTimelineId = firstTimeline.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult secondTimeline = mockMvc.perform(post("/api/v1/memorials/{id}/timeline", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datePrecision\":\"UNKNOWN\",\"title\":\"午后的散步\",\"body\":\"风吹过耳朵。\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secondTimelineId = secondTimeline.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(put("/api/v1/memorials/{id}/timeline/order", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryIds\":[\"" + firstTimelineId + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("TIMELINE_ORDER_INVALID"));

        mockMvc.perform(put("/api/v1/memorials/{id}/timeline/order", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryIds\":[\"" + secondTimelineId + "\",\"" + firstTimelineId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondTimelineId))
                .andExpect(jsonPath("$.data[1].mediaId").value(secondMediaId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/memorials/{id}/gallery/{galleryItemId}", id, secondGalleryItemId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("TIMELINE_GALLERY_ITEM_REQUIRED"));

        mockMvc.perform(put("/api/v1/memorials/{id}/letter", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"谢谢你\",\"body\":\"谢谢你一直等我回家。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("谢谢你"));

        mockMvc.perform(put("/api/v1/memorials/{id}", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"小麦\",\"species\":\"小狗\",\"coverMediaId\":\"" + mediaId + "\",\"farewellMessage\":\"谢谢你等我回家。\",\"aboutTa\":\"最喜欢叼着玩具在门口等人，也总会把肚皮翻出来。\",\"companionStartedOn\":\"2021-06-01\",\"companionEndedOn\":\"2025-01-01\",\"visibility\":\"PUBLIC\",\"theme\":\"NIGHT\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.theme").value("NIGHT"));

        mockMvc.perform(put("/api/v1/memorials/{id}", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"小麦\",\"species\":\"小狗\",\"coverMediaId\":\"" + mediaId + "\",\"farewellMessage\":\"旧窗口仍在编辑。\",\"visibility\":\"PUBLIC\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_VERSION_CONFLICT"));

        MvcResult published = mockMvc.perform(post("/api/v1/memorials/{id}/publish", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andReturn();
        String publishedAt = published.getResponse().getContentAsString()
                .replaceAll(".*\\\"publishedAt\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(put("/api/v1/memorials/{id}", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"小麦\",\"species\":\"小狗\",\"coverMediaId\":\"" + mediaId + "\",\"farewellMessage\":\"发布后也能继续整理。\",\"aboutTa\":\"最喜欢叼着玩具在门口等人，也总会把肚皮翻出来。\",\"companionStartedOn\":\"2021-06-01\",\"companionEndedOn\":\"2025-01-01\",\"visibility\":\"PUBLIC\",\"theme\":\"NIGHT\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedAt").value(publishedAt));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memorial.petName").value("小麦"))
                .andExpect(jsonPath("$.data.memorial.theme").value("NIGHT"))
                .andExpect(jsonPath("$.data.memorial.aboutTa").value("最喜欢叼着玩具在门口等人，也总会把肚皮翻出来。"))
                .andExpect(jsonPath("$.data.memorial.companionStartedOn").value("2021-06-01"))
                .andExpect(jsonPath("$.data.memorial.companionEndedOn").value("2025-01-01"))
                .andExpect(jsonPath("$.data.timelineEntries[0].title").value("午后的散步"))
                .andExpect(jsonPath("$.data.timelineEntries[1].title").value("第一次回家"))
                .andExpect(jsonPath("$.data.timelineEntries[1].mediaId").value(secondMediaId))
                .andExpect(jsonPath("$.data.letter.subject").value("谢谢你"))
                .andExpect(jsonPath("$.data.galleryItems.length()").value(3))
                .andExpect(jsonPath("$.data.galleryItems[0].mediaId").value(thirdMediaId))
                .andExpect(jsonPath("$.data.galleryItems[1].caption").value("散步时总会回头等我"));

        mockMvc.perform(get("/m/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>纪念小麦 | 数字小窝</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("property=\"og:title\" content=\"纪念小麦 | 数字小窝\"")));

        mockMvc.perform(post("/api/v1/memorials/public/{slug}/lights", slug)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lit").value(true))
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/v1/memorials/public/{slug}/lights", slug)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lit").value(false))
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(get("/api/v1/media/{id}/content", mediaId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(get("/api/v1/media/{id}/content", secondMediaId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));

        MvcResult tribute = mockMvc.perform(post("/api/v1/memorials/public/{slug}/tributes", slug)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"阿姨\",\"message\":\"永远想念你。\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorName").value("阿姨"))
                .andReturn();
        String tributeId = tribute.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/memorials/public/{slug}/tributes/{tributeId}/report", slug, tributeId)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reported").value(true));

        mockMvc.perform(get("/api/v1/memorials/{id}/tributes", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REPORTED"));

        MvcResult pendingTribute = mockMvc.perform(post("/api/v1/memorials/public/{slug}/tributes", slug)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"陌生访客\",\"message\":\"请告诉我怎么赌博\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String pendingTributeId = pendingTribute.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tributes.length()").value(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/v1/memorials/{id}/tributes/{tributeId}", id, pendingTributeId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tributes.length()").value(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/v1/memorials/{id}/tributes/{tributeId}", id, tributeId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(post("/api/v1/memorials/{id}/archive", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/memorials/{id}/restore", id)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isOk());

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
        String exportManifest = zipEntry(export.getResponse().getContentAsByteArray(), "digital-nest-export.json");
        String exportMarkdown = zipEntry(export.getResponse().getContentAsByteArray(), "digital-nest-export.md");
        assertTrue(exportManifest.contains("\"galleryItems\""));
        assertTrue(exportManifest.contains("散步时总会回头等我"));
        assertTrue(exportManifest.contains("\"HIDDEN\""));
        assertTrue(exportManifest.contains("\"APPROVED\""));
        assertTrue(exportMarkdown.contains("#### 相册"));
        assertTrue(exportMarkdown.contains("散步时总会回头等我"));
        assertTrue(exportMarkdown.contains("状态：HIDDEN"));
        assertTrue(exportMarkdown.contains("状态：APPROVED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/account")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduled").value(true));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/media/{id}/content", mediaId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/auth/account/deletion").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(true));

        mockMvc.perform(post("/api/v1/auth/account/deletion/cancel")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(false));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/media/{id}/content", mediaId))
                .andExpect(status().isOk());
    }

    @Test
    void passwordProtectedMemorialRequiresUnlockingInTheVisitorSession() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MockHttpSession ownerSession = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(ownerSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lock@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"豆包的家人\"}"))
                .andExpect(status().isCreated());

        String coverMediaId = uploadImage(ownerSession, csrfCookie, csrfToken, "cover-lock.png", PNG);

        MvcResult created = mockMvc.perform(post("/api/v1/memorials")
                        .session(ownerSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"豆包\",\"species\":\"小猫\",\"coverMediaId\":\"" + coverMediaId + "\",\"visibility\":\"PASSWORD\",\"accessCode\":\"shared-123\",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String slug = response.replaceAll(".*\\\"slug\\\":\\\"([^\\\"]+)\\\".*", "$1");

        addGalleryImage(id, ownerSession, csrfCookie, csrfToken, "lock-2.png", PNG);
        addGalleryImage(id, ownerSession, csrfCookie, csrfToken, "lock-3.png", PNG);

        mockMvc.perform(post("/api/v1/memorials/{id}/publish", id)
                        .session(ownerSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/m/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>数字小窝 | 宠物数字纪念</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("豆包"))));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_ACCESS_CODE_REQUIRED"));

        MockHttpSession visitorSession = new MockHttpSession();
        mockMvc.perform(post("/api/v1/memorials/public/{slug}/unlock", slug)
                        .session(visitorSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\":\"shared-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granted").value(true));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", slug).session(visitorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memorial.petName").value("豆包"));
    }

    private String uploadImage(MockHttpSession session, Cookie csrfCookie, String csrfToken, String filename, byte[] content) throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/media/images")
                        .file(new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, content))
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isCreated())
                .andReturn();
        return uploaded.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private void addGalleryImage(String memorialId, MockHttpSession session, Cookie csrfCookie, String csrfToken, String filename, byte[] content) throws Exception {
        String mediaId = uploadImage(session, csrfCookie, csrfToken, filename, content);
        mockMvc.perform(post("/api/v1/memorials/{id}/gallery", memorialId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + mediaId + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void accountOwnerCanExportRequestDeletionAndCancelWithinTheRevokeWindow() throws Exception {
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
                        .content("{\"email\":\"export@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"资料所有者\"}"))
                .andExpect(status().isCreated());

        MvcResult exportLink = mockMvc.perform(post("/api/v1/auth/export-links")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andReturn();
        String exportUrl = exportLink.getResponse().getContentAsString()
                .replaceAll(".*\\\"downloadUrl\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String exportToken = exportUrl.substring(exportUrl.lastIndexOf('/') + 1);

        MockHttpSession anotherSession = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/register")
                        .session(anotherSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other-export@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"其他账户\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/auth/export-download/{token}", exportToken).session(anotherSession))
                .andExpect(status().isGone());

        MvcResult export = mockMvc.perform(get("/api/v1/auth/export-download/{token}", exportToken).session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("digital-nest-export.zip")))
                .andReturn();
        Set<String> exportedEntries = zipEntries(export.getResponse().getContentAsByteArray());
        assertTrue(exportedEntries.contains("digital-nest-export.json"));
        assertTrue(exportedEntries.contains("digital-nest-export.md"));
        assertTrue(exportedEntries.contains("images/manifest.json"));
        mockMvc.perform(get("/api/v1/auth/export-download/{token}", exportToken).session(session))
                .andExpect(status().isGone());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/account")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduled").value(true))
                .andExpect(jsonPath("$.data.scheduledFor").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/account/deletion").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(true));
        mockMvc.perform(post("/api/v1/auth/account/deletion/cancel")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(false));
    }

    @Test
    void accountOwnerCannotCancelDeletionAfterTheRevokeWindowEnds() throws Exception {
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
                        .content("{\"email\":\"expired-delete@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"到期账户\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/account")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                        UPDATE account_deletion_requests
                        SET scheduled_for = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP)
                        WHERE user_id = (SELECT id FROM users WHERE email = ?)
                        """, "expired-delete@example.com");

        mockMvc.perform(post("/api/v1/auth/account/deletion/cancel")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("DELETION_REVOKE_WINDOW_EXPIRED"));
    }

    private Set<String> zipEntries(byte[] archive) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private String zipEntry(byte[] archive, String targetEntry) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (targetEntry.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("未在导出包中找到 " + targetEntry);
    }
}
