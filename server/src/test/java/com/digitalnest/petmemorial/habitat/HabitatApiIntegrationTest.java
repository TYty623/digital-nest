package com.digitalnest.petmemorial.habitat;

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
class HabitatApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void habitatIsPrivateAndSupportsSettingsItemsAndTraceableNotes() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        RegisteredAccount owner = register("habitat-api-owner@example.com", "主人", csrfCookie, csrfToken);
        RegisteredAccount visitor = register("habitat-api-visitor@example.com", "访客", csrfCookie, csrfToken);
        UUID memorialId = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility) VALUES (?,?,?,?,?,'DRAFT','PRIVATE')",
                memorialId, owner.id(), "habitat-api-" + UUID.randomUUID(), "小满", "小狗");

        mockMvc.perform(put("/api/v1/memorials/{id}/habitat", memorialId).session(owner.session()).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"COMPANION\",\"title\":\"窗边旧时光\",\"light\":70}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scene").value("COMPANION"));
        mockMvc.perform(post("/api/v1/memorials/{id}/habitat/items", memorialId).session(owner.session()).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LIGHT\",\"xPercent\":45,\"yPercent\":62}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.kind").value("LIGHT"));
        mockMvc.perform(post("/api/v1/memorials/{id}/habitat/notes", memorialId).session(owner.session()).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoryDate\":\"2026-09-01\",\"text\":\"它总是在窗边等我。\",\"sourceType\":\"DAY_MOMENT\",\"sourceLabel\":\"18:30 · 门口等我\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sourceType").value("DAY_MOMENT"));
        mockMvc.perform(get("/api/v1/memorials/{id}/habitat", memorialId).session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.notes[0].sourceLabel").value("18:30 · 门口等我"));
        mockMvc.perform(get("/api/v1/memorials/{id}/habitat", memorialId).session(visitor.session()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data.code").value("MEMORIAL_NOT_FOUND"));
    }

    private RegisteredAccount register(String email, String name, Cookie cookie, String csrfToken) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").session(session).cookie(cookie)
                        .header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-long-enough-password\",\"displayName\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return new RegisteredAccount(UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")), session);
    }

    private record RegisteredAccount(UUID id, MockHttpSession session) {}
}
