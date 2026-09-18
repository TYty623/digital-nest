package com.digitalnest.petmemorial.admin;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.bootstrap-admin-emails=moderator@example.com")
class AdminModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyStaffCanModerateTributesAndEveryDecisionIsAudited() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memorialId = UUID.randomUUID();
        UUID tributeId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO users (id, email, password_hash, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """, ownerId, "owner-for-admin@example.com", "not-used", "纪念页主人");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'USER')", ownerId);
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility, published_at)
                        VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', CURRENT_TIMESTAMP)
                        """, memorialId, ownerId, "m-admin-test", "审核小猫", "小猫");
        jdbcTemplate.update("""
                        INSERT INTO memorial_share_events (id, memorial_id, owner_id, event_type)
                        VALUES (?, ?, ?, 'LINK_COPIED')
                        """, UUID.randomUUID(), memorialId, ownerId);
        jdbcTemplate.update("""
                        INSERT INTO media_assets (id, owner_id, storage_filename, content_type, byte_size)
                        VALUES (?, ?, ?, 'image/png', 1536)
                        """, UUID.randomUUID(), ownerId, "admin-storage-" + UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO tribute_messages (id, memorial_id, author_name, message, status)
                        VALUES (?, ?, ?, ?, 'PENDING')
                        """, tributeId, memorialId, "访客", "请人工确认这条留言。" );

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();

        MockHttpSession ordinarySession = register("ordinary@example.com", "普通用户", csrfCookie, csrfToken);
        mockMvc.perform(get("/api/v1/admin/overview").session(ordinarySession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/memorials").session(ordinarySession))
                .andExpect(status().isForbidden());

        MockHttpSession moderatorSession = register("moderator@example.com", "审核员", csrfCookie, csrfToken);
        mockMvc.perform(get("/api/v1/admin/overview").session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingTributes").value(1))
                .andExpect(jsonPath("$.data.publishedMemorialsLast7Days").value(1))
                .andExpect(jsonPath("$.data.shareEventsLast7Days").value(1))
                .andExpect(jsonPath("$.data.managedMediaBytes").value(1536));

        mockMvc.perform(get("/api/v1/admin/tributes")
                        .param("status", "PENDING")
                        .session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(tributeId.toString()))
                .andExpect(jsonPath("$.data[0].petName").value("审核小猫"));

        mockMvc.perform(patch("/api/v1/admin/tributes/{tributeId}", tributeId)
                        .session(moderatorSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\",\"reason\":\"留言内容需要进一步核验\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(get("/api/v1/admin/audit-logs").session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("TRIBUTE_HIDDEN"))
                .andExpect(jsonPath("$.data[0].reason").value("留言内容需要进一步核验"));

        mockMvc.perform(get("/api/v1/admin/memorials")
                        .param("status", "PUBLISHED")
                        .session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(memorialId.toString()))
                .andExpect(jsonPath("$.data[0].ownerDisplayName").value("纪念页主人"))
                .andExpect(jsonPath("$.data[0].ownerEmail").doesNotExist());

        mockMvc.perform(patch("/api/v1/admin/memorials/{memorialId}", memorialId)
                        .session(moderatorSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"公开页面需要进一步内容核验\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(patch("/api/v1/admin/memorials/{memorialId}", memorialId)
                        .session(moderatorSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"重复下线不应写入新审计记录\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_PUBLISHED"));

        mockMvc.perform(patch("/api/v1/admin/memorials/{memorialId}", UUID.randomUUID())
                        .session(moderatorSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不存在的页面不能下线\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/memorials/public/{slug}", "m-admin-test"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/audit-logs").session(moderatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("MEMORIAL_ARCHIVED"))
                .andExpect(jsonPath("$.data[0].reason").value("公开页面需要进一步内容核验"));
    }

    private MockHttpSession register(String email, String displayName, Cookie csrfCookie, String csrfToken) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-long-enough-password\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated());
        return session;
    }
}
