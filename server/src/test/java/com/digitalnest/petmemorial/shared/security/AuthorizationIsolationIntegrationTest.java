package com.digitalnest.petmemorial.shared.security;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unauthenticatedAccountRequestReturnsActionableJson() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void userCannotReadOrMutateAnotherUsersMemorialOrderOrServiceRequest() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();

        RegisteredAccount owner = register("isolation-owner@example.com", "主人", csrfCookie, csrfToken);
        RegisteredAccount visitor = register("isolation-visitor@example.com", "另一位用户", csrfCookie, csrfToken);
        UUID memorialId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID serviceRequestId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, status, visibility)
                        VALUES (?, ?, ?, ?, ?, 'DRAFT', 'PRIVATE')
                        """, memorialId, owner.id(), "m-isolation-test", "私密小猫", "小猫");
        jdbcTemplate.update("""
                        INSERT INTO billing_orders (
                            id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                            timeline_limit, theme_limit, hosted_years, status, idempotency_key
                        ) VALUES (?, ?, 'GUARDIAN', '温暖守护', 9900, 'CNY', 80, 3, 30, 6, 3, 'PENDING', ?)
                        """, orderId, owner.id(), "isolation-key-" + orderId);
        jdbcTemplate.update("""
                        INSERT INTO custom_service_requests (id, user_id, contact_details, request_details, status)
                        VALUES (?, ?, ?, ?, 'DELIVERED')
                        """, serviceRequestId, owner.id(), "owner@example.com", "这是只属于主人的定制服务申请。" );

        mockMvc.perform(get("/api/v1/memorials/{id}", memorialId).session(visitor.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/memorials/{id}", memorialId)
                        .session(visitor.session())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petName\":\"被篡改\",\"species\":\"小猫\",\"visibility\":\"PRIVATE\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/billing/orders").session(visitor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/api/v1/billing/orders/{id}/mock-pay", orderId)
                        .session(visitor.session())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("BILLING_ORDER_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/custom-service-requests").session(visitor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/api/v1/custom-service-requests/{id}/confirm-delivery", serviceRequestId)
                        .session(visitor.session())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("CUSTOM_SERVICE_REQUEST_NOT_FOUND"));
    }

    private RegisteredAccount register(String email, String displayName, Cookie csrfCookie, String csrfToken) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-long-enough-password\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new RegisteredAccount(UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")), session);
    }

    private record RegisteredAccount(UUID id, MockHttpSession session) {
    }
}
