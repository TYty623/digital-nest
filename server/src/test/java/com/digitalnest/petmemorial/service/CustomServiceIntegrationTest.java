package com.digitalnest.petmemorial.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
@TestPropertySource(properties = "app.security.bootstrap-admin-emails=service-admin@example.com")
class CustomServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestIsPrivateToOwnerAndFollowsAuditedDeliveryWorkflow() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();

        MockHttpSession ownerSession = register("service-owner@example.com", "申请人", csrfCookie, csrfToken);
        MvcResult created = mockMvc.perform(post("/api/v1/custom-service-requests")
                        .session(ownerSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactDetails":"owner@example.com","requestDetails":"希望协助整理照片和故事，做成给家人看的纪念页。"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String requestId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/custom-service-requests").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contactDetails").value("owner@example.com"));
        mockMvc.perform(get("/api/v1/admin/custom-service-requests").session(ownerSession))
                .andExpect(status().isForbidden());

        MockHttpSession staffSession = register("service-admin@example.com", "运营", csrfCookie, csrfToken);
        mockMvc.perform(get("/api/v1/admin/custom-service-requests")
                        .session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(requestId))
                .andExpect(jsonPath("$.data[0].ownerEmail").value("service-owner@example.com"));

        updateByStaff(requestId, "MATERIALS_PENDING", false, 0, "请补充最想保留的几张照片。", "已确认服务范围", staffSession, csrfCookie, csrfToken);
        updateByStaff(requestId, "IN_PROGRESS", true, 0, "材料已齐，开始整理。", "材料已齐全，安排制作", staffSession, csrfCookie, csrfToken);
        updateByStaff(requestId, "REVISION", true, 3, "已进入第 3 轮修改，请先确认额外范围。", "第三轮修改超出原约定范围", staffSession, csrfCookie, csrfToken);
        updateByStaff(requestId, "DELIVERED", true, 3, "页面已交付，请确认。", "已交付并等待用户确认", staffSession, csrfCookie, csrfToken);

        mockMvc.perform(post("/api/v1/custom-service-requests/{requestId}/confirm-delivery", requestId)
                        .session(ownerSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.revisionCount").value(3));

        mockMvc.perform(get("/api/v1/admin/audit-logs").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("CUSTOM_SERVICE_UPDATED"))
                .andExpect(jsonPath("$.data[0].reason").value("已交付并等待用户确认"));
    }

    private void updateByStaff(
            String requestId,
            String status,
            boolean materialsReady,
            int revisionCount,
            String customerMessage,
            String reason,
            MockHttpSession session,
            Cookie csrfCookie,
            String csrfToken
    ) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/custom-service-requests/{requestId}", requestId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","materialsReady":%s,"assignee":"小林","dueDate":"2026-09-12","revisionCount":%d,"customerMessage":"%s","reason":"%s"}
                                """.formatted(status, materialsReady, revisionCount, customerMessage, reason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(status));
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
