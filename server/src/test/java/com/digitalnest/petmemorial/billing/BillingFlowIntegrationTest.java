package com.digitalnest.petmemorial.billing;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.bootstrap-admin-emails=refund-staff@example.com")
class BillingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsServerPricedOrderAndGrantsExactlyOneEntitlementForRepeatedMockPayment() throws Exception {
        mockMvc.perform(get("/api/v1/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].code").value("GUARDIAN"))
                .andExpect(jsonPath("$.data[1].amountCents").value(9900))
                .andExpect(jsonPath("$.data[1].shortVideoLimit").value(3))
                .andExpect(jsonPath("$.data[1].checkoutAvailable").value(true));

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
                .content("{\"email\":\"billing@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"订单所有者\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/billing/limits").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoLimit").value(12))
                .andExpect(jsonPath("$.data.shortVideoLimit").value(0))
                .andExpect(jsonPath("$.data.timelineLimit").value(5));
        mockMvc.perform(get("/api/v1/billing/capabilities").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.themeCodes.length()").value(1))
                .andExpect(jsonPath("$.data.themeCodes[0]").value("NIGHT"))
                .andExpect(jsonPath("$.data.allThemeCollectionUnlocked").value(false));

        String key = "checkout-guardian-0001";
        MvcResult created = mockMvc.perform(post("/api/v1/billing/orders")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"GUARDIAN\",\"amountCents\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.planCode").value("GUARDIAN"))
                .andExpect(jsonPath("$.data.amountCents").value(9900))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String orderId = created.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/billing/orders")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"GUARDIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(post("/api/v1/billing/orders")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"TREASURE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/api/v1/billing/orders/{orderId}/mock-pay", orderId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("PAID"))
                .andExpect(jsonPath("$.data.entitlement.planCode").value("GUARDIAN"))
                .andExpect(jsonPath("$.data.newlyPaid").value(true));
        mockMvc.perform(get("/api/v1/billing/capabilities").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("GUARDIAN"))
                .andExpect(jsonPath("$.data.themeCodes.length()").value(6))
                .andExpect(jsonPath("$.data.allThemeCollectionUnlocked").value(true));

        mockMvc.perform(post("/api/v1/billing/orders/{orderId}/mock-pay", orderId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("PAID"))
                .andExpect(jsonPath("$.data.newlyPaid").value(false));

        mockMvc.perform(get("/api/v1/billing/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].amountCents").value(9900));
        mockMvc.perform(get("/api/v1/billing/entitlements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].photoLimit").value(80))
                .andExpect(jsonPath("$.data[0].shortVideoLimit").value(3))
                .andExpect(jsonPath("$.data[0].timelineLimit").value(30));
        mockMvc.perform(get("/api/v1/billing/limits").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoLimit").value(80))
                .andExpect(jsonPath("$.data.shortVideoLimit").value(3))
                .andExpect(jsonPath("$.data.timelineLimit").value(30));

        MockHttpSession staffSession = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/register")
                        .session(staffSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refund-staff@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"退款运营\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/orders/{orderId}/mock-refund", orderId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用户不再需要付费托管\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/orders/{orderId}/mock-refund", orderId)
                        .session(staffSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用户不再需要付费托管\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.entitlementStatus").value("REVOKED"))
                .andExpect(jsonPath("$.data.newlyRefunded").value(true));

        mockMvc.perform(post("/api/v1/admin/orders/{orderId}/mock-refund", orderId)
                        .session(staffSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"重复请求不会重复撤销权益\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newlyRefunded").value(false));

        mockMvc.perform(get("/api/v1/billing/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data[0].refundedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/billing/entitlements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.data[0].revokedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/orders").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data[0].refundedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/audit-logs").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("BILLING_ORDER_REFUNDED"))
                .andExpect(jsonPath("$.data[0].reason").value("用户不再需要付费托管"));
    }
}
